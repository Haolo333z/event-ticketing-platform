param([ValidateRange(10,1800)][int]$WaitSeconds=660)
$ErrorActionPreference='Stop'
$base='http://127.0.0.1:8080'
$customer=@{Authorization='Bearer demo-customer-token'}
$admin=@{Authorization='Bearer demo-admin-token'}
$body=@{
    title=('Payment Windows '+[Guid]::NewGuid().ToString('N'));description='Manual payment fixture';venue='Local demo'
    saleStartAt='2020-01-01T00:00:00Z';saleEndAt='2099-01-01T00:00:00Z';startsAt='2099-01-02T00:00:00Z';status='PUBLISHED'
    ticketTypes=@(@{name='GA';mode='GA';priceVnd=100000;capacity=10},@{name='Seats';mode='SEATED';priceVnd=200000;seats=@(@{label='A1'},@{label='A2'})})
}|ConvertTo-Json -Depth 7
$event=Invoke-RestMethod "$base/api/admin/events" -Method Post -Headers $admin -ContentType 'application/json' -Body $body
$ga=$event.ticketTypes|Where-Object mode -eq 'GA'
$seated=$event.ticketTypes|Where-Object mode -eq 'SEATED'
function New-Booking($command){
    $headers=@{Authorization='Bearer demo-customer-token';'Idempotency-Key'=[Guid]::NewGuid().ToString()}
    Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body ($command|ConvertTo-Json -Depth 5)
}
function Simulate($id,$scenario,$requestId){
    Invoke-RestMethod "$base/api/bookings/$id/simulate-payment" -Method Post -Headers $customer -ContentType 'application/json' -Body (@{scenario=$scenario;requestId=$requestId}|ConvertTo-Json)
}
$booking=New-Booking @{eventId=$event.id;ticketTypeId=$ga.id;quantity=3}
$requestId=[Guid]::NewGuid().ToString()
$payment=Simulate $booking.bookingId 'SUCCESS' $requestId
$again=Simulate $booking.bookingId 'SUCCESS' $requestId
$tickets=Invoke-RestMethod "$base/api/bookings/$($booking.bookingId)/tickets" -Headers $customer
if($payment.status -ne 'ACCEPTED' -or $payment.paymentIntentId -ne $again.paymentIntentId -or $tickets.Count -ne 3){throw 'GA success/replay/ticket quantity failed'}
$snapshot=Invoke-RestMethod "$base/api/events/$($event.id)/availability"
$seatIds=@(($snapshot.ticketTypes|Where-Object mode -eq 'SEATED').seats|ForEach-Object id)
$seatBooking=New-Booking @{eventId=$event.id;ticketTypeId=$seated.id;seatIds=$seatIds}
$null=Simulate $seatBooking.bookingId 'SUCCESS' ([Guid]::NewGuid().ToString())
$seatTickets=Invoke-RestMethod "$base/api/bookings/$($seatBooking.bookingId)/tickets" -Headers $customer
if($seatTickets.Count -ne 2 -or @($seatTickets|Where-Object {$_.seatLabel -in @('A1','A2') -and $_.ticketOrdinal -eq 1}).Count -ne 2){throw 'Seated ticket allocation failed'}
$failure=New-Booking @{eventId=$event.id;ticketTypeId=$ga.id;quantity=1}
$null=Simulate $failure.bookingId 'FAILURE' ([Guid]::NewGuid().ToString())
if((Invoke-RestMethod "$base/api/bookings/$($failure.bookingId)" -Headers $customer).status -ne 'PAYMENT_FAILED'){throw 'Expected payment failure'}
$cancel=New-Booking @{eventId=$event.id;ticketTypeId=$ga.id;quantity=1}
$cancelled=Invoke-RestMethod "$base/api/bookings/$($cancel.bookingId)/cancel" -Method Post -Headers $customer
$repeat=Invoke-RestMethod "$base/api/bookings/$($cancel.bookingId)/cancel" -Method Post -Headers $customer
if($cancelled.status -ne 'CANCELLED' -or $repeat.status -ne 'CANCELLED'){throw 'Cancel/repeat failed'}
$late=New-Booking @{eventId=$event.id;ticketTypeId=$ga.id;quantity=1}
$null=Simulate $late.bookingId 'DELAY_TIMEOUT' ([Guid]::NewGuid().ToString())
$deadline=[DateTimeOffset]::UtcNow.AddSeconds($WaitSeconds)
do {
    $current=Invoke-RestMethod "$base/api/bookings/$($late.bookingId)" -Headers $customer
    if($current.status -eq 'REFUND_REQUIRED'){break}
    Start-Sleep -Seconds 1
} while([DateTimeOffset]::UtcNow -lt $deadline)
if($current.status -ne 'REFUND_REQUIRED'){throw 'Wait budget exceeded. Check scheduler or use launcher -HoldSeconds 20 -ExpiryIntervalSeconds 1.'}
$history=@($current.history|ForEach-Object toStatus)
if(($history -join ',') -ne 'PENDING_PAYMENT,EXPIRED,REFUND_REQUIRED'){throw 'Expected expiry then refund-required history'}
$empty=Invoke-WebRequest -UseBasicParsing "$base/api/bookings/$($late.bookingId)/tickets" -Headers $customer
$text=if($empty.Content -is [byte[]]){[Text.Encoding]::UTF8.GetString($empty.Content)}else{$empty.Content}
if($text -notmatch '^\s*\[\s*\]\s*$'){throw 'Late success must have no tickets'}
Write-Output "PASS Windows: event=$($event.id); GA booking=$($booking.bookingId),3tickets; seated booking=$($seatBooking.bookingId),2tickets; repeated request unchanged; FAILURE/CANCELLED; delayed=$($late.bookingId),REFUND_REQUIRED/0tickets."
Write-Output 'Demo ticket codes from the API:'
$tickets|Select-Object id,ticketOrdinal,seatLabel,code
$seatTickets|Select-Object id,ticketOrdinal,seatLabel,code
Write-Output 'This script leaves its own catalog, bookings and issued demo tickets for review; it does not reset the database.'
