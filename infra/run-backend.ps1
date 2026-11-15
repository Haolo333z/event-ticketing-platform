$lines = Get-Content (Join-Path $PSScriptRoot '..\.env')
foreach ($line in $lines) {
    if ($line -match '^([^#=\s]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
    }
}
$jar = (Join-Path $PSScriptRoot '.local-runtime\backend.jar')
& java "-Duser.timezone=UTC" -jar "$jar" "--server.port=8080"
