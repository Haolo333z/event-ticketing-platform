CREATE TABLE ticketing.payments (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL REFERENCES ticketing.bookings(id), request_id uuid NOT NULL,
 scenario varchar(32) NOT NULL CHECK(scenario IN ('SUCCESS','FAILURE','DELAY_TIMEOUT')),
 status varchar(16) NOT NULL CHECK(status IN ('SCHEDULED','SUCCESS','FAILURE')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), result_due_at timestamptz, recorded_at timestamptz, processed_at timestamptz,
 UNIQUE(booking_id,request_id), UNIQUE(id,booking_id),
 CHECK((status='SCHEDULED' AND recorded_at IS NULL AND result_due_at IS NOT NULL)
 OR(status IN ('SUCCESS','FAILURE') AND recorded_at IS NOT NULL))
);
CREATE INDEX payments_recorded_idx ON ticketing.payments(booking_id,recorded_at);
CREATE INDEX payments_due_idx ON ticketing.payments(result_due_at,id) WHERE status='SCHEDULED';
CREATE TABLE ticketing.outbox_events (
 id uuid PRIMARY KEY, aggregate_id uuid NOT NULL REFERENCES ticketing.bookings(id), aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 type varchar(64) NOT NULL CHECK(type IN ('BookingHeld','PaymentResultRecorded','BookingConfirmed','BookingPaymentFailed','BookingCancelled','BookingExpired','BookingRefundRequired')),
 schema_version integer NOT NULL CHECK(schema_version=1), payload jsonb NOT NULL,
 correlation_id uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED','FAILED')),
 published_at timestamptz, attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), next_attempt_at timestamptz, last_error varchar(500),
 replay_requests jsonb NOT NULL DEFAULT '[]'::jsonb,
 UNIQUE(aggregate_id,aggregate_version),
 CHECK(jsonb_typeof(payload)='object' AND payload ? 'bookingId' AND payload->>'bookingId'=aggregate_id::text),
 CHECK(jsonb_typeof(replay_requests)='array' AND jsonb_array_length(replay_requests)<=3)
);
CREATE INDEX outbox_pending_idx ON ticketing.outbox_events(status,next_attempt_at,created_at,id) WHERE status IN ('PENDING','FAILED');
