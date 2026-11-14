-- Read-only T10.1 restart audit on owned project ticketing-readme-94eeadabbaa1, DB ticketing_readme; never run against old demo.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT jsonb_build_object(
 'database',current_database(),
 'tableHashes',jsonb_build_object('users',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.users r),
'events',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.events r),
'ticket_types',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.ticket_types r),
'bookings',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.bookings r),
'seats',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.seats r),
'inventory_ga',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.inventory_ga r),
'booking_items',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.booking_items r),
'tickets',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.tickets r),
'payment_compensations',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.payment_compensations r),
'inbox_events',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.inbox_events r),
'event_projections',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.event_projections r),
'projection_events',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.projection_events r),
'message_failures',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.message_failures r),
'compensation_attempts',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.compensation_attempts r),
'payments',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.payments r),
'outbox_events',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.outbox_events r),
'idempotency_keys',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.idempotency_keys r),
'booking_status_history',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ticketing.booking_status_history r)),
 'counts',jsonb_build_object('users',(SELECT count(*) FROM ticketing.users),'events',(SELECT count(*) FROM ticketing.events),'ticket_types',(SELECT count(*) FROM ticketing.ticket_types),'bookings',(SELECT count(*) FROM ticketing.bookings),'seats',(SELECT count(*) FROM ticketing.seats),'inventory_ga',(SELECT count(*) FROM ticketing.inventory_ga),'booking_items',(SELECT count(*) FROM ticketing.booking_items),'tickets',(SELECT count(*) FROM ticketing.tickets),'payment_compensations',(SELECT count(*) FROM ticketing.payment_compensations),'inbox_events',(SELECT count(*) FROM ticketing.inbox_events),'event_projections',(SELECT count(*) FROM ticketing.event_projections),'projection_events',(SELECT count(*) FROM ticketing.projection_events),'message_failures',(SELECT count(*) FROM ticketing.message_failures),'compensation_attempts',(SELECT count(*) FROM ticketing.compensation_attempts),'payments',(SELECT count(*) FROM ticketing.payments),'outbox_events',(SELECT count(*) FROM ticketing.outbox_events),'idempotency_keys',(SELECT count(*) FROM ticketing.idempotency_keys),'booking_status_history',(SELECT count(*) FROM ticketing.booking_status_history)),
 'migrations',(SELECT jsonb_agg(jsonb_build_object('version',version,'checksum',checksum,'success',success,'installedOn',installed_on) ORDER BY installed_rank) FROM public.flyway_schema_history),
 'migrationHistoryHash',(SELECT encode(sha256(convert_to(jsonb_agg(to_jsonb(f) ORDER BY installed_rank)::text,'UTF8')),'hex') FROM public.flyway_schema_history f),
 'bookings',(SELECT jsonb_agg(jsonb_build_object('id',b.id,'mode',tt.mode,'status',b.status,'totalVnd',b.total_amount_vnd,'units',(SELECT sum(quantity) FROM ticketing.booking_items i WHERE i.booking_id=b.id)) ORDER BY tt.mode,b.id) FROM ticketing.bookings b JOIN ticketing.ticket_types tt ON tt.id=b.ticket_type_id),
 'tickets',(SELECT jsonb_agg(jsonb_build_object('id',t.id,'bookingId',t.booking_id,'itemId',t.booking_item_id,'ordinal',t.ticket_ordinal,'seatId',i.seat_id,'seatLabel',s.label,'codeSha256',encode(sha256(convert_to(t.code,'UTF8')),'hex')) ORDER BY t.booking_id,t.ticket_ordinal) FROM ticketing.tickets t JOIN ticketing.booking_items i ON i.id=t.booking_item_id LEFT JOIN ticketing.seats s ON s.id=i.seat_id),
 'ga',(SELECT jsonb_agg(jsonb_build_object('capacity',capacity,'held',held_count,'sold',sold_count)) FROM ticketing.inventory_ga),
 'seats',(SELECT jsonb_agg(jsonb_build_object('label',label,'status',status,'owner',held_by_booking_id) ORDER BY label) FROM ticketing.seats),
 'violations',jsonb_build_object(
   'oversell',(SELECT count(*) FROM ticketing.inventory_ga WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
   'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN ticketing.bookings b ON b.id=t.booking_id JOIN ticketing.booking_items i ON i.id=t.booking_item_id WHERE b.status<>'CONFIRMED' OR i.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
   'duplicateTicketOrdinal',(SELECT count(*) FROM (SELECT booking_item_id,ticket_ordinal FROM ticketing.tickets GROUP BY 1,2 HAVING count(*)>1) d),
   'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN ticketing.booking_items i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
   'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history GROUP BY 1,2 HAVING count(*)>1) d),
   'wrongSoldOwner',(SELECT count(*) FROM ticketing.seats s LEFT JOIN ticketing.bookings b ON b.id=s.held_by_booking_id WHERE s.status='SOLD' AND (b.id IS NULL OR b.status<>'CONFIRMED'))
 ));
COMMIT;