param([ValidateSet('127.0.0.1','localhost','::1')][string]$BindAddress='127.0.0.1')
. "$PSScriptRoot/local-env.ps1"
$vite=Join-Path $repoRoot 'frontend/node_modules/vite/bin/vite.js'
if(!(Test-Path -LiteralPath $vite)){throw 'Run npm.cmd ci --prefix frontend first; no automatic package download at startup.'}
# Keep port5173 for the existing CORS policy; ::1 permits a separate localhost test beside127.0.0.1.
Push-Location (Join-Path $repoRoot 'frontend')
try {
    & node $vite --host $BindAddress --port 5173 --strictPort
    if($LASTEXITCODE -ne 0){throw 'Frontend exited. Check port5173 and the Vite output.'}
} finally {Pop-Location}
