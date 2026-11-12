. "$PSScriptRoot/local-env.ps1"
$jar=Join-Path $repoRoot 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Run infra/test-part6.ps1 first'}
& docker @composeArgs up -d --wait postgres
if($LASTEXITCODE -ne 0){throw 'PostgreSQL unavailable'}
$name='ticketing_payment_ui_'+[Guid]::NewGuid().ToString('N')
& docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $name
if($LASTEXITCODE -ne 0){throw 'Cannot create isolated test database'}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$runtime=Join-Path $env:TEMP ($name+'.jar')
Copy-Item -LiteralPath $jar -Destination $runtime
$process=$null
$env:API_BASE_URL="http://127.0.0.1:$port"
function Start-TestBackend([string]$suffix){
    Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=test',"--server.port=$port",'--expiry.enabled=true','--BOOKING_HOLD_TTL_SECONDS=4','--EXPIRY_INTERVAL_SECONDS=1') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP ($name+$suffix+'.log')) -RedirectStandardError (Join-Path $env:TEMP ($name+$suffix+'.err'))
}
function Wait-TestBackend($taskProcess){
    for($i=0;$i -lt 100;$i++){
        if($taskProcess.HasExited){throw 'Test JVM exited; inspect its TEMP log'}
        try{$events=Invoke-RestMethod "$env:API_BASE_URL/api/events" -TimeoutSec 2;if($events.totalElements -ge 2){return}}catch{}
        Start-Sleep -Milliseconds 200
    }
    throw 'Test JVM did not become ready'
}
try {
    $process=Start-TestBackend ''
    Wait-TestBackend $process
    & npm.cmd run test:live --prefix (Join-Path $repoRoot 'tools/contracts')
    if($LASTEXITCODE -ne 0){throw 'Live API contract checks failed'}
    & npm.cmd run test:live:payments --prefix (Join-Path $repoRoot 'frontend')
    if($LASTEXITCODE -ne 0){throw 'Live payment UI failed'}
    # Leave a scheduled result in PostgreSQL and restart only our own JVM before it is recorded.
    $headers=@{Authorization='Bearer demo-customer-token'}
    $bookingHeaders=@{Authorization='Bearer demo-customer-token';'Idempotency-Key'=[Guid]::NewGuid().ToString()}
    $body='{"eventId":"20000000-0000-4000-8000-000000000002","ticketTypeId":"30000000-0000-4000-8000-000000000002","quantity":1}'
    $booking=Invoke-RestMethod "$env:API_BASE_URL/api/bookings" -Method Post -Headers $bookingHeaders -ContentType 'application/json' -Body $body
    $requestId=[Guid]::NewGuid().ToString()
    $payment=@{scenario='DELAY_TIMEOUT';requestId=$requestId}|ConvertTo-Json
    $accepted=Invoke-RestMethod "$env:API_BASE_URL/api/bookings/$($booking.bookingId)/simulate-payment" -Method Post -Headers $headers -ContentType 'application/json' -Body $payment
    Stop-Process -Id $process.Id
    $process.WaitForExit()
    $process=Start-TestBackend '-restart'
    Wait-TestBackend $process
    $current=$null
    for($i=0;$i -lt 100;$i++){
        $current=Invoke-RestMethod "$env:API_BASE_URL/api/bookings/$($booking.bookingId)" -Headers $headers
        if($current.status -eq 'REFUND_REQUIRED'){break}
        Start-Sleep -Milliseconds 200
    }
    if($current.status -ne 'REFUND_REQUIRED'){throw 'Scheduled payment did not recover after restart'}
    $replay=Invoke-RestMethod "$env:API_BASE_URL/api/bookings/$($booking.bookingId)/simulate-payment" -Method Post -Headers $headers -ContentType 'application/json' -Body $payment
    if($replay.paymentIntentId -ne $accepted.paymentIntentId){throw 'Delayed replay changed intent'}
    $ticketResponse=Invoke-WebRequest -UseBasicParsing "$env:API_BASE_URL/api/bookings/$($booking.bookingId)/tickets" -Headers $headers
    $ticketText=if($ticketResponse.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($ticketResponse.Content)}else{$ticketResponse.Content}
    if($ticketResponse.StatusCode -ne 200 -or $ticketText -notmatch '^\s*\[\s*\]\s*$'){throw 'Refund-required booking must return an empty ticket array'}
    if((Get-Content -LiteralPath (Join-Path $env:TEMP ($name+'-restart.log')) -Raw) -notmatch 'No migration necessary'){throw 'Expected no migration after restart'}
    Write-Output 'PASS restart: durable scheduled payment recovered, REFUND_REQUIRED,0tickets, same intent on replay, no migration necessary.'
    $sql=@'
SELECT count(*) FROM ticketing.tickets t JOIN ticketing.bookings b ON b.id=t.booking_id WHERE b.status<>'CONFIRMED';
SELECT count(*) FROM ticketing.tickets t JOIN ticketing.booking_items i ON i.id=t.booking_item_id WHERE t.ticket_ordinal>i.quantity OR t.booking_id<>i.booking_id;
SELECT count(*) FROM ticketing.inventory_ga WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity;
SELECT count(*) FROM ticketing.seats s JOIN ticketing.bookings b ON b.id=s.held_by_booking_id WHERE s.status='SOLD' AND b.status<>'CONFIRMED';
SELECT count(*) FROM (SELECT payment_id FROM ticketing.payment_compensations GROUP BY payment_id HAVING count(*)>1) d;
SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7','8');
'@
    $actual=$sql|& docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $name -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0 -or ($actual -join ',') -ne '0,0,0,0,0,8'){throw "Invariant checks failed: $($actual -join ',')"}
    Write-Output 'PASS SQL:0tickets on invalid states,0wrong ordinals/booking,0oversell,0wrong SOLD owner,0duplicate compensation;V1-V8.'
} finally {
    if($process -and !$process.HasExited){Stop-Process -Id $process.Id;$process.WaitForExit()}
    Remove-Item Env:API_BASE_URL -ErrorAction SilentlyContinue
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --force $name
    Remove-Item -LiteralPath $runtime -Force -ErrorAction SilentlyContinue
}
