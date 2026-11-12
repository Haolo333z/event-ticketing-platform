CREATE TABLE ticketing.users (
 id uuid PRIMARY KEY, demo_role varchar(16) NOT NULL CHECK (demo_role IN ('CUSTOMER','ADMIN')),
 display_name varchar(100) NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE ticketing.events (
 id uuid PRIMARY KEY, title varchar(200) NOT NULL, description varchar(2000) NOT NULL,
 venue varchar(200) NOT NULL, sale_start_at timestamptz NOT NULL, sale_end_at timestamptz NOT NULL,
 starts_at timestamptz NOT NULL, status varchar(16) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','CLOSED')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(sale_start_at < sale_end_at AND sale_end_at <= starts_at)
);
CREATE INDEX events_sale_idx ON ticketing.events(status,sale_start_at);
CREATE INDEX events_list_idx ON ticketing.events(starts_at,id);
CREATE TABLE ticketing.ticket_types (
 id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES ticketing.events(id), name varchar(100) NOT NULL,
 mode varchar(16) NOT NULL CHECK(mode IN ('GA','SEATED')),
 price_vnd bigint NOT NULL CHECK(price_vnd BETWEEN 0 AND 1000000000000), active boolean NOT NULL DEFAULT true,
 UNIQUE(id,event_id)
);
CREATE INDEX ticket_types_event_idx ON ticketing.ticket_types(event_id);
CREATE TABLE ticketing.bookings (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES ticketing.users(id), event_id uuid NOT NULL REFERENCES ticketing.events(id),
 ticket_type_id uuid NOT NULL, payment_intent_id uuid NOT NULL UNIQUE,
 status varchar(32) NOT NULL CHECK(status IN ('PENDING_PAYMENT','CONFIRMED','PAYMENT_FAILED','CANCELLED','EXPIRED','REFUND_REQUIRED')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), expires_at timestamptz NOT NULL, expired_at timestamptz,
 total_amount_vnd bigint NOT NULL CHECK(total_amount_vnd BETWEEN 0 AND 1000000000000),
 aggregate_version bigint NOT NULL CHECK(aggregate_version>=1),
 FOREIGN KEY(ticket_type_id,event_id) REFERENCES ticketing.ticket_types(id,event_id),
 UNIQUE(id,event_id,ticket_type_id), CHECK(expires_at>created_at),
 CHECK(status NOT IN ('EXPIRED','REFUND_REQUIRED') OR expired_at IS NOT NULL)
);
CREATE INDEX bookings_account_idx ON ticketing.bookings(account_id,created_at DESC,id DESC);
CREATE INDEX bookings_event_idx ON ticketing.bookings(event_id);
CREATE INDEX bookings_type_idx ON ticketing.bookings(ticket_type_id);
CREATE INDEX bookings_expiry_idx ON ticketing.bookings(status,expires_at,id) WHERE status='PENDING_PAYMENT';
CREATE TABLE ticketing.seats (
 id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES ticketing.events(id), ticket_type_id uuid NOT NULL,
 label varchar(40) NOT NULL, status varchar(16) NOT NULL DEFAULT 'AVAILABLE', held_by_booking_id uuid REFERENCES ticketing.bookings(id),
 hold_expires_at timestamptz, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 FOREIGN KEY(ticket_type_id,event_id) REFERENCES ticketing.ticket_types(id,event_id),
 UNIQUE(event_id,label), UNIQUE(id,event_id,ticket_type_id),
 CHECK((status='AVAILABLE' AND held_by_booking_id IS NULL AND hold_expires_at IS NULL)
 OR (status='HELD' AND held_by_booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
 OR (status='SOLD' AND held_by_booking_id IS NOT NULL AND hold_expires_at IS NULL))
);
CREATE INDEX seats_availability_idx ON ticketing.seats(event_id,ticket_type_id,status,id);
CREATE INDEX seats_type_idx ON ticketing.seats(ticket_type_id);
CREATE INDEX seats_owner_idx ON ticketing.seats(held_by_booking_id);
CREATE INDEX seats_expiry_idx ON ticketing.seats(hold_expires_at) WHERE status='HELD';
CREATE TABLE ticketing.inventory_ga (
 ticket_type_id uuid PRIMARY KEY REFERENCES ticketing.ticket_types(id),
 capacity integer NOT NULL CHECK(capacity BETWEEN 0 AND 1000000),
 held_count integer NOT NULL DEFAULT 0 CHECK(held_count>=0), sold_count integer NOT NULL DEFAULT 0 CHECK(sold_count>=0),
 CHECK(held_count+sold_count<=capacity)
);
CREATE TABLE ticketing.booking_items (
 id uuid PRIMARY KEY, booking_id uuid NOT NULL, event_id uuid NOT NULL, ticket_type_id uuid NOT NULL,
 seat_id uuid, quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 6),
 unit_price_vnd bigint NOT NULL CHECK(unit_price_vnd BETWEEN 0 AND 1000000000000),
 total_price_vnd bigint NOT NULL CHECK(total_price_vnd BETWEEN 0 AND 1000000000000),
 FOREIGN KEY(booking_id,event_id,ticket_type_id) REFERENCES ticketing.bookings(id,event_id,ticket_type_id),
 FOREIGN KEY(ticket_type_id,event_id) REFERENCES ticketing.ticket_types(id,event_id),
 FOREIGN KEY(seat_id,event_id,ticket_type_id) REFERENCES ticketing.seats(id,event_id,ticket_type_id),
 UNIQUE(booking_id,seat_id), UNIQUE(id,booking_id),
 CHECK(total_price_vnd=quantity*unit_price_vnd), CHECK(seat_id IS NULL OR quantity=1)
);
CREATE UNIQUE INDEX booking_items_ga_idx ON ticketing.booking_items(booking_id) WHERE seat_id IS NULL;
CREATE INDEX booking_items_type_idx ON ticketing.booking_items(ticket_type_id);
CREATE INDEX booking_items_seat_idx ON ticketing.booking_items(seat_id);
