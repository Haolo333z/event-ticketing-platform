param([ValidateRange(50,200)][int]$Requests=50,[ValidateRange(1,10)][int]$Rounds=3)
$ErrorActionPreference='Stop'
$workspaceRoot=Split-Path $PSScriptRoot -Parent
$runId='ticketing-load-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$copy=Join-Path $env:TEMP $runId
$output=Join-Path $workspaceRoot "reports/t9-2/$runId"
New-Item -ItemType Directory -Force $output | Out-Null
$report=[ordered]@{task='T9.2';status='RUNNING';started=(Get-Date -Format o);project=$runId;checkout=$copy;output=$output;steps=@()}
$loaded=$false;$server=$null
function Save-Report {$script:report|ConvertTo-Json -Depth 20|Set-Content -Encoding UTF8 (Join-Path $output 'summary.json')}
function Free-Port {$p=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);try{$p.Start();return $p.LocalEndpoint.Port}finally{$p.Stop()}}
function Containers {return @(& docker ps -a --format '{{.ID}}|{{.Names}}|{{.State}}'|Sort-Object)}
function Step([string]$Name,[string]$Command){
    $file=Join-Path $output "$Name.ps1"
    $literal="'"+$copy.Replace("'","''")+"'"
    [IO.File]::WriteAllText($file,"`$ErrorActionPreference='Stop'`r`nSet-Location $literal`r`n$Command`r`nif(`$LASTEXITCODE -ne 0){exit `$LASTEXITCODE}`r`nexit 0",[Text.UTF8Encoding]::new($true))
    Write-Output "RUN $Name"
    $child=Start-Process powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$file+'"')) -WorkingDirectory $copy -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output "$Name.stdout.log") -RedirectStandardError (Join-Path $output "$Name.stderr.log")
    $null=$child.Handle;while(!$child.WaitForExit(1000)){};$child.Refresh()
    $script:report.steps+=@{name=$Name;command=$Command;exitCode=$child.ExitCode};Save-Report
    if($child.ExitCode -ne 0){throw "$Name failed; inspect logs in $output"}
    Write-Output "PASS $Name"
}
try {
    $report.sourceHead=(& git -C $workspaceRoot rev-parse HEAD).Trim()
    if($LASTEXITCODE){throw 'Cannot read repository HEAD'}
    & git -C $workspaceRoot diff --quiet HEAD -- backend frontend tools/contracts .env.example infra/docker-compose.yml infra/local-env.ps1
    if($LASTEXITCODE){throw 'Application/config differs from HEAD; cannot benchmark stale application code'}
    if(@(& git -C $workspaceRoot ls-files --others --exclude-standard -- backend/src frontend/src).Count){throw 'Untracked application source would be omitted'}
    & docker version --format '{{.Server.Version}}'|Out-Null
    if($LASTEXITCODE){throw 'Docker Server unavailable'}
    $report.preexistingContainers=Containers;$report.preexistingVolumes=@(& docker volume ls -q|Sort-Object)
    $originalEnv=Join-Path $workspaceRoot '.env';$envHash=if(Test-Path $originalEnv){(Get-FileHash $originalEnv).Hash}else{$null}
    $cpu=Get-CimInstance Win32_Processor;$os=Get-CimInstance Win32_OperatingSystem;$computer=Get-CimInstance Win32_ComputerSystem
    $report.machine=@{os=$os.Caption;cpu=$cpu.Name;cores=$cpu.NumberOfCores;logicalProcessors=$cpu.NumberOfLogicalProcessors;ramBytes=$computer.TotalPhysicalMemory;freeMemoryKiB=$os.FreePhysicalMemory;
        docker=(& docker version --format '{{.Client.Version}} / {{.Server.Version}}');compose=(& docker compose version);dockerResources=(& docker info --format 'CPUs={{.NCPU}};MemoryBytes={{.MemTotal}}');
        java=((& java --version) -join ' ');node=(& node --version)}
    & git clone --quiet --no-hardlinks $workspaceRoot $copy
    if($LASTEXITCODE -or (& git -C $copy rev-parse HEAD).Trim() -ne $report.sourceHead){throw 'Fresh checkout failed or wrong HEAD'}
    if(Test-Path (Join-Path $copy '.env')){throw 'Fresh copy unexpectedly contains .env'}
    $report.tools=@()
    $loadDir=Join-Path $copy 'tools/load';New-Item -ItemType Directory -Force $loadDir|Out-Null
    foreach($name in @('HttpBurst.java','booking-load.mjs','statistics.mjs','statistics.test.mjs')){
        $source=Join-Path $workspaceRoot "tools/load/$name"
        Copy-Item -LiteralPath $source -Destination (Join-Path $loadDir $name) -Force
        $report.tools+=@{path="tools/load/$name";sha256=(Get-FileHash $source).Hash}
    }
    . (Join-Path $copy 'infra/local-env.ps1')
    $pg=Free-Port;$kafka=Free-Port;$http=Free-Port
    while($kafka -eq $pg){$kafka=Free-Port};while($http -in @($pg,$kafka)){$http=Free-Port}
    $config=[IO.File]::ReadAllText($envPath) -replace '(?m)^POSTGRES_DB=.*$','POSTGRES_DB=ticketing_load'
    $config=$config -replace '(?m)^DB_PORT=.*$',"DB_PORT=$pg"
    $config=$config -replace '(?m)^KAFKA_PORT=.*$',"KAFKA_PORT=$kafka"
    $config=$config -replace '(?m)^KAFKA_BOOTSTRAP_SERVERS=.*$',"KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:$kafka"
    $config=$config -replace '(?m)^SERVER_PORT=.*$',"SERVER_PORT=$http"
    [IO.File]::WriteAllText($envPath,$config+"`nCOMPOSE_PROJECT_NAME=$runId`n",[Text.UTF8Encoding]::new($false))
    . (Join-Path $copy 'infra/local-env.ps1');$loaded=$true
    if($env:COMPOSE_PROJECT_NAME -ne $runId -or $env:POSTGRES_DB -ne 'ticketing_load'){throw 'Isolation guard failed'}
    $report.isolation=@{database=$env:POSTGRES_DB;postgresPort=$pg;kafkaPort=$kafka;backendPort=$http;freshVolumes=$true}
    $report.configuration=@{requests=$Requests;rounds=$Rounds;clientWorkers=$Requests;clientJvm='-Xms128m -Xmx256m';backendJvm='-Xms256m -Xmx512m';
        poolMax=$env:DB_POOL_MAX_SIZE;poolMin=$env:DB_POOL_MIN_IDLE;poolAcquisitionTimeoutMs=2000;
        lockTimeoutMs=$env:BOOKING_LOCK_TIMEOUT_MS;statementTimeoutMs=$env:BOOKING_STATEMENT_TIMEOUT_MS;attemptTimeoutMs=$env:BOOKING_ATTEMPT_TIMEOUT_MS;totalTimeoutMs=$env:BOOKING_TOTAL_TIMEOUT_MS;
        holdTtlSeconds=$env:BOOKING_HOLD_TTL_SECONDS;expiryEnabled=$env:EXPIRY_ENABLED;expiryIntervalSeconds=$env:EXPIRY_INTERVAL_SECONDS;messagingEnabled=$true;
        identity='One seeded customer principal, unique idempotency key per request; local/test fixture only';transport='Loopback HTTP/1.1; same host for client/server/containers'}
    Save-Report
    Step 'tool-tests' '& node --test tools/load/statistics.test.mjs'
    & docker @composeArgs up -d --wait postgres kafka
    if($LASTEXITCODE){throw 'Owned dependencies unavailable'}
    Step 'backend-build' '& backend/mvnw.cmd -B -ntp -f backend/pom.xml -DskipTests package'
    $jar=Join-Path $copy 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar'
    $server=Start-Process java -ArgumentList @('-Xms256m','-Xmx512m','-Duser.timezone=UTC','-jar',('"'+$jar+'"'),"--server.port=$http",'--spring.profiles.active=local','--messaging.enabled=true',"--messaging.topic=$runId.booking.v1","--messaging.dlq-topic=$runId.booking.dlq.v1","--messaging.group-prefix=$runId") -WorkingDirectory $copy -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output 'backend.stdout.log') -RedirectStandardError (Join-Path $output 'backend.stderr.log')
    $null=$server.Handle;$up=$false
    for($i=0;$i -lt 120;$i++){
        if($server.HasExited){throw 'Owned backend exited'}
        try{$health=Invoke-RestMethod "http://127.0.0.1:$http/actuator/health/readiness" -TimeoutSec 2;if($health.status -eq 'UP'){$up=$true;break}}catch{}
        Start-Sleep -Milliseconds 500
    }
    if(!$up){throw 'Owned backend readiness timeout'}
    $listener=Get-NetTCPConnection -LocalPort $http -State Listen
    if($listener.OwningProcess -ne $server.Id -or $listener.LocalAddress -ne '127.0.0.1'){throw 'Target listener is not our JVM loopback'}
    $report.backendPid=$server.Id
    $env:LOAD_BASE="http://127.0.0.1:$http";$env:LOAD_OUTPUT=$output;$env:LOAD_REQUESTS="$Requests";$env:LOAD_ROUNDS="$Rounds"
    Step 'measured-load' '& node tools/load/booking-load.mjs'
    $report.results=Get-Content (Join-Path $output 'load-results.json') -Raw|ConvertFrom-Json
    if($report.results.status -ne 'PASS'){throw 'Load or SQL acceptance failed'}
    $report.status='PASS'
} catch {$report.status='FAIL';$report.error=$_.Exception.Message;throw}
finally {
    if($server -and !$server.HasExited){
        $identity=Get-CimInstance Win32_Process -Filter "ProcessId=$($server.Id)"
        if($identity.Name -eq 'java.exe' -and $identity.CommandLine.Replace('/','\').Contains($jar.Replace('/','\'))){Stop-Process -Id $server.Id;$server.WaitForExit()}
        else{$report.status='FAIL';$report.cleanupError='JVM identity changed; refused stop'}
    }
    if($loaded){& docker @composeArgs stop kafka postgres;if($LASTEXITCODE){$report.status='FAIL';$report.cleanupError='Owned services stop failed'};$report.retainedOwnedVolumes=@(& docker volume ls --filter "label=com.docker.compose.project=$runId" -q)}
    # Windows redirection holds writer handles open until the owned JVM has stopped.
    if($loaded){try{
        foreach($file in Get-ChildItem $output -Filter '*.log'){
            $body=[IO.File]::ReadAllText($file.FullName)
            if($body.Contains($env:POSTGRES_PASSWORD) -or $body -match 'demo-customer-token|demo-admin-token|Authorization: Bearer'){throw 'Sensitive data in captured log; do not publish'}
        }
        $report.logScan='PASS'
    }catch{$report.status='FAIL';$report.logScanError=$_.Exception.Message}}
    if($report.Contains('preexistingContainers')){
        $after=Containers
        $report.preexistingStatesUnchanged=@(Compare-Object $report.preexistingContainers @($after|Where-Object {($_ -split '\|')[0] -in @($report.preexistingContainers|ForEach-Object {($_ -split '\|')[0]})})).Count -eq 0
        $current=@(& docker volume ls -q);$report.preexistingVolumesRetained=@($report.preexistingVolumes|Where-Object {$_ -notin $current}).Count -eq 0
        $afterHash=if(Test-Path $originalEnv){(Get-FileHash $originalEnv).Hash}else{$null};$report.workspaceEnvUnchanged=$envHash -eq $afterHash
        if(!$report.preexistingStatesUnchanged -or !$report.preexistingVolumesRetained -or !$report.workspaceEnvUnchanged){$report.status='FAIL';$report.preservationError='Preexisting resource changed'}
    }
    $report.finished=Get-Date -Format o;Save-Report;Write-Output "$($report.status) T9.2: $output/summary.json"
}
if($report.status -ne 'PASS'){throw 'T9.2 not complete; inspect evidence'}
