-- CHECK treats SQL NULL as passing, so explicitly reject a JSON null bookingId.
ALTER TABLE ticketing.outbox_events ADD CONSTRAINT outbox_booking_id_not_null
 CHECK ((payload->>'bookingId') IS NOT NULL);
