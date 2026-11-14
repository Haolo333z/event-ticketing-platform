param([switch]$RecoveryEvidence,[switch]$WorkingTree)
$ErrorActionPreference='Stop'
$workspaceRoot=Split-Path $PSScriptRoot -Parent
$RecoveryEvidence=$RecoveryEvidence -or $WorkingTree
$task=if($WorkingTree){'T9.4'}elseif($RecoveryEvidence){'T9.3'}else{'T9.1'}
$runId=$(if($WorkingTree){'ticketing-audit-'}elseif($RecoveryEvidence){'ticketing-recovery-'}else{'ticketing-invariants-'})+[Guid]::NewGuid().ToString('N').Substring(0,12)
$copy=Join-Path $env:TEMP $runId
$output=Join-Path $workspaceRoot "reports/$(if($WorkingTree){'t9-4'}elseif($RecoveryEvidence){'t9-3'}else{'t9-1'})/$runId"
New-Item -ItemType Directory -Force $output | Out-Null
$report=[ordered]@{task=$task;status='RUNNING';started=(Get-Date -Format o);checkout=$copy;project=$runId;output=$output;steps=@();harnessSha256=(Get-FileHash -LiteralPath $PSCommandPath).Hash}
$loaded=$false
function Save-Report {$script:report | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 (Join-Path $output 'summary.json')}
function Get-Port {
    $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    try{$probe.Start();return $probe.LocalEndpoint.Port}finally{$probe.Stop()}
}
function Invoke-Step([string]$Name,[string]$Command){
    $stepFile=Join-Path $output "$Name.ps1"
    $literalCopy="'"+$copy.Replace("'","''")+"'"
    [IO.File]::WriteAllText($stepFile,"`$ErrorActionPreference='Stop'`r`nSet-Location $literalCopy`r`n$Command`r`nif(`$LASTEXITCODE -ne 0){exit `$LASTEXITCODE}`r`nexit 0`r`n",[Text.UTF8Encoding]::new($true))
    $out=Join-Path $output "$Name.stdout.log";$err=Join-Path $output "$Name.stderr.log"
    Write-Output "RUN $Name"
    $child=Start-Process powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$stepFile+'"')) -WorkingDirectory $copy -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    # Retain the process handle before it exits so Windows PowerShell can read ExitCode.
    $null=$child.Handle
    while(!$child.WaitForExit(1000)){}
    $child.Refresh()
    $script:report.steps+=@{name=$Name;command=$Command;exitCode=$child.ExitCode;stdout=$out;stderr=$err}
    Save-Report
    if($child.ExitCode -ne 0){throw "$Name failed; inspect $out and $err"}
    Write-Output "PASS $Name"
}
function Get-ExistingContainers {return @(& docker ps -a --format '{{.ID}}|{{.Names}}|{{.State}}' | Sort-Object)}
function Get-CandidateManifest {
    $paths=@(& git -C $workspaceRoot ls-files --cached --others --exclude-standard | Sort-Object -Unique)
    if($LASTEXITCODE){throw 'Cannot inventory current working tree'}
    foreach($path in $paths){
        if($path -match '(^|/)(\.env(?!\.example$)[^/]*|[^/]+\.(pem|key))$'){throw 'Private file must not be in candidate source'}
        $source=Join-Path $workspaceRoot $path
        if(Test-Path -LiteralPath $source -PathType Leaf){
            if((Get-Item -LiteralPath $source).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Candidate symlinks are not supported'}
            [pscustomobject]@{path=$path;sha256=(Get-FileHash -LiteralPath $source).Hash}
        }
    }
}
try {
    $report.sourceHead=(& git -C $workspaceRoot rev-parse HEAD).Trim()
    if($LASTEXITCODE){throw 'Cannot read repository HEAD'}
    # Default gate requires HEAD. T9.3 permits only a hash-verified overlay of current test sources.
    if(!$WorkingTree){
    [string[]]$protectedPaths=if($RecoveryEvidence){@('backend/src/main','backend/pom.xml','backend/.mvn','backend/mvnw','backend/mvnw.cmd')}else{@('backend')}
    & git -C $workspaceRoot diff --quiet HEAD -- @protectedPaths frontend tools/contracts .env.example infra/docker-compose.yml infra/local-env.ps1 'infra/test-part*.ps1' infra/test-recovery.ps1
    if($LASTEXITCODE){throw 'Application/test harness differs from HEAD; this gate must not claim to have tested those edits.'}
    [string[]]$untrackedPaths=if($RecoveryEvidence){@('backend/src/main')}else{@('backend/src')}
    $untracked=@(& git -C $workspaceRoot ls-files --others --exclude-standard -- @untrackedPaths frontend/src tools/contracts)
    if($LASTEXITCODE){throw 'Cannot check untracked application/test files; refusing stale-source certification'}
    if($untracked.Count){throw 'Untracked application/tests would be omitted from HEAD clone.'}
    }
    & docker version --format '{{.Server.Version}}' | Out-Null
    if($LASTEXITCODE){throw 'Docker Server unavailable; start Docker Desktop Linux engine first.'}
    $report.preexistingContainers=Get-ExistingContainers
    $report.preexistingVolumes=@(& docker volume ls -q | Sort-Object)
    $originalEnv=Join-Path $workspaceRoot '.env'
    $envBefore=if(Test-Path -LiteralPath $originalEnv){(Get-FileHash -LiteralPath $originalEnv).Hash}else{$null}
    & git clone --quiet --no-hardlinks $workspaceRoot $copy
    if($LASTEXITCODE){throw 'Cannot create isolated checkout'}
    if((& git -C $copy rev-parse HEAD).Trim() -ne $report.sourceHead){throw 'Checkout HEAD mismatch'}
    if($WorkingTree){
        $report.candidateManifest=@(Get-CandidateManifest)
        $candidatePaths=@($report.candidateManifest | ForEach-Object {$_.path})
        foreach($tracked in @(& git -C $copy ls-files)){
            # Reflect deletions too; never leave a stale HEAD fixture in the candidate.
            if($tracked -notin $candidatePaths){Remove-Item -LiteralPath (Join-Path $copy $tracked) -Force}
        }
        foreach($item in $report.candidateManifest){
            $destination=Join-Path $copy $item.path
            New-Item -ItemType Directory -Force (Split-Path $destination -Parent)|Out-Null
            Copy-Item -LiteralPath (Join-Path $workspaceRoot $item.path) -Destination $destination -Force
            if((Get-FileHash -LiteralPath $destination).Hash -ne $item.sha256){throw 'Working-tree snapshot hash mismatch'}
        }
        $env:TICKETING_RETAIN_TEST_RESOURCES='true'
        $env:TICKETING_AUDIT_OUTPUT=$output
        $env:TICKETING_AUDIT_PROJECT=$runId
        # Scan only candidate source, before generating private test .env or build output.
        Invoke-Step 'security-static' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-release-security.ps1'
    }
    if($RecoveryEvidence){
        $report.testOverlay=@()
        # Refuse deleted fixtures instead of silently keeping a stale file from HEAD.
        $trackedTests=@(& git -C $workspaceRoot ls-files -- backend/src/test)
        if($LASTEXITCODE){throw 'Cannot inventory tracked test fixtures'}
        foreach($tracked in $trackedTests){
            if(!(Test-Path -LiteralPath (Join-Path $workspaceRoot $tracked))){throw "Deleted test requires explicit harness support: $tracked"}
        }
        $testRoot=Join-Path $workspaceRoot 'backend/src/test'
        foreach($file in Get-ChildItem -LiteralPath $testRoot -Recurse -File){
            $relative=$file.FullName.Substring($workspaceRoot.Length+1)
            if($file.Name -match '^\.env' -or $file.Extension -in @('.key','.pem')){throw 'Sensitive file is not a test fixture overlay'}
            $destination=Join-Path $copy $relative
            New-Item -ItemType Directory -Force (Split-Path $destination -Parent)|Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
            $hash=(Get-FileHash -LiteralPath $file.FullName).Hash
            if($hash -ne (Get-FileHash -LiteralPath $destination).Hash){throw 'Candidate overlay hash mismatch'}
            $report.testOverlay+=@{path=$relative.Replace('\','/');sha256=$hash}
        }
        $env:RECOVERY_EVIDENCE_DIR=Join-Path $output 'postgres-snapshots'
        New-Item -ItemType Directory -Force $env:RECOVERY_EVIDENCE_DIR|Out-Null
        $report.snapshotDirectory=$env:RECOVERY_EVIDENCE_DIR
    }
    foreach($p in @('.env','backend/target','frontend/node_modules','tools/contracts/node_modules')){if(Test-Path -LiteralPath (Join-Path $copy $p)){throw "Unexpected existing checkout resource: $p"}}
    if(@(& docker volume ls --filter "label=com.docker.compose.project=$runId" -q).Count){throw 'Project volumes already exist'}
    . (Join-Path $copy 'infra/local-env.ps1')
    $pgPort=Get-Port;$kafkaPort=Get-Port
    while($pgPort -eq $kafkaPort){$kafkaPort=Get-Port}
    $config=[IO.File]::ReadAllText($envPath)
    $config=$config -replace '(?m)^POSTGRES_DB=.*$','POSTGRES_DB=ticketing_invariants'
    $config=$config -replace '(?m)^DB_PORT=.*$',"DB_PORT=$pgPort"
    $config=$config -replace '(?m)^KAFKA_PORT=.*$',"KAFKA_PORT=$kafkaPort"
    $config=$config -replace '(?m)^KAFKA_BOOTSTRAP_SERVERS=.*$',"KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:$kafkaPort"
    [IO.File]::WriteAllText($envPath,$config+"`nCOMPOSE_PROJECT_NAME=$runId`n",[Text.UTF8Encoding]::new($false))
    . (Join-Path $copy 'infra/local-env.ps1')
    $loaded=$true
    if($env:COMPOSE_PROJECT_NAME -ne $runId -or $env:POSTGRES_DB -ne 'ticketing_invariants'){throw 'Isolation configuration mismatch'}
    $report.ports=@{postgres=$pgPort;kafka=$kafkaPort;http='random per harness'}
    $report.isolation='Fresh application HEAD clone; T9.3 overlays current test sources with per-file hashes. Generated .env, new project/volumes; never loads workspace .env. Test DB/topic/group UUID; backend suite also owns a separate Kafka broker.'
    if($WorkingTree){$report.isolation='Full current working-tree snapshot (tracked + nonignored untracked, deletions reflected), SHA256 verified before/after; no workspace .env or artifacts. Own project/ports/DB/topics; all volumes retained.'}
    Save-Report
    Invoke-Step 'install-frontend' '& npm.cmd ci --prefix frontend'
    Invoke-Step 'install-contracts' '& npm.cmd ci --prefix tools/contracts'
    Invoke-Step 'frontend-tests' '& npm.cmd test --prefix frontend'
    Invoke-Step 'frontend-build' '& npm.cmd run build --prefix frontend'
    Invoke-Step 'contracts' '& npm.cmd test --prefix tools/contracts'
    Invoke-Step 'backend-recovery-live' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live'
    $totals=@{tests=0;failures=0;errors=0;skipped=0};$classes=@();$cases=@()
    $junit=Join-Path $copy 'backend/target/surefire-reports'
    foreach($file in Get-ChildItem (Join-Path $junit 'TEST-*.xml')){
        [xml]$xml=Get-Content -LiteralPath $file.FullName
        foreach($n in @('tests','failures','errors','skipped')){$totals[$n]+=[int]$xml.testsuite.$n}
        $classes+=@{name=[string]$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
        foreach($case in $xml.testsuite.testcase){$cases+=@{class=[string]$case.classname;name=[string]$case.name;status=if($case.failure -or $case.error){'FAIL'}elseif($case.skipped){'SKIP'}else{'PASS'}}}
    }
    $report.backend=$totals;$report.classes=$classes;$report.testCases=$cases
    if($totals.tests -eq 0 -or $totals.failures -or $totals.errors -or $totals.skipped){throw 'Full backend requires nonzero tests and zero failures/errors/skips'}
    $artifactDir=Join-Path $output 'artifacts'
    New-Item -ItemType Directory -Force $artifactDir | Out-Null
    foreach($name in @('part4-evidence.json','part5-evidence.json','part6-evidence.json','part7-kafka-evidence.json')){
        Copy-Item -LiteralPath (Join-Path $copy "backend/target/$name") -Destination $artifactDir
    }
    Copy-Item -LiteralPath $junit -Destination (Join-Path $output 'surefire-reports') -Recurse
    # Reuse existing live SQL oracles and actual process restarts from TEST_PLAN.
    Invoke-Step 'ga-live' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4-ui.ps1'
    Invoke-Step 'seats-expiry-restart-live' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5-ui.ps1'
    Invoke-Step 'payments-kafka-restart-live' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1 -Kafka'
    if($WorkingTree){
        Invoke-Step 'release-checks' '& powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-release-checks.ps1'
    }
    Copy-Item -LiteralPath (Join-Path $copy 'frontend/reports') -Destination (Join-Path $output 'frontend-reports') -Recurse
    $report.sqlAndLiveChecks=@(Get-ChildItem (Join-Path $output '*.stdout.log') | Select-String -Pattern '^PASS SQL:|^PASS recovery SQL:|^PASS restart:|^PASS Kafka scheduler:|^PASS pagination:|^PASS:.*live HTTP' | ForEach-Object {$_.Line})
    if($RecoveryEvidence){
        $report.snapshotCount=@(Get-ChildItem -LiteralPath $report.snapshotDirectory -Filter '*.json').Count
        $required=@{
            'broker-outage'=@('before','offline','recovered');'consumer-restart-duplicates'=@('before','after')
            'ack-db-window'=@('before','acked-db-rollback','resent');'consumer-rollback-replay'=@('before','dead-after-three-deliveries','recovered')
            'poison-dlq'=@('before','dead','published');'replay-security-budget'=@('before','budget-exhausted');'replay-noop'=@('before','after')
            'timely-payment-broker-down'=@('before','durable-offline','worker-confirmed-offline','recovered')
            'late-payment-refund'=@('before','expired','first-refund-failed','retried-completed')
            'late-success-seat-reallocated'=@('expired','new-owner-held','late-result-processed')
            'ga-payment-failure'=@('before','released','repeated');'seat-payment-failure'=@('before','released','repeated')
            'scheduled-payment'=@('scheduled','processed-once')
        }
        $expectedCount=0
        foreach($scenario in $required.Keys){foreach($phase in $required[$scenario]){
            $expectedCount++
            $file=Join-Path $report.snapshotDirectory "$scenario--$phase.json"
            if(!(Test-Path -LiteralPath $file) -or !(Test-Path -LiteralPath ($file -replace '\.json$','.sql'))){throw "Missing recovery phase: $scenario/$phase"}
            $text=[IO.File]::ReadAllText($file);$snapshot=$text|ConvertFrom-Json
            if($snapshot.scenario -ne $scenario -or $snapshot.phase -ne $phase -or $snapshot.database -notmatch '^ticketing_it_[a-f0-9]{32}$'){throw 'Snapshot identity/database mismatch'}
            foreach($value in $snapshot.snapshot.violations.PSObject.Properties){if($value.Value -ne 0){throw "Recovery invariant failed: $($value.Name)"}}
            if($text -match '"(raw_event|rawEvent|payload|record_key|authorization|token|password|display_name)"\s*:'){throw 'Private field in published recovery evidence'}
        }}
        if($report.snapshotCount -ne $expectedCount){throw 'Recovery snapshot count mismatch'}
        $report.snapshotCases=$required
        foreach($item in $report.testOverlay){
            if((Get-FileHash -LiteralPath (Join-Path $copy $item.path)).Hash -ne $item.sha256 -or (Get-FileHash -LiteralPath (Join-Path $workspaceRoot $item.path)).Hash -ne $item.sha256){throw 'Test source changed during gate; results do not certify current working tree'}
        }
        if((Get-FileHash -LiteralPath $PSCommandPath).Hash -ne $report.harnessSha256){throw 'Harness changed during gate; rerun current source'}
    }
    if($WorkingTree){
        $current=@(Get-CandidateManifest)
        if(($current|ConvertTo-Json -Compress) -ne ($report.candidateManifest|ConvertTo-Json -Compress)){throw 'Working-tree inventory/content changed during gate; rerun'}
        foreach($item in $current){if((Get-FileHash -LiteralPath (Join-Path $copy $item.path)).Hash -ne $item.sha256){throw 'Candidate source changed during gate'}}
    }
    $password=$env:POSTGRES_PASSWORD
    foreach($log in Get-ChildItem (Join-Path $output '*.log')){
        $content=[IO.File]::ReadAllText($log.FullName)
        if($content.Contains($password) -or $content -match 'demo-customer-token|demo-admin-token|Authorization: Bearer'){throw "Sensitive data in $($log.Name); do not publish raw logs"}
    }
    $report.status='PASS'
} catch {
    $report.status='FAIL';$report.error=$_.Exception.Message
    throw
} finally {
    if($loaded){
        # Stop only our UUID project; retain volumes/copy for inspection, never down demo.
        & docker @composeArgs stop kafka postgres
        if($LASTEXITCODE){$report.status='FAIL';$report.cleanupError='Failed to stop owned project'}
        $report.retainedOwnedVolumes=@(& docker volume ls --filter "label=com.docker.compose.project=$runId" -q)
    }
    if($report.Contains('preexistingContainers')){
        $after=Get-ExistingContainers
        $report.preexistingContainerStatesUnchanged=@(Compare-Object $report.preexistingContainers @($after | Where-Object {($_ -split '\|')[0] -in @($report.preexistingContainers | ForEach-Object {($_ -split '\|')[0]})})).Count -eq 0
        $currentVolumes=@(& docker volume ls -q)
        $report.preexistingVolumesRetained=@($report.preexistingVolumes | Where-Object {$_ -notin $currentVolumes}).Count -eq 0
        $envAfter=if(Test-Path -LiteralPath $originalEnv){(Get-FileHash -LiteralPath $originalEnv).Hash}else{$null}
        $report.workspaceEnvUnchanged=$envBefore -eq $envAfter
        if(!$report.preexistingContainerStatesUnchanged -or !$report.preexistingVolumesRetained -or !$report.workspaceEnvUnchanged){$report.status='FAIL';$report.preservationError='Preexisting resources changed'}
    }
    $report.finished=Get-Date -Format o
    Save-Report
    Write-Output "$($report.status) $task`: $output/summary.json"
}
if($report.status -ne 'PASS'){throw "$task evidence incomplete; see summary.json"}
