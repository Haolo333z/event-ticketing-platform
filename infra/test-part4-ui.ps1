. "$PSScriptRoot/local-env.ps1"
$jar=Join-Path $repoRoot 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Run infra/test-part4.ps1 to build the backend first.'}
& docker @composeArgs up -d --wait postgres
if($LASTEXITCODE -ne 0){throw 'PostgreSQL unavailable'}
$name='ticketing_ui_'+[Guid]::NewGuid().ToString('N')
& docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $name
if($LASTEXITCODE -ne 0){throw 'Cannot create UI test DB'}
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$runtime=Join-Path $env:TEMP ($name+'.jar')
Copy-Item -LiteralPath $jar -Destination $runtime
$process=$null
try {
    $process=Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar',$runtime,"--spring.datasource.url=jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$name",'--spring.profiles.active=test',"--server.port=$port") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP ($name+'.log')) -RedirectStandardError (Join-Path $env:TEMP ($name+'.err'))
    $env:API_BASE_URL="http://127.0.0.1:$port"
    $ready=$false
    for($i=0;$i -lt 100;$i++){
        if($process.HasExited){throw "Test backend exited; inspect $name.log in TEMP"}
        try{$events=Invoke-RestMethod "$env:API_BASE_URL/api/events" -TimeoutSec 2;if($events.totalElements -ge 2){$ready=$true;break}}catch{}
        Start-Sleep -Milliseconds 200
    }
    if(!$ready){throw 'Test backend did not become ready'}
    & npm.cmd run test:live --prefix (Join-Path $repoRoot 'frontend')
    if($LASTEXITCODE -ne 0){throw 'Live UI test failed'}
    $actual="SELECT count(*) FROM ticketing.inventory_ga WHERE capacity=3 AND held_count=3 AND sold_count=0;" | & docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $name -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0 -or ($actual -join '').Trim() -ne '1'){throw 'SQL inventory invariant failed'}
    Write-Output 'PASS SQL: held=3, sold=0, capacity=3 after UI success, replay and conflict.'
} finally {
    if($process -and !$process.HasExited){Stop-Process -Id $process.Id;$process.WaitForExit()}
    Remove-Item Env:API_BASE_URL -ErrorAction SilentlyContinue
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --force $name
    Remove-Item -LiteralPath $runtime -Force -ErrorAction SilentlyContinue
}
