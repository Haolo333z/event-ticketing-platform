param([Nullable[int]]$Port,[ValidateSet('127.0.0.1','::1')][string]$FrontendAddress='127.0.0.1',[switch]$StopServices)
. "$PSScriptRoot/local-env.ps1"
$backendPort=8080
if($null -ne $Port){$backendPort=[int]$Port}
elseif($env:SERVER_PORT -and ![int]::TryParse($env:SERVER_PORT,[ref]$backendPort)){throw 'SERVER_PORT must be an integer'}
if($backendPort -lt 1 -or $backendPort -gt 65535){throw 'Port must be 1..65535'}
$backendJar=(Join-Path $PSScriptRoot '.local-runtime/backend.jar').Replace('/','\')
$vite=(Join-Path $repoRoot 'frontend/node_modules/vite/bin/vite.js').Replace('/','\')
$targets=@()
foreach($spec in @(@{Port=$backendPort;Address='127.0.0.1';Name='java.exe';Path=$backendJar},@{Port=5173;Address=$FrontendAddress;Name='node.exe';Path=$vite})){
    foreach($ownerId in @(Get-NetTCPConnection -State Listen -LocalPort $spec.Port -ErrorAction SilentlyContinue | Where-Object LocalAddress -eq $spec.Address | Select-Object -ExpandProperty OwningProcess -Unique)){
        $process=Get-CimInstance Win32_Process -Filter "ProcessId=$ownerId"
        if(!$process -or $process.Name -ne $spec.Name -or !$process.CommandLine -or $process.CommandLine.Replace('/','\').IndexOf($spec.Path,[StringComparison]::OrdinalIgnoreCase) -lt 0){throw "Port$($spec.Port) belongs to another process; refusing to stop anything."}
        $targets+=$process
    }
}
# Validate all listeners before stopping any; recheck identity against PID reuse.
foreach($target in $targets){
    $current=Get-CimInstance Win32_Process -Filter "ProcessId=$($target.ProcessId)"
    if($current -and ($current.Name -ne $target.Name -or $current.CommandLine -ne $target.CommandLine)){throw 'Process identity changed; refusing to stop it.'}
    if($current){Stop-Process -Id $current.ProcessId;Wait-Process -Id $current.ProcessId -ErrorAction SilentlyContinue}
}
if($StopServices){& docker @composeArgs stop kafka postgres;if($LASTEXITCODE -ne 0){throw 'Service stop failed'}}
Write-Output 'Stopped only matching workspace listeners. Docker volumes were not deleted.'
