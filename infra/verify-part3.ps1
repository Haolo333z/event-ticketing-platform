. "$PSScriptRoot/local-env.ps1"
& "$PSScriptRoot/verify-local.ps1"
$base='http://127.0.0.1:8080'
$events=Invoke-RestMethod "$base/api/events"
if($events.totalElements -lt 2){throw 'Expected at least two demo events'}
$seated=Invoke-RestMethod "$base/api/events/20000000-0000-4000-8000-000000000001"
$ga=Invoke-RestMethod "$base/api/events/20000000-0000-4000-8000-000000000002"
if($seated.ticketTypes[0].mode -ne 'SEATED' -or $ga.ticketTypes[0].mode -ne 'GA'){throw 'Wrong demo event modes'}
$seats=Invoke-RestMethod "$base/api/events/$($seated.id)/availability"
$capacity=Invoke-RestMethod "$base/api/events/$($ga.id)/availability"
if($seats.ticketTypes[0].seats.Count -ne 4 -or $capacity.ticketTypes[0].available -ne 100){throw 'Demo inventory differs from initial seed (may have been edited)'}
function Assert-HttpError([int]$Expected,[string]$Uri,[hashtable]$Headers=@{}) {
    try {Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Post -ContentType 'application/json' -Body '{}' -Headers $Headers | Out-Null}
    catch {if([int]$_.Exception.Response.StatusCode -eq $Expected){return};throw}
    throw "Expected HTTP $Expected"
}
Assert-HttpError 401 "$base/api/admin/events"
Assert-HttpError 401 "$base/api/admin/events" @{Authorization='Bearer unknown-token'}
Assert-HttpError 403 "$base/api/admin/events" @{Authorization='Bearer demo-customer-token'}
$sql="SELECT count(*) FROM public.flyway_schema_history WHERE version IN ('1','2','3','4') AND success;"
$count=$sql | docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -At -v ON_ERROR_STOP=1
if($LASTEXITCODE -ne 0 -or ($count -join '').Trim() -ne '4'){throw 'Expected successful migrations V1 through V4 exactly once'}
Write-Output 'PASS Part 3: demo SEATED/GA, 4 seats, GA100, HTTP401/403, migrations V1-V4.'
