param([Nullable[int]]$HoldSeconds,[Nullable[int]]$ExpiryIntervalSeconds,[switch]$RestartExisting)
. "$PSScriptRoot/local-env.ps1"
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
$listeners=@(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)
foreach($listenerId in $listeners){
    if(!$RestartExisting){throw 'Port8080 is busy. Stop the old backend or use -RestartExisting for this workspace runtime.'}
    $listenerProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$listenerId"
    $expectedCommand=$expectedRuntime.Replace('/','\')
    if(!$listenerProcess -or $listenerProcess.Name -ne 'java.exe' -or !$listenerProcess.CommandLine -or $listenerProcess.CommandLine.Replace('/','\').IndexOf($expectedCommand,[StringComparison]::OrdinalIgnoreCase) -lt 0){throw 'Port8080 belongs to another process; refusing to stop it.'}
}
& docker @composeArgs up -d --wait postgres
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL failed to start.' }
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
    & java '-Duser.timezone=UTC' -jar $runtimeJar
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' }
} finally { Pop-Location }
