-- V17: Smart Waitlist (event_waitlists) & Virtual Waiting Room configuration

CREATE TABLE IF NOT EXISTS ticketing.event_waitlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE,
    ticket_type_id UUID REFERENCES ticketing.ticket_types(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    desired_quantity INTEGER NOT NULL DEFAULT 1 CHECK (desired_quantity BETWEEN 1 AND 6),
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'NOTIFIED', 'CONVERTED', 'CANCELLED')),
    notified_at TIMESTAMPTZ,
    trigger_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Prevent duplicate active waitlist registration for the same user & event
CREATE UNIQUE INDEX IF NOT EXISTS uq_waitlist_active_user_event
    ON ticketing.event_waitlists(event_id, user_id)
    WHERE status IN ('WAITING', 'NOTIFIED');

CREATE INDEX IF NOT EXISTS idx_waitlist_event_fifo
    ON ticketing.event_waitlists(event_id, status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_waitlist_user
    ON ticketing.event_waitlists(user_id, created_at DESC);
