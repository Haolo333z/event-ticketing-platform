-- V27: User profile avatar, active sessions tracking, and default VAT invoice details

-- 1. Bổ sung trường avatar_url và thông tin xuất hóa đơn VAT mặc định vào ticketing.users
ALTER TABLE ticketing.users
    ADD COLUMN IF NOT EXISTS avatar_url TEXT,
    ADD COLUMN IF NOT EXISTS vat_company_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS vat_tax_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS vat_company_address TEXT,
    ADD COLUMN IF NOT EXISTS vat_recipient_email VARCHAR(255);

-- 2. Bổ sung theo dõi thiết bị & địa chỉ IP vào bảng ticketing.refresh_tokens (Token Families)
ALTER TABLE ticketing.refresh_tokens
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON ticketing.refresh_tokens(user_id, is_revoked, expires_at);
