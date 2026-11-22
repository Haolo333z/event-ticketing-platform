-- V26: Fan-to-Fan Resale Marketplace (Chợ chuyển nhượng vé chính chủ - Chống đầu cơ)

CREATE TABLE IF NOT EXISTS ticketing.resale_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES ticketing.tickets(id) ON DELETE CASCADE,
    seller_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE,
    original_price_vnd BIGINT NOT NULL,
    asking_price_vnd BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'LISTED'
        CHECK (status IN ('LISTED', 'RESERVED', 'SOLD', 'CANCELLED')),
    locked_by_user_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    lock_expires_at TIMESTAMPTZ,
    buyer_user_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    sold_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    -- Bất biến chống phe vé / đầu cơ: Giá bán lại bắt buộc > 0 và <= giá vé gốc!
    CONSTRAINT chk_resale_anti_scalping CHECK (asking_price_vnd > 0 AND asking_price_vnd <= original_price_vnd)
);

-- Chỉ cho phép một vé có tối đa 1 niêm yết đang hoạt động (LISTED hoặc RESERVED)
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_resale_ticket
    ON ticketing.resale_listings(ticket_id)
    WHERE status IN ('LISTED', 'RESERVED');

CREATE INDEX IF NOT EXISTS idx_resale_event_status ON ticketing.resale_listings(event_id, status);
CREATE INDEX IF NOT EXISTS idx_resale_seller ON ticketing.resale_listings(seller_user_id);
CREATE INDEX IF NOT EXISTS idx_resale_buyer ON ticketing.resale_listings(buyer_user_id);
CREATE INDEX IF NOT EXISTS idx_resale_lock_expires ON ticketing.resale_listings(lock_expires_at) WHERE status = 'RESERVED';
