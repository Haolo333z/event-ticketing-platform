param([string]$Tests='*Test')
. "$PSScriptRoot/local-env.ps1"
& docker @composeArgs up -d --wait postgres
if($LASTEXITCODE -ne 0){throw 'PostgreSQL unavailable'}
$testDatabase='ticketing_it_'+[Guid]::NewGuid().ToString('N')
& docker @composeArgs exec -T postgres createdb -U $env:POSTGRES_USER $testDatabase
if($LASTEXITCODE -ne 0){throw 'Cannot create isolated test database'}
$env:PG_IT_URL="jdbc:postgresql://127.0.0.1:$($env:DB_PORT)/$testDatabase"
Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\mvnw.cmd -B -ntp "-Dtest=$Tests" '-Duser.timezone=UTC' verify
    if($LASTEXITCODE -ne 0){throw 'Part 3 tests failed; inspect backend/target/surefire-reports'}
} finally {
    Pop-Location
    Remove-Item Env:PG_IT_URL -ErrorAction SilentlyContinue
    # Only the fresh, uniquely named database created above; preserve the user's demo DB.
    & docker @composeArgs exec -T postgres dropdb -U $env:POSTGRES_USER --force $testDatabase
}
