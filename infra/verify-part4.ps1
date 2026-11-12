param([string]$Key='windows-ga-smoke-part4-v1')
. "$PSScriptRoot/local-env.ps1"
$base='http://127.0.0.1:8080'
$headers=@{Authorization='Bearer demo-customer-token';'Idempotency-Key'=$Key}
$body='{"eventId":"20000000-0000-4000-8000-000000000002","ticketTypeId":"30000000-0000-4000-8000-000000000002","quantity":1}'
function Text-Body($response){if($response.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($response.Content)}else{$response.Content}}
$first=Invoke-WebRequest -UseBasicParsing "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$second=Invoke-WebRequest -UseBasicParsing "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$text=Text-Body $first;$result=$text|ConvertFrom-Json
if($first.StatusCode -ne 202 -or $second.StatusCode -ne 202 -or $text -ne (Text-Body $second)){throw 'Idempotency replay mismatch'}
if($first.Headers['Location'] -ne "/api/bookings/$($result.bookingId)"){throw 'Location header mismatch'}
$booking=Invoke-RestMethod "$base/api/bookings/$($result.bookingId)" -Headers $headers
$mine=Invoke-RestMethod "$base/api/me/bookings" -Headers $headers
if($booking.bookingId -ne $result.bookingId -or $booking.history.Count -lt 1 -or $mine.totalElements -lt 1){throw 'Booking/owner/history mismatch'}
$migration="SELECT count(*) FROM public.flyway_schema_history WHERE version IN ('1','2','3','4','5','6') AND success;" | docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -At -v ON_ERROR_STOP=1
if($LASTEXITCODE -ne 0 -or ($migration -join '').Trim() -ne '6'){throw 'Expected V1-V6 exactly once'}
Write-Output "PASS Part 4: HTTP202 + Location, same key/body replays one booking, owner GET/history; booking=$($result.bookingId), serverStatus=$($booking.status), totalVnd=$($result.totalAmountVnd)."
Write-Output 'The first successful run holds 1 demo GA ticket. Reusing this key does not hold another ticket.'
