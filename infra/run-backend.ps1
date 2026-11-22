param()
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/local-env.ps1"
$runtimeDir = "$PSScriptRoot/.local-runtime"
New-Item -ItemType Directory -Force $runtimeDir | Out-Null
Copy-Item "$repoRoot/backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar" -Destination "$runtimeDir/backend.jar" -Force
& java '-Duser.timezone=UTC' -jar "$runtimeDir/backend.jar" --server.port=8080
