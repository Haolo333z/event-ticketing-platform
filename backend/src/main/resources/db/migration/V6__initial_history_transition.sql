-- SQL CHECK can accept UNKNOWN; an initial history entry must explicitly be pending.
ALTER TABLE ticketing.booking_status_history ADD CONSTRAINT initial_booking_history_pending
 CHECK(from_status IS NOT NULL OR to_status='PENDING_PAYMENT');
