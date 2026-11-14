-- Read-only; IDs must belong to the rehearsal fixtures, never the original demo.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='5s';
WITH b AS (SELECT * FROM ticketing.bookings WHERE event_id IN (:'ga_event'::uuid,:'seat_event'::uuid)),
i AS (SELECT i.* FROM ticketing.booking_items i JOIN b ON b.id=i.booking_id),
t AS (SELECT t.* FROM ticketing.tickets t JOIN b ON b.id=t.booking_id),
p AS (SELECT p.* FROM ticketing.payments p JOIN b ON b.id=p.booking_id),
h AS (SELECT h.* FROM ticketing.booking_status_history h JOIN b ON b.id=h.booking_id),
o AS (SELECT o.* FROM ticketing.outbox_events o JOIN b ON b.id=o.aggregate_id),
k AS (SELECT k.* FROM ticketing.idempotency_keys k JOIN b ON b.id=k.booking_id),
g AS (SELECT g.* FROM ticketing.inventory_ga g JOIN ticketing.ticket_types tt ON tt.id=g.ticket_type_id WHERE tt.event_id=:'ga_event'::uuid),
s AS (SELECT * FROM ticketing.seats WHERE event_id=:'seat_event'::uuid),
c AS (SELECT c.* FROM ticketing.payment_compensations c JOIN b ON b.id=c.booking_id),
ca AS (SELECT ca.* FROM ticketing.compensation_attempts ca JOIN c ON c.id=ca.compensation_id),
ib AS (SELECT ib.* FROM ticketing.inbox_events ib JOIN b ON b.id=ib.aggregate_id),
pe AS (SELECT pe.* FROM ticketing.projection_events pe JOIN b ON b.id=pe.aggregate_id),
ep AS (SELECT ep.* FROM ticketing.event_projections ep JOIN b ON b.id=ep.aggregate_id),
business_rows AS (
 SELECT 'bookings' AS tab,to_jsonb(b) AS row FROM b UNION ALL SELECT 'items',to_jsonb(i) FROM i
 UNION ALL SELECT 'tickets',to_jsonb(t) FROM t UNION ALL SELECT 'payments',to_jsonb(p) FROM p
 UNION ALL SELECT 'history',to_jsonb(h) FROM h UNION ALL SELECT 'keys',to_jsonb(k) FROM k
 UNION ALL SELECT 'ga',to_jsonb(g) FROM g UNION ALL SELECT 'seats',to_jsonb(s) FROM s
 UNION ALL SELECT 'compensations',to_jsonb(c) FROM c UNION ALL SELECT 'compensation_attempts',to_jsonb(ca) FROM ca
 UNION ALL SELECT 'inbox',to_jsonb(ib) FROM ib UNION ALL SELECT 'projection_events',to_jsonb(pe) FROM pe
 UNION ALL SELECT 'projections',to_jsonb(ep) FROM ep
 -- Operational publisher status/attempt/replay audit can change; immutable event contents cannot.
 UNION ALL SELECT 'events',jsonb_build_object('id',id,'aggregate',aggregate_id,'version',aggregate_version,
   'type',type,'schema',schema_version,'payload',payload,'correlation',correlation_id,'created',created_at) FROM o
)
SELECT jsonb_build_object(
 'database',current_database(),'at',clock_timestamp(),
 'counts',jsonb_build_object('bookings',(SELECT count(*) FROM b),'items',(SELECT count(*) FROM i),
  'tickets',(SELECT count(*) FROM t),'payments',(SELECT count(*) FROM p),'history',(SELECT count(*) FROM h),
  'keys',(SELECT count(*) FROM k),'outbox',(SELECT count(*) FROM o),'inbox',(SELECT count(*) FROM ib),
  'projections',(SELECT count(*) FROM ep),'projectionEvents',(SELECT count(*) FROM pe),
  'compensations',(SELECT count(*) FROM c),'compensationAttempts',(SELECT count(*) FROM ca)),
 'businessSha256',(SELECT encode(sha256(convert_to(coalesce(jsonb_agg(jsonb_build_array(tab,row) ORDER BY tab,row::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM business_rows),
 'bookings',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'status',status,'expiresAt',expires_at,'expiredAt',expired_at,'version',aggregate_version) ORDER BY id) FROM b),'[]'::jsonb),
 'tickets',coalesce((SELECT jsonb_agg(jsonb_build_object('id',t.id,'bookingId',t.booking_id,'ordinal',t.ticket_ordinal,'seatLabel',s.label) ORDER BY t.id) FROM t JOIN i ON i.id=t.booking_item_id LEFT JOIN s ON s.id=i.seat_id),'[]'::jsonb),
 'ga',coalesce((SELECT jsonb_agg(jsonb_build_object('capacity',capacity,'held',held_count,'sold',sold_count,'available',capacity-held_count-sold_count)) FROM g),'[]'::jsonb),
 'seats',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'label',label,'status',status,'owner',held_by_booking_id) ORDER BY label) FROM s),'[]'::jsonb),
 'outbox',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'bookingId',aggregate_id,'version',aggregate_version,'type',type,'status',status,'attempts',attempts,'replayRequests',replay_requests) ORDER BY aggregate_id,aggregate_version) FROM o),'[]'::jsonb),
 'compensations',coalesce((SELECT jsonb_agg(jsonb_build_object('bookingId',booking_id,'status',status,'attempts',attempt_count)) FROM c),'[]'::jsonb),
 'lateRecordedAfterDeadline',(SELECT coalesce(bool_and(p.recorded_at>b.expires_at),false) FROM p JOIN b ON b.id=p.booking_id WHERE b.status='REFUND_REQUIRED'),
 'violations',jsonb_build_object(
  'oversell',(SELECT count(*) FROM g WHERE held_count<0 OR sold_count<0 OR held_count+sold_count>capacity),
  'gaAllocationMismatch',(SELECT count(*) FROM g WHERE held_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='PENDING_PAYMENT') OR sold_count<>(SELECT coalesce(sum(i.quantity),0) FROM i JOIN b ON b.id=i.booking_id WHERE i.ticket_type_id=g.ticket_type_id AND b.status='CONFIRMED')),
  'duplicateActiveSeat',(SELECT count(*) FROM (SELECT i.seat_id FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id IS NOT NULL AND b.status IN ('PENDING_PAYMENT','CONFIRMED') GROUP BY i.seat_id HAVING count(*)>1) x),
  'seatOwnerMismatch',(SELECT count(*) FROM s WHERE (status='AVAILABLE' AND held_by_booking_id IS NOT NULL) OR (status IN ('HELD','SOLD') AND NOT EXISTS(SELECT 1 FROM i JOIN b ON b.id=i.booking_id WHERE i.seat_id=s.id AND b.id=s.held_by_booking_id AND b.status=CASE WHEN s.status='HELD' THEN 'PENDING_PAYMENT' ELSE 'CONFIRMED' END))),
  'ticketMismatch',(SELECT count(*) FROM b WHERE (SELECT count(*) FROM t WHERE t.booking_id=b.id)<>CASE WHEN b.status='CONFIRMED' THEN (SELECT coalesce(sum(quantity),0) FROM i WHERE i.booking_id=b.id) ELSE 0 END),
  'duplicateTicketOrdinal',(SELECT count(*) FROM (SELECT booking_item_id,ticket_ordinal FROM t GROUP BY booking_item_id,ticket_ordinal HAVING count(*)>1) x),
  'duplicateHistory',(SELECT count(*) FROM (SELECT booking_id,from_status,to_status FROM h GROUP BY booking_id,from_status,to_status HAVING count(*)>1) x),
  'partialBooking',(SELECT count(*) FROM b WHERE NOT EXISTS(SELECT 1 FROM i WHERE i.booking_id=b.id) OR NOT EXISTS(SELECT 1 FROM k WHERE k.booking_id=b.id AND response_status=202 AND response_body IS NOT NULL) OR NOT EXISTS(SELECT 1 FROM h WHERE h.booking_id=b.id) OR NOT EXISTS(SELECT 1 FROM o WHERE o.aggregate_id=b.id)),
  'duplicateCompensation',(SELECT count(*) FROM (SELECT payment_id FROM c GROUP BY payment_id HAVING count(*)>1) x),
  'projectionLag',(SELECT count(*) FROM b WHERE NOT EXISTS(SELECT 1 FROM ep WHERE ep.aggregate_id=b.id AND ep.last_applied_version=b.aggregate_version))
 ))::text;
COMMIT;
