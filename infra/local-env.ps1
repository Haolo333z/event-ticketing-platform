$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $repoRoot '.env'
if (!(Test-Path -LiteralPath $envPath)) {
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $template = [IO.File]::ReadAllText((Join-Path $repoRoot '.env.example'))
    $template = $template -replace '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$password"
    [IO.File]::WriteAllText($envPath, $template, [Text.UTF8Encoding]::new($false))
}
# Parse simple KEY=value entries as data; never execute .env as PowerShell code.
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
    throw 'Set a nonempty POSTGRES_PASSWORD in root .env before starting.'
}
$composeArgs = @('compose', '--env-file', $envPath, '-f', (Join-Path $PSScriptRoot 'docker-compose.yml'))
