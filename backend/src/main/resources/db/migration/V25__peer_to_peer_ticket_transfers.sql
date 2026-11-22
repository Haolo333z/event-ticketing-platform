-- V25: Peer-to-Peer Ticket Transfers (Gửi tặng vé cho bạn bè an toàn & nguyên tử ACID)

-- 1. Bổ sung trạng thái & người sở hữu vé vào ticketing.tickets
ALTER TABLE ticketing.tickets
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'VALID'
        CHECK (status IN ('VALID', 'REVOKED', 'TRANSFERRED', 'USED')),
    ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES ticketing.users(id),
    ADD COLUMN IF NOT EXISTS transferred_from_ticket_id UUID REFERENCES ticketing.tickets(id),
    ADD COLUMN IF NOT EXISTS transferred_at TIMESTAMPTZ;

-- Backfill owner_user_id từ bảng bookings
UPDATE ticketing.tickets t
SET owner_user_id = b.account_id
FROM ticketing.bookings b
WHERE t.booking_id = b.id AND t.owner_user_id IS NULL;

-- 2. Bảng quản lý giao dịch gửi tặng vé (ticket_transfers)
CREATE TABLE IF NOT EXISTS ticketing.ticket_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    from_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    to_email VARCHAR(255) NOT NULL,
    to_user_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    transfer_token VARCHAR(255) NOT NULL UNIQUE,
    note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp() + INTERVAL '48 hours'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    accepted_at TIMESTAMPTZ,
    new_ticket_id UUID REFERENCES ticketing.tickets(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_ticket_transfers_token ON ticketing.ticket_transfers(transfer_token);
CREATE INDEX IF NOT EXISTS idx_ticket_transfers_from_user ON ticketing.ticket_transfers(from_user_id);
CREATE INDEX IF NOT EXISTS idx_ticket_transfers_to_email ON ticketing.ticket_transfers(to_email);
CREATE INDEX IF NOT EXISTS idx_tickets_owner ON ticketing.tickets(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON ticketing.tickets(status);
