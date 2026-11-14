-- queryId=ack-db-window:02177de0-d9fa-461c-85b3-9cdd2b5439c0; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='02177de0-d9fa-461c-85b3-9cdd2b5439c0'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='02177de0-d9fa-461c-85b3-9cdd2b5439c0'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='02177de0-d9fa-461c-85b3-9cdd2b5439c0'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='02177de0-d9fa-461c-85b3-9cdd2b5439c0'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=broker-outage:dd983816-d4c0-40bd-b5b8-24ffaa783117; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='dd983816-d4c0-40bd-b5b8-24ffaa783117'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='dd983816-d4c0-40bd-b5b8-24ffaa783117'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='dd983816-d4c0-40bd-b5b8-24ffaa783117'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='dd983816-d4c0-40bd-b5b8-24ffaa783117'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=consumer-restart-duplicates:67769b58-2b35-48c7-906f-f673098d91c8; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='67769b58-2b35-48c7-906f-f673098d91c8'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='67769b58-2b35-48c7-906f-f673098d91c8'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='67769b58-2b35-48c7-906f-f673098d91c8'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='67769b58-2b35-48c7-906f-f673098d91c8'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=consumer-rollback-replay:169cf98d-5e5b-4642-ba7e-8244c5e3f196; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='169cf98d-5e5b-4642-ba7e-8244c5e3f196'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='169cf98d-5e5b-4642-ba7e-8244c5e3f196'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='169cf98d-5e5b-4642-ba7e-8244c5e3f196'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='169cf98d-5e5b-4642-ba7e-8244c5e3f196'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=ga-payment-failure:ba387108-0415-437d-93dc-0e02549e1158; originalDatabase=ticketing_it_294ca43b5a284ba38f20c5cc8edb7472
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='ba387108-0415-437d-93dc-0e02549e1158'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='ba387108-0415-437d-93dc-0e02549e1158'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='ba387108-0415-437d-93dc-0e02549e1158'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='ba387108-0415-437d-93dc-0e02549e1158'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=late-payment-refund:8a23a36c-6f69-4385-8750-a0b6959f5c35; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='8a23a36c-6f69-4385-8750-a0b6959f5c35'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='8a23a36c-6f69-4385-8750-a0b6959f5c35'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='8a23a36c-6f69-4385-8750-a0b6959f5c35'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='8a23a36c-6f69-4385-8750-a0b6959f5c35'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=late-success-seat-reallocated:30ca6892-14a8-4e1e-8933-773b364017c1; originalDatabase=ticketing_it_294ca43b5a284ba38f20c5cc8edb7472
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='30ca6892-14a8-4e1e-8933-773b364017c1'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='30ca6892-14a8-4e1e-8933-773b364017c1'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='30ca6892-14a8-4e1e-8933-773b364017c1'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='30ca6892-14a8-4e1e-8933-773b364017c1'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=poison-dlq:b957b686-0432-4fa9-9983-d7fd26938747; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='b957b686-0432-4fa9-9983-d7fd26938747'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='b957b686-0432-4fa9-9983-d7fd26938747'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='b957b686-0432-4fa9-9983-d7fd26938747'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='b957b686-0432-4fa9-9983-d7fd26938747'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=replay-noop:6acc812b-78f9-4a88-a937-00ecb485ebd9; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='6acc812b-78f9-4a88-a937-00ecb485ebd9'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='6acc812b-78f9-4a88-a937-00ecb485ebd9'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='6acc812b-78f9-4a88-a937-00ecb485ebd9'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='6acc812b-78f9-4a88-a937-00ecb485ebd9'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=replay-security-budget:0187b16e-3a6e-43b7-ba69-192335d3581e; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='0187b16e-3a6e-43b7-ba69-192335d3581e'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='0187b16e-3a6e-43b7-ba69-192335d3581e'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='0187b16e-3a6e-43b7-ba69-192335d3581e'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='0187b16e-3a6e-43b7-ba69-192335d3581e'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=scheduled-payment:5507503c-d3ba-4b99-8d90-76cdf1f1acde; originalDatabase=ticketing_it_294ca43b5a284ba38f20c5cc8edb7472
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='5507503c-d3ba-4b99-8d90-76cdf1f1acde'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='5507503c-d3ba-4b99-8d90-76cdf1f1acde'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='5507503c-d3ba-4b99-8d90-76cdf1f1acde'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='5507503c-d3ba-4b99-8d90-76cdf1f1acde'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=seat-payment-failure:30db5471-f15b-468e-8f22-a85c0952e03e; originalDatabase=ticketing_it_294ca43b5a284ba38f20c5cc8edb7472
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='30db5471-f15b-468e-8f22-a85c0952e03e'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='30db5471-f15b-468e-8f22-a85c0952e03e'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='30db5471-f15b-468e-8f22-a85c0952e03e'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='30db5471-f15b-468e-8f22-a85c0952e03e'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;


