. "$PSScriptRoot/local-env.ps1"
& docker @composeArgs up -d --wait postgres
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL failed to start.' }
Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend build/test failed.' }
    # Foreground JVM: Ctrl+C stops backend; PostgreSQL data remains in demo volume.
    & java '-Duser.timezone=UTC' -jar target/ticketing-backend-0.0.1-SNAPSHOT.jar
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error.' }
} finally { Pop-Location }
