ALTER TABLE ticketing.payments ADD COLUMN correlation_id uuid NOT NULL DEFAULT gen_random_uuid();
CREATE INDEX payments_unprocessed_idx ON ticketing.payments(recorded_at,id)
 WHERE recorded_at IS NOT NULL AND processed_at IS NULL;
CREATE TABLE ticketing.tickets (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL REFERENCES ticketing.bookings(id),
 booking_item_id uuid NOT NULL, ticket_ordinal integer NOT NULL CHECK(ticket_ordinal BETWEEN 1 AND 6),
 code varchar(128) NOT NULL UNIQUE, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(booking_item_id,booking_id) REFERENCES ticketing.booking_items(id,booking_id),
 UNIQUE(booking_item_id,ticket_ordinal)
);
CREATE INDEX tickets_booking_idx ON ticketing.tickets(booking_id);
CREATE TABLE ticketing.payment_compensations (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL REFERENCES ticketing.bookings(id),
 payment_id uuid NOT NULL UNIQUE, status varchar(16) NOT NULL DEFAULT 'PENDING'
 CHECK(status IN ('PENDING','COMPLETED','FAILED')), reason varchar(500) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), processed_at timestamptz,
 attempt_count integer NOT NULL DEFAULT 0 CHECK(attempt_count>=0),
 FOREIGN KEY(payment_id,booking_id) REFERENCES ticketing.payments(id,booking_id),
 CHECK(status<>'COMPLETED' OR processed_at IS NOT NULL)
);
CREATE INDEX compensations_booking_idx ON ticketing.payment_compensations(booking_id);
CREATE INDEX compensations_pending_idx ON ticketing.payment_compensations(status,created_at,id)
 WHERE status IN ('PENDING','FAILED');
