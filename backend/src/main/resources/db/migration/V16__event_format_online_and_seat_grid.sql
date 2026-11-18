-- V16: Event format (OFFLINE_SEATED, OFFLINE_GA, ONLINE), Online join URL & structured Seat Grid (row_label, seat_number)

ALTER TABLE ticketing.events
    ADD COLUMN IF NOT EXISTS event_format VARCHAR(32) NOT NULL DEFAULT 'OFFLINE_GA'
        CHECK (event_format IN ('OFFLINE_SEATED', 'OFFLINE_GA', 'ONLINE')),
    ADD COLUMN IF NOT EXISTS online_join_url TEXT;

-- Backfill existing seated events automatically
UPDATE ticketing.events e
SET event_format = 'OFFLINE_SEATED'
WHERE e.event_format = 'OFFLINE_GA'
  AND EXISTS (
      SELECT 1 FROM ticketing.ticket_types t
      WHERE t.event_id = e.id AND t.mode = 'SEATED'
  );

CREATE INDEX IF NOT EXISTS idx_events_format_status ON ticketing.events(event_format, status);

-- Structured seat grid coordinates for Best Available Contiguous Seats algorithm
ALTER TABLE ticketing.seats
    ADD COLUMN IF NOT EXISTS row_label VARCHAR(16),
    ADD COLUMN IF NOT EXISTS seat_number INTEGER;

-- Backfill row_label and seat_number from existing labels (e.g., A1 -> A / 1, VIP-B12 -> VIP-B / 12)
UPDATE ticketing.seats
SET row_label = COALESCE(NULLIF(regexp_replace(label, '[0-9]+$', ''), ''), 'ROW'),
    seat_number = CASE
        WHEN label ~ '[0-9]+$' THEN CAST(substring(label FROM '([0-9]+)$') AS INTEGER)
        ELSE NULL
    END
WHERE row_label IS NULL;

CREATE INDEX IF NOT EXISTS idx_seats_grid ON ticketing.seats(ticket_type_id, row_label, seat_number);