-- queryId=timely-payment-broker-down:7ebc0333-4e1e-4f1b-9f9f-c04389fcaa9a; originalDatabase=ticketing_it_a0a13a9beec14f0a9ba0c4862e7f741d
-- Read-only snapshot; original DB exists only during the isolated harness.
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id='7ebc0333-4e1e-4f1b-9f9f-c04389fcaa9a'::uuid),
i AS (SELECT * FROM ticketing.booking_items WHERE event_id='7ebc0333-4e1e-4f1b-9f9f-c04389fcaa9a'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id='7ebc0333-4e1e-4f1b-9f9f-c04389fcaa9a'::uuid),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types t ON t.id=g.ticket_type_id WHERE t.event_id='7ebc0333-4e1e-4f1b-9f9f-c04389fcaa9a'::uuid),
o AS (SELECT * FROM ticketing.outbox_events WHERE aggregate_id IN (SELECT id FROM b)),
p AS (SELECT * FROM ticketing.payments WHERE booking_id IN (SELECT id FROM b)),
c AS (SELECT * FROM ticketing.payment_compensations WHERE booking_id IN (SELECT id FROM b))
SELECT jsonb_build_object(
  'dbTime',statement_timestamp(),
  'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'version',aggregate_version,'expiresAt',expires_at,'expiredAt',expired_at) ORDER BY id) FROM b),'[]'::jsonb),
  'ga',coalesce((SELECT jsonb_agg(to_jsonb(g) ORDER BY ticket_type_id) FROM g),'[]'::jsonb),
  'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'owner',held_by_booking_id,'holdExpiresAt',hold_expires_at) ORDER BY id) FROM s),'[]'::jsonb),
  'payments',coalesce((SELECT jsonb_agg(jsonb_build_object('id',p.id,'booking',p.booking_id,'status',p.status,'recordedAt',p.recorded_at,'processedAt',p.processed_at,'timely',p.recorded_at<=b.expires_at) ORDER BY p.id) FROM p JOIN b ON b.id=p.booking_id),'[]'::jsonb),
  'tickets',(SELECT count(*) FROM ticketing.tickets WHERE booking_id IN (SELECT id FROM b)),
  'history',coalesce((SELECT jsonb_agg(to_jsonb(h) ORDER BY booking_id,to_status) FROM (SELECT booking_id,to_status,count(*) AS count FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status) h),'[]'::jsonb),
  'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replays',jsonb_array_length(replay_requests)) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
  'inbox',coalesce((SELECT jsonb_agg(jsonb_build_object('consumer',consumer,'event',event_id,'booking',aggregate_id,'version',aggregate_version) ORDER BY consumer,event_id) FROM ticketing.inbox_events WHERE aggregate_id IN (SELECT id FROM b)),'[]'::jsonb),
  'failures',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'consumer',consumer,'event',event_id,'status',status,'attempts',attempts,'dlqAttempts',dlq_attempts,'dlqPublished',dlq_published_at IS NOT NULL) ORDER BY id) FROM ticketing.message_failures WHERE event_id IN (SELECT id FROM o) OR event_id IS NULL),'[]'::jsonb),
  'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'booking',booking_id,'payment',payment_id,'status',status,'attempts',attempt_count) ORDER BY id) FROM c),'[]'::jsonb),
  'refundAttempts',coalesce((SELECT jsonb_agg(jsonb_build_object('compensation',compensation_id,'attempt',attempt,'outcome',outcome) ORDER BY compensation_id,attempt) FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c)),'[]'::jsonb),
  'violations',jsonb_build_object(
    'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
    'gaQuantityMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
    'duplicateActiveSeats',(SELECT count(*) FROM (SELECT seat_id FROM i JOIN b ON b.id=i.booking_id WHERE seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY seat_id HAVING count(*)>1) d),
    'seatOwnerMismatch',(SELECT count(*) FROM i JOIN b ON b.id=i.booking_id JOIN s ON s.id=i.seat_id WHERE b.status IN ('PENDING_PAYMENT','CONFIRMED') AND (s.held_by_booking_id IS DISTINCT FROM b.id OR s.status IS DISTINCT FROM CASE WHEN b.status='CONFIRMED' THEN 'SOLD' ELSE 'HELD' END)),
    'orphanHold',(SELECT count(*) FROM s WHERE status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status IN ('PENDING_PAYMENT','CONFIRMED'))),
    'invalidTickets',(SELECT count(*) FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id JOIN b ON b.id=i.booking_id WHERE b.status<>'CONFIRMED' OR t.booking_id<>b.id OR t.ticket_ordinal>i.quantity),
    'duplicateSeatTickets',(SELECT count(*) FROM (SELECT seat_id FROM ticketing.tickets t JOIN i ON i.id=t.booking_item_id WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) d),
    'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,to_status FROM ticketing.booking_status_history WHERE booking_id IN (SELECT id FROM b) GROUP BY booking_id,to_status HAVING count(*)>1) d),
    'duplicateBusinessEvents',(SELECT count(*) FROM (SELECT aggregate_id,type FROM o WHERE type<>'PaymentResultRecorded' GROUP BY aggregate_id,type HAVING count(*)>1) d),
    'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) d),
    'duplicateRefundCompletion',(SELECT count(*) FROM (SELECT compensation_id FROM ticketing.compensation_attempts WHERE compensation_id IN (SELECT id FROM c) AND outcome='COMPLETED' GROUP BY compensation_id HAVING count(*)>1) d)
  ))::text;
