param([ValidateRange(10,1800)][int]$WaitSeconds=660)
. "$PSScriptRoot/local-env.ps1"
$base='http://127.0.0.1:8080'
$event='20000000-0000-4000-8000-000000000001'
$snapshot=Invoke-RestMethod "$base/api/events/$event/availability"
$type=$snapshot.ticketTypes | Where-Object mode -eq 'SEATED' | Select-Object -First 1
$seats=@($type.seats | Where-Object status -eq 'AVAILABLE' | Sort-Object label | Select-Object -First 2)
if($seats.Count -ne 2){throw 'Need two AVAILABLE demo seats. Wait for existing holds to expire; do not reset the database.'}
$headers=@{Authorization='Bearer demo-customer-token';'Idempotency-Key'=('windows-seat-'+[Guid]::NewGuid().ToString('N'))}
$body=@{eventId=$event;ticketTypeId=$type.ticketTypeId;seatIds=@($seats | ForEach-Object id)} | ConvertTo-Json -Depth 5
$first=Invoke-WebRequest -UseBasicParsing "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$text=if($first.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($first.Content)}else{$first.Content}
$booking=$text | ConvertFrom-Json
if($first.StatusCode -ne 202 -or $first.Headers['Location'] -ne "/api/bookings/$($booking.bookingId)"){throw 'Expected202/Location'}
$again=Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
if($again.bookingId -ne $booking.bookingId -or $again.expiresAt -ne $booking.expiresAt){throw 'Replay mismatch'}
Write-Output "Held $($seats.label -join ','): $($booking.bookingId), status=$($booking.status), total=$($booking.totalAmountVnd), expiresAt=$($booking.expiresAt). Replay uses the same booking."
$deadline=[DateTimeOffset]::UtcNow.AddSeconds($WaitSeconds)
do {
    $current=Invoke-RestMethod "$base/api/bookings/$($booking.bookingId)" -Headers $headers
    if($current.status -eq 'EXPIRED'){break}
    Start-Sleep -Seconds 1
} while([DateTimeOffset]::UtcNow -lt $deadline)
if($current.status -ne 'EXPIRED'){throw 'Not expired within wait budget. Check worker logs; use start-local.ps1 -HoldSeconds 20 -ExpiryIntervalSeconds 1 for a short demo.'}
if(!$current.expiredAt -or @($current.history | Where-Object toStatus -eq 'EXPIRED').Count -ne 1){throw 'Expected one EXPIRED history transition'}
$free=Invoke-RestMethod "$base/api/events/$event/availability"
foreach($seat in $seats){if(@($free.ticketTypes.seats | Where-Object {$_.id -eq $seat.id -and $_.status -eq 'AVAILABLE'}).Count -ne 1){throw 'Seat not available after expiry'}}
$oldReplay=Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
if($oldReplay.bookingId -ne $booking.bookingId -or $oldReplay.status -ne 'PENDING_PAYMENT'){throw 'Expired replay must preserve the original response'}
$headers['Idempotency-Key']='windows-seat-rebook-'+[Guid]::NewGuid().ToString('N')
$next=Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
if($next.bookingId -eq $booking.bookingId -or $next.status -ne 'PENDING_PAYMENT'){throw 'Expected a new booking after release'}
Write-Output "PASS: old booking EXPIRED once; both seats AVAILABLE; old key replays without holding again; new booking=$($next.bookingId). The new hold expires normally at $($next.expiresAt)."
