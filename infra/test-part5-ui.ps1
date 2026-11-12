. "$PSScriptRoot/local-env.ps1"
$jar=Join-Path $repoRoot 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Run infra/test-part5.ps1 first.'}
& docker @composeArgs up -d --wait postgres
if($LASTEXITCODE -ne 0){throw 'PostgreSQL unavailable'}
$name='ticketing_seat_ui_'+[Guid]::NewGuid().ToString('N')
& docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $name
if($LASTEXITCODE -ne 0){throw 'Cannot create isolated database'}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$runtime=Join-Path $env:TEMP ($name+'.jar')
Copy-Item -LiteralPath $jar -Destination $runtime
$process=$null
try {
    $process=Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=test',"--server.port=$port",'--expiry.enabled=true','--BOOKING_HOLD_TTL_SECONDS=4','--EXPIRY_INTERVAL_SECONDS=1') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP ($name+'.log')) -RedirectStandardError (Join-Path $env:TEMP ($name+'.err'))
    $env:API_BASE_URL="http://127.0.0.1:$port"
    $ready=$false
    for($i=0;$i -lt 100;$i++){
        if($process.HasExited){throw "Backend exited; inspect $name.log in TEMP"}
        try{$events=Invoke-RestMethod "$env:API_BASE_URL/api/events" -TimeoutSec 2;if($events.totalElements -ge 2){$ready=$true;break}}catch{}
        Start-Sleep -Milliseconds 200
    }
    if(!$ready){throw 'Backend not ready'}
    & npm.cmd run test:live --prefix (Join-Path $repoRoot 'tools/contracts')
    if($LASTEXITCODE -ne 0){throw 'Live contract failed'}
    & npm.cmd run test:live:seats --prefix (Join-Path $repoRoot 'frontend')
    if($LASTEXITCODE -ne 0){throw 'Live seat UI failed'}
    $sql=@'
SELECT count(*) FROM ticketing.seats WHERE status='HELD' AND (held_by_booking_id IS NULL OR hold_expires_at IS NULL);
SELECT count(*) FROM ticketing.bookings b WHERE status='EXPIRED' AND EXISTS(SELECT 1 FROM ticketing.seats s WHERE s.held_by_booking_id=b.id);
SELECT count(*) FROM ticketing.inventory_ga WHERE held_count<0 OR held_count+sold_count>capacity;
SELECT count(*) FROM (SELECT aggregate_id FROM ticketing.outbox_events WHERE type='BookingExpired' GROUP BY aggregate_id HAVING count(*)>1) d;
SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7');
'@
    $actual=$sql | & docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $name -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0 -or ($actual -join ',') -ne '0,0,0,0,7'){throw "Unexpected invariant counts: $($actual -join ',')"}
    Write-Output 'PASS SQL: no orphan holds, no expired seat owner, no GA oversell, no duplicate BookingExpired; migrations V1-V7.'
    # Restart only the temporary JVM created by this script, on the same isolated DB.
    Stop-Process -Id $process.Id
    $process.WaitForExit()
    $process=Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=test',"--server.port=$port",'--expiry.enabled=true','--BOOKING_HOLD_TTL_SECONDS=4','--EXPIRY_INTERVAL_SECONDS=1') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP ($name+'-restart.log')) -RedirectStandardError (Join-Path $env:TEMP ($name+'-restart.err'))
    $ready=$false
    for($i=0;$i -lt 100;$i++){
        try{$health=Invoke-RestMethod "$env:API_BASE_URL/actuator/health" -TimeoutSec 2;if($health.status -eq 'UP'){$ready=$true;break}}catch{}
        Start-Sleep -Milliseconds 200
    }
    if(!$ready){throw 'Restart on same DB failed'}
    $log=Get-Content -LiteralPath (Join-Path $env:TEMP ($name+'-restart.log')) -Raw
    if($log -notmatch 'No migration necessary'){throw 'Expected Flyway up-to-date after restart'}
    Write-Output 'PASS restart: same isolated DB, health UP, no migration necessary.'
} finally {
    Remove-Item Env:API_BASE_URL -ErrorAction SilentlyContinue
    if($process -and !$process.HasExited){Stop-Process -Id $process.Id;$process.WaitForExit()}
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --force $name
    Remove-Item -LiteralPath $runtime -Force -ErrorAction SilentlyContinue
}
