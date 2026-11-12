. "$PSScriptRoot/local-env.ps1"
$base='http://127.0.0.1:8080'
$customer=@{Authorization='Bearer demo-customer-token'}
$admin=@{Authorization='Bearer demo-admin-token'}
$health=Invoke-RestMethod "$base/actuator/health"
if($health.status -ne 'UP'){throw 'Backend is not UP'}
$catalog=@{
    title='Kafka smoke '+[Guid]::NewGuid().ToString();description='Isolated local smoke fixture';venue='Local'
    saleStartAt='2020-01-01T00:00:00Z';saleEndAt='2099-01-01T00:00:00Z';startsAt='2099-01-02T00:00:00Z';status='PUBLISHED'
    ticketTypes=@(@{name='GA';mode='GA';priceVnd=100000;capacity=6})
}|ConvertTo-Json -Depth 6
$event=Invoke-RestMethod "$base/api/admin/events" -Method Post -Headers $admin -ContentType 'application/json' -Body $catalog
$command=@{eventId=$event.id;ticketTypeId=$event.ticketTypes[0].id;quantity=2}|ConvertTo-Json
$bookingHeaders=@{Authorization='Bearer demo-customer-token';'Idempotency-Key'=[Guid]::NewGuid().ToString()}
$booking=Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $bookingHeaders -ContentType 'application/json' -Body $command
$payment=@{scenario='SUCCESS';requestId=[Guid]::NewGuid().ToString()}|ConvertTo-Json
$accepted=Invoke-RestMethod "$base/api/bookings/$($booking.bookingId)/simulate-payment" -Method Post -Headers $customer -ContentType 'application/json' -Body $payment
if($accepted.status -ne 'ACCEPTED'){throw 'Payment command was not accepted'}
for($i=0;$i -lt 150;$i++){
    $current=Invoke-RestMethod "$base/api/bookings/$($booking.bookingId)" -Headers $customer
    if($current.status -eq 'CONFIRMED'){break}
    Start-Sleep -Milliseconds 200
}
if($current.status -ne 'CONFIRMED'){throw "Expected CONFIRMED; received $($current.status). Check Kafka/outbox."}
$tickets=Invoke-RestMethod "$base/api/bookings/$($booking.bookingId)/tickets" -Headers $customer
if($tickets.Count -ne 2){throw 'Expected exactly 2 tickets'}
$outbox=Invoke-RestMethod "$base/api/admin/operations/outbox?size=100" -Headers $admin
$notification=@($outbox.items|Where-Object {$_.bookingId -eq $booking.bookingId -and $_.eventType -eq 'BookingConfirmed'})
if($notification.Count -ne 1){throw 'Missing confirmed notification'}
$replay=@{requestId=[Guid]::NewGuid().ToString();reason='Windows smoke replay confirmed notification'}|ConvertTo-Json
1..2|ForEach-Object {Invoke-RestMethod "$base/api/admin/operations/events/$($notification[0].eventId)/replay" -Method Post -Headers $admin -ContentType 'application/json' -Body $replay|Out-Null}
$bookingId=$booking.bookingId
$eventId=$notification[0].eventId
$proof=$null
for($i=0;$i -lt 100;$i++){
    $sql="SELECT (SELECT count(*) FROM ticketing.tickets WHERE booking_id='$bookingId'),(SELECT sold_count FROM ticketing.inventory_ga WHERE ticket_type_id='$($event.ticketTypes[0].id)'),(SELECT jsonb_array_length(replay_requests) FROM ticketing.outbox_events WHERE id='$eventId'),(SELECT count(*) FROM ticketing.inbox_events WHERE consumer='payment-v1' AND aggregate_id='$bookingId'),(SELECT last_applied_version FROM ticketing.event_projections WHERE aggregate_id='$bookingId'),(SELECT count(*) FROM ticketing.outbox_events WHERE aggregate_id='$bookingId' AND status<>'PUBLISHED');"
    $proof=$sql|& docker @composeArgs exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -At -v ON_ERROR_STOP=1
    if(($proof -join '').Trim() -eq '2|2|1|1|3|0'){break}
    Start-Sleep -Milliseconds 200
}
if($LASTEXITCODE -ne 0 -or ($proof -join '').Trim() -ne '2|2|1|1|3|0'){throw "Kafka/PostgreSQL invariants failed: $proof"}
Write-Output "PASS Kafka Windows smoke: booking=$bookingId;CONFIRMED;2tickets;GA sold2;payment inbox1;projection version3;one replay audit for duplicate request;all events PUBLISHED."
