. "$PSScriptRoot/local-env.ps1"
$jar=Join-Path $repoRoot 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Run infra/test-part8.ps1 first'}
& docker @composeArgs up -d --wait postgres kafka
if($LASTEXITCODE -ne 0){throw 'PostgreSQL/Kafka unavailable'}
$name='ticketing_t8ui_'+[Guid]::NewGuid().ToString('N')
& docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $name
if($LASTEXITCODE -ne 0){throw 'Cannot create isolated UI database'}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$runtime=Join-Path $env:TEMP ($name+'.jar');Copy-Item -LiteralPath $jar -Destination $runtime
$process=$null;$prod=$null;$env:API_BASE_URL="http://127.0.0.1:$port"
$log=Join-Path $env:TEMP ($name+'.log');$err=Join-Path $env:TEMP ($name+'.err')
try {
    $process=Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=test',"--server.port=$port",'--expiry.enabled=true','--BOOKING_HOLD_TTL_SECONDS=60','--EXPIRY_INTERVAL_SECONDS=1','--messaging.enabled=true',"--messaging.topic=$name.booking.v1","--messaging.dlq-topic=$name.booking.dlq.v1","--messaging.group-prefix=$name") -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
    $ready=$false
    for($i=0;$i -lt 150;$i++){
        if($process.HasExited){throw "Test JVM exited; inspect $log"}
        try{$events=Invoke-RestMethod "$env:API_BASE_URL/api/events" -TimeoutSec 2;if($events.totalElements -ge 2){$ready=$true;break}}catch{}
        Start-Sleep -Milliseconds 200
    }
    if(!$ready){throw 'Test backend did not become ready'}
    $bindings=@(Get-NetTCPConnection -OwningProcess $process.Id -State Listen)
    if(!($bindings|Where-Object {$_.LocalPort -eq $port -and $_.LocalAddress -eq '127.0.0.1'}) -or ($bindings|Where-Object {$_.LocalAddress -eq '0.0.0.0' -or $_.LocalAddress -eq '::'})){throw 'Backend is not bound only to loopback'}
    & npm.cmd run test:live --prefix (Join-Path $repoRoot 'tools/contracts')
    if($LASTEXITCODE -ne 0){throw 'Live API contract checks failed'}
    & npm.cmd run test:live:admin --prefix (Join-Path $repoRoot 'frontend')
    if($LASTEXITCODE -ne 0){throw 'Live admin/customer UI failed'}
    $proof=Get-Content -LiteralPath (Join-Path $repoRoot 'frontend/reports/part8-live.json') -Raw|ConvertFrom-Json
    $sql="SELECT (SELECT count(*) FROM ticketing.tickets WHERE booking_id='$($proof.bookingId)'),(SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id='$($proof.gaTypeId)'),(SELECT count(*) FROM ticketing.inbox_events WHERE consumer='payment-v1' AND aggregate_id='$($proof.bookingId)'),(SELECT count(*) FROM ticketing.inventory_ga WHERE held_count+sold_count>capacity OR held_count<0 OR sold_count<0),(SELECT count(*) FROM ticketing.tickets t JOIN ticketing.bookings b ON b.id=t.booking_id WHERE b.status<>'CONFIRMED'),(SELECT count(*) FROM public.flyway_schema_history WHERE success);"
    $result=$sql|& docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $name -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0 -or ($result -join '').Trim() -ne '2|2|1|0|0|9'){throw "SQL invariant checks failed: $result"}
    Write-Output 'PASS SQL:2tickets/sold2/payment inbox1,0oversell,0tickets on invalid states,9unchanged migrations.'
    & npm.cmd run test:live:recovery --prefix (Join-Path $repoRoot 'frontend')
    if($LASTEXITCODE -ne 0){throw 'Live recovery UI failed'}
    $recovery=Get-Content -LiteralPath (Join-Path $repoRoot 'frontend/reports/recovery-live.json') -Raw|ConvertFrom-Json
    $recoverySql="SELECT (SELECT count(*) FROM ticketing.tickets WHERE booking_id='$($recovery.bookingId)'),(SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id='$($recovery.ticketTypeId)'),(SELECT count(*) FROM ticketing.booking_status_history WHERE booking_id='$($recovery.bookingId)'),(SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id='$($recovery.bookingId)'),(SELECT jsonb_array_length(replay_requests) FROM ticketing.outbox_events WHERE id='$($recovery.paymentEventId)'),(SELECT count(*) FROM ticketing.inbox_events WHERE consumer='payment-v1' AND event_id='$($recovery.paymentEventId)');"
    $recoveryResult=$recoverySql|& docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $name -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0 -or ($recoveryResult -join '').Trim() -ne '2|2|2|3|1|1'){throw "Recovery SQL invariant checks failed: $recoveryResult"}
    Write-Output 'PASS recovery SQL:2tickets/sold2/history2/businessEvents3/audit1/paymentInbox1 after two identical HTTP replays.'
    # Verify SEC-01 through an actual packaged application startup, not just a settings unit test.
    $prodLog=Join-Path $env:TEMP ($name+'-prod.log');$prodErr=Join-Path $env:TEMP ($name+'-prod.err')
    $prod=Start-Process java -ArgumentList @('-Xmx384m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=prod','--demo.auth.enabled=true','--messaging.enabled=false','--expiry.enabled=false','--server.port=0') -WindowStyle Hidden -PassThru -RedirectStandardOutput $prodLog -RedirectStandardError $prodErr
    if(!$prod.WaitForExit(30000)){throw 'Production guard did not terminate startup'}
    $prodText=(Get-Content -LiteralPath $prodLog -Raw)+(Get-Content -LiteralPath $prodErr -Raw)
    if($prod.ExitCode -eq 0 -or $prodText -notmatch 'Mock auth is allowed only in local/test profiles'){throw 'Production guard failed for an unexpected reason'}
    Write-Output 'PASS SEC-01: packaged prod+mock auth refuses startup; test backend bound127.0.0.1 only.'
    $allText=(Get-Content -LiteralPath $log -Raw)+(Get-Content -LiteralPath $err -Raw)
    if($allText -match 'demo-customer-token|demo-admin-token|Authorization: Bearer'){throw 'Demo token leaked into application log'}
    Write-Output "PASS stage 8 UI/log checks. Logs retained in TEMP: $name"
} finally {
    foreach($p in @($process,$prod)){if($p -and !$p.HasExited){Stop-Process -Id $p.Id;$p.WaitForExit()}}
    Remove-Item Env:API_BASE_URL -ErrorAction SilentlyContinue
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --force $name
    Remove-Item -LiteralPath $runtime -Force -ErrorAction SilentlyContinue
}
