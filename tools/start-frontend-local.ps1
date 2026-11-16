$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../infra/local-env.ps1"
$vite = Join-Path $repoRoot 'frontend/node_modules/vite/bin/vite.js'
Push-Location (Join-Path $repoRoot 'frontend')
try {
    node $vite --host 127.0.0.1 --port 5173 --strictPort
} finally {
    Pop-Location
}
