param([Nullable[int]]$HoldSeconds,[Nullable[int]]$ExpiryIntervalSeconds,[switch]$RestartExisting,[Nullable[int]]$Port)
. "$PSScriptRoot/local-env.ps1"
$backendPort=8080
if($null -ne $Port){$backendPort=[int]$Port}
elseif($env:SERVER_PORT -and ![int]::TryParse($env:SERVER_PORT,[ref]$backendPort)){throw 'SERVER_PORT must be an integer'}
if($backendPort -lt 1 -or $backendPort -gt 65535){throw 'Port must be 1..65535'}
$env:SERVER_PORT=$backendPort.ToString()
if($null -ne $HoldSeconds){
    if($HoldSeconds -lt 1 -or $HoldSeconds -gt 86400){throw 'HoldSeconds must be 1..86400'}
    $env:BOOKING_HOLD_TTL_SECONDS=$HoldSeconds.ToString()
}
if($null -ne $ExpiryIntervalSeconds){
    if($ExpiryIntervalSeconds -lt 1 -or $ExpiryIntervalSeconds -gt 3600){throw 'ExpiryIntervalSeconds must be 1..3600'}
    $env:EXPIRY_INTERVAL_SECONDS=$ExpiryIntervalSeconds.ToString()
}
# Replacing only this workspace's copied runtime is explicit; never stop unrelated listeners.
$expectedRuntime=Join-Path $PSScriptRoot '.local-runtime/backend.jar'
$listeners=@(Get-NetTCPConnection -State Listen -LocalPort $backendPort -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
foreach($listenerId in $listeners){
    if(!$RestartExisting){throw "Port$backendPort is busy. Stop the old backend or use -RestartExisting for this workspace runtime."}
    $listenerProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$listenerId"
    $expectedCommand=$expectedRuntime.Replace('/','\')
    if(!$listenerProcess -or $listenerProcess.Name -ne 'java.exe' -or !$listenerProcess.CommandLine -or $listenerProcess.CommandLine.Replace('/','\').IndexOf($expectedCommand,[StringComparison]::OrdinalIgnoreCase) -lt 0){throw "Port$backendPort belongs to another process; refusing to stop it."}
}
& docker @composeArgs up -d --wait postgres kafka
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL/Kafka unavailable; inspect docker compose ps and logs.' }
Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend build/test failed.' }
    # Build first so a failed build leaves the existing runtime available.
    foreach($listenerId in $listeners){
        $listenerProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$listenerId"
        if($listenerProcess){
            if($listenerProcess.Name -ne 'java.exe' -or !$listenerProcess.CommandLine -or $listenerProcess.CommandLine.Replace('/','\').IndexOf($expectedRuntime.Replace('/','\'),[StringComparison]::OrdinalIgnoreCase) -lt 0){throw 'Runtime identity changed during build; refusing to stop it.'}
            Stop-Process -Id $listenerId
            Wait-Process -Id $listenerId -ErrorAction SilentlyContinue
        }
    }
    # Run a separate copy so Windows does not lock the build artifact during later tests.
    $runtimeDir=Join-Path $PSScriptRoot '.local-runtime'
    New-Item -ItemType Directory -Force $runtimeDir | Out-Null
    $runtimeJar=Join-Path $runtimeDir 'backend.jar'
    Copy-Item -LiteralPath 'target/ticketing-backend-0.0.1-SNAPSHOT.jar' -Destination $runtimeJar -Force
    # Foreground JVM: Ctrl+C stops backend; PostgreSQL data remains in demo volume.
    & java '-Duser.timezone=UTC' -jar $runtimeJar "--server.port=$backendPort"
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' }
} finally { Pop-Location }
