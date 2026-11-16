$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/../infra/local-env.ps1"
$env:SPRING_PROFILES_ACTIVE = 'local'
java "-Duser.timezone=UTC" -jar (Join-Path $repoRoot 'backend/target/ticketing-backend-0.0.1-SNAPSHOT.jar') --server.port=8080
