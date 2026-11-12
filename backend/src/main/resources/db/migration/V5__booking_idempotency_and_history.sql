ALTER TABLE ticketing.bookings ADD CONSTRAINT bookings_id_account_unique UNIQUE(id,account_id);
CREATE TABLE ticketing.idempotency_keys (
 account_id uuid NOT NULL REFERENCES ticketing.users(id),
 operation varchar(64) NOT NULL CHECK(operation='CREATE_BOOKING'),
 key varchar(128) NOT NULL CHECK(key ~ '^[A-Za-z0-9._:-]{1,128}$'),
 request_hash char(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
 booking_id uuid UNIQUE, response_status integer CHECK(response_status=202), response_body jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(account_id,operation,key),
 FOREIGN KEY(booking_id,account_id) REFERENCES ticketing.bookings(id,account_id),
 CHECK(response_body IS NULL OR jsonb_typeof(response_body)='object')
);
CREATE FUNCTION ticketing.require_completed_idempotency() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE row_final ticketing.idempotency_keys%ROWTYPE;
BEGIN
 SELECT * INTO row_final FROM ticketing.idempotency_keys
 WHERE account_id=NEW.account_id AND operation=NEW.operation AND key=NEW.key;
 IF FOUND AND (row_final.booking_id IS NULL OR row_final.response_status IS NULL OR row_final.response_body IS NULL
   OR (row_final.response_body->>'bookingId') IS DISTINCT FROM row_final.booking_id::text
   OR (row_final.response_body->>'status') IS DISTINCT FROM 'PENDING_PAYMENT') THEN
   RAISE EXCEPTION 'Incomplete idempotency result cannot commit' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER completed_idempotency_at_commit
 AFTER INSERT OR UPDATE ON ticketing.idempotency_keys DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION ticketing.require_completed_idempotency();
CREATE TABLE ticketing.booking_status_history (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL REFERENCES ticketing.bookings(id),
 from_status varchar(32), to_status varchar(32) NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(), cause_event_id uuid,
 CHECK(from_status IS NULL OR from_status IN ('PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED')),
 CHECK(to_status IN ('PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED')),
 CHECK((from_status IS NULL AND to_status='PENDING_PAYMENT')
  OR(from_status='PENDING_PAYMENT' AND to_status IN ('CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED'))
  OR(from_status='EXPIRED' AND to_status='REFUND_REQUIRED'))
);
CREATE INDEX booking_history_idx ON ticketing.booking_status_history(booking_id,occurred_at,id);
