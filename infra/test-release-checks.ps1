$ErrorActionPreference='Stop'
. "$PSScriptRoot/local-env.ps1"
$output=$env:TICKETING_AUDIT_OUTPUT
if(!$output -or $env:COMPOSE_PROJECT_NAME -ne $env:TICKETING_AUDIT_PROJECT -or $env:COMPOSE_PROJECT_NAME -notmatch '^ticketing-audit-[a-f0-9]{12}$'){
    throw 'Requires the isolated working-tree audit project; never run on demo'
}
$result=[ordered]@{status='RUNNING';checks=@();migration=@{};logScan=@{}}
function Invoke-Check($name,$exe,[string[]]$arguments){
    $p=Start-Process -FilePath $exe -ArgumentList $arguments -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output "$name.stdout.log") -RedirectStandardError (Join-Path $output "$name.stderr.log")
    $null=$p.Handle;$p.WaitForExit();$p.Refresh()
    $script:result.checks+=@{name=$name;exitCode=$p.ExitCode}
    if($p.ExitCode -ne 0){throw "$name failed (exit $($p.ExitCode)); inspect audit logs"}
}
function Query([string]$sql){
    $value=$sql | & docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -At -v ON_ERROR_STOP=1
    if($LASTEXITCODE){throw 'Migration SQL failed'}
    return ($value -join "`n").Trim()
}
$process=$null
try{
    Invoke-Check 'audit-frontend-deps' 'npm.cmd' @('audit','--prefix','frontend','--audit-level=high','--json')
    Invoke-Check 'audit-contract-deps' 'npm.cmd' @('audit','--prefix','tools/contracts','--audit-level=high','--json')
    Invoke-Check 'load-tool-tests' 'node' @('--test','tools/load/statistics.test.mjs')
    $compiled=Join-Path $output 'load-client'
    New-Item -ItemType Directory -Force $compiled|Out-Null
    Invoke-Check 'load-client-compile' 'javac' @('-d',('"'+$compiled+'"'),'tools/load/HttpBurst.java')
    Push-Location (Join-Path $repoRoot 'backend')
    try{
        & .\mvnw.cmd -B -ntp cyclonedx:makeAggregateBom
        if($LASTEXITCODE){throw 'SBOM generation failed'}
    }finally{Pop-Location}
    $tools=Join-Path (Split-Path $output -Parent) 'tools'
    Invoke-Check 'audit-backend-deps' (Join-Path $tools 'osv-scanner_windows_amd64.exe') @('scan','source','--sbom','backend/target/bom.json','--format','json')
    # The project's primary database has not been used by any UUID test harness.
    & docker @composeArgs up -d --wait postgres kafka
    if($LASTEXITCODE){throw 'Audit dependencies unavailable'}
    $before=Query "SELECT count(*) FROM information_schema.tables WHERE table_schema='ticketing';"
    if($before -ne '0'){throw 'Migration gate requires a fresh database; refusing reset'}
    $result.migration.database=$env:POSTGRES_DB
    $result.migration.beforeTables=[int]$before
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
    $sql=@'
SELECT jsonb_build_object(
 'migrations',(SELECT jsonb_agg(jsonb_build_object('version',version,'checksum',checksum,'success',success,'installedOn',installed_on) ORDER BY installed_rank) FROM public.flyway_schema_history),
 'tables',(SELECT count(*) FROM information_schema.tables WHERE table_schema='ticketing' AND table_type='BASE TABLE'),
 'users',(SELECT count(*) FROM ticketing.users), 'events',(SELECT count(*) FROM ticketing.events),
 'gaCapacity',(SELECT sum(capacity) FROM ticketing.inventory_ga), 'seats',(SELECT count(*) FROM ticketing.seats),
 'bookings',(SELECT count(*) FROM ticketing.bookings));
'@
    [IO.File]::WriteAllText((Join-Path $output 'migration-query.sql'),$sql,[Text.UTF8Encoding]::new($false))
    $states=@()
    foreach($boot in 1..2){
        $log=Join-Path $output "migration-boot-$boot.stdout.log"
        $err=Join-Path $output "migration-boot-$boot.stderr.log"
        $process=Start-Process java -ArgumentList @('-Duser.timezone=UTC','-Xmx512m','-jar','backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar','--spring.profiles.active=test',"--server.port=$port",'--expiry.enabled=false','--messaging.enabled=true',"--messaging.topic=$($env:COMPOSE_PROJECT_NAME).migration.v1","--messaging.dlq-topic=$($env:COMPOSE_PROJECT_NAME).migration.dlq.v1","--messaging.group-prefix=$($env:COMPOSE_PROJECT_NAME).migration") -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
        $null=$process.Handle
        $ready=$false
        for($i=0;$i -lt 150;$i++){
            if($process.HasExited){throw "Migration boot $boot exited; inspect audit logs"}
            try{if((Invoke-RestMethod "http://127.0.0.1:$port/actuator/health/readiness" -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{}
            Start-Sleep -Milliseconds 200
        }
        if(!$ready){throw "Migration boot $boot not ready"}
        $catalog=Invoke-RestMethod "http://127.0.0.1:$port/api/events" -TimeoutSec 5
        if($catalog.totalElements -ne 2){throw 'Clean seed must expose two events'}
        $state=Query $sql
        $parsed=$state|ConvertFrom-Json
        if($parsed.migrations.Count -ne 9 -or @($parsed.migrations|Where-Object {!$_.success}).Count -or ($parsed.migrations.version -join ',') -ne '1,2,3,4,5,6,7,8,9' -or $parsed.tables -ne 18 -or $parsed.users -ne 2 -or $parsed.events -ne 2 -or $parsed.gaCapacity -ne 100 -or $parsed.seats -ne 4 -or $parsed.bookings -ne 0){throw 'Clean migration/seed state mismatch'}
        $states+=@{boot=$boot;state=$parsed;readiness='UP';catalogEvents=$catalog.totalElements}
        Stop-Process -Id $process.Id;$process.WaitForExit();$process=$null
        if($boot -eq 1 -and [IO.File]::ReadAllText($log) -notmatch 'Successfully applied 9 migrations'){throw 'Missing clean Flyway execution evidence'}
        if($boot -eq 2 -and ([IO.File]::ReadAllText($log) -notmatch 'No migration necessary' -or $state -ne $first)){throw 'Restart changed migration/seed state'}
        $first=$state
    }
    $result.migration.boots=$states
    $result.migration.sources=@(Get-ChildItem (Join-Path $repoRoot 'backend/src/main/resources/db/migration/*.sql')|ForEach-Object {@{file=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}})
    # Scan files after JVM writers have closed. Do not emit matching values or raw logs.
    $logs=@(Get-ChildItem -LiteralPath $output -File -Filter '*.log')+@(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend/target/surefire-reports') -File | Where-Object Extension -in @('.xml','.txt'))
    $violations=@()
    foreach($log in $logs){
        if($log.Name -like 'release-checks.*.log'){continue} # parent process still owns these writers
        $content=[IO.File]::ReadAllText($log.FullName)
        if($content.Contains($env:POSTGRES_PASSWORD) -or $content -match 'demo-customer-token|demo-admin-token|Authorization: Bearer|"(authorization|password|customer-token|admin-token|raw_event|rawEvent|payload)"\s*:'){$violations+=@($log.Name)}
    }
    $result.logScan=@{files=$logs.Count;violations=$violations;scope='Audit subprocess output, clean-boot logs and JUnit XML/text (closed writers); not all possible production logging levels'}
    if($violations.Count){throw 'Sensitive fields/values in audit logs; inspect privately, do not publish'}
    $result.status='PASS'
}catch{$result.status='FAIL';$result.error=$_.Exception.Message;throw}
finally{
    if($process -and !$process.HasExited){Stop-Process -Id $process.Id;$process.WaitForExit()}
    $result|ConvertTo-Json -Depth 15|Set-Content -Encoding UTF8 (Join-Path $output 'release-checks.json')
}
Write-Output 'PASS dependencies, load-tool regression, clean V1-V9 migration/restart/seed and sensitive-log scan.'
