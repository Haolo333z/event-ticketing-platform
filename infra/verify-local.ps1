param([Nullable[int]]$Port)
. "$PSScriptRoot/local-env.ps1"
$backendPort=8080
if($null -ne $Port){$backendPort=[int]$Port}
elseif($env:SERVER_PORT -and ![int]::TryParse($env:SERVER_PORT,[ref]$backendPort)){throw 'SERVER_PORT must be an integer'}
if($backendPort -lt 1 -or $backendPort -gt 65535){throw 'Port must be 1..65535'}
$response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$backendPort/actuator/health"
$body = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { $response.Content }
if ($response.StatusCode -ne 200 -or ($body | ConvertFrom-Json).status -ne 'UP') {
    throw 'Backend health is not UP.'
}
$sql = "SELECT count(*) FROM public.flyway_schema_history WHERE version='1' AND success;"
$result = $sql | & docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -At
if ($LASTEXITCODE -ne 0 -or ($result -join '').Trim() -ne '1') { throw 'Expected one successful V1 migration.' }
$schema = "SELECT count(*) FROM information_schema.schemata WHERE schema_name='ticketing';" | & docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -At
if ($LASTEXITCODE -ne 0 -or ($schema -join '').Trim() -ne '1') { throw 'Application schema is missing.' }
Write-Output 'PASS: HTTP 200 / UP; V1 applied exactly once; ticketing schema exists.'
