-- Preserve all earlier migrations. The owner must belong to the same event/type.
ALTER TABLE ticketing.seats ADD CONSTRAINT seats_owner_event_type_fk
 FOREIGN KEY(held_by_booking_id,event_id,ticket_type_id)
 REFERENCES ticketing.bookings(id,event_id,ticket_type_id);
