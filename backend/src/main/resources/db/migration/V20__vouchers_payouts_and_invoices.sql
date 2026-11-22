-- Flyway V20: Tu phuc vu B2B, Ma giam gia ACID (vouchers), Hoa don VAT dien tu (e_invoices) & Cong rut doanh thu (organizer_payouts)
-- (B2B Self-Service, ACID Vouchers, VAT E-Invoices & Organizer Payouts)

-- 1. Bang quan ly ma giam gia (Vouchers)
CREATE TABLE IF NOT EXISTS ticketing.vouchers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID REFERENCES ticketing.organizer_profiles(id) ON DELETE CASCADE,
    event_id UUID REFERENCES ticketing.events(id) ON DELETE CASCADE,
    ticket_type_id UUID REFERENCES ticketing.ticket_types(id) ON DELETE SET NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    discount_type VARCHAR(16) NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_VND')),
    discount_value BIGINT NOT NULL CHECK (discount_value > 0),
    min_order_amount_vnd BIGINT NOT NULL DEFAULT 0,
    max_discount_amount_vnd BIGINT,
    max_uses INTEGER NOT NULL CHECK (max_uses > 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= max_uses),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (valid_from < valid_to)
);

CREATE INDEX IF NOT EXISTS idx_vouchers_code ON ticketing.vouchers(code);
CREATE INDEX IF NOT EXISTS idx_vouchers_organizer ON ticketing.vouchers(organizer_id);
CREATE INDEX IF NOT EXISTS idx_vouchers_event ON ticketing.vouchers(event_id);

-- 2. Bang lich su su dung ma giam gia (Voucher Redemptions) - Chong dung trung
CREATE TABLE IF NOT EXISTS ticketing.voucher_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id UUID NOT NULL REFERENCES ticketing.vouchers(id) ON DELETE RESTRICT,
    booking_id UUID NOT NULL REFERENCES ticketing.bookings(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT,
    discount_amount_vnd BIGINT NOT NULL CHECK (discount_amount_vnd >= 0),
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_voucher_account UNIQUE(voucher_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_booking ON ticketing.voucher_redemptions(booking_id);
CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_voucher ON ticketing.voucher_redemptions(voucher_id);

-- 3. Bang yeu cau rut doanh thu cua Nha to chuc (Organizer Payouts)
CREATE TABLE IF NOT EXISTS ticketing.organizer_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL REFERENCES ticketing.organizer_profiles(id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE RESTRICT,
    gross_revenue_vnd BIGINT NOT NULL CHECK (gross_revenue_vnd >= 0),
    platform_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 5.00,
    platform_fee_vnd BIGINT NOT NULL CHECK (platform_fee_vnd >= 0),
    net_payout_vnd BIGINT NOT NULL CHECK (net_payout_vnd >= 0),
    bank_name VARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50) NOT NULL,
    bank_account_holder VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'APPROVED', 'PAID', 'REJECTED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    processed_at TIMESTAMPTZ,
    admin_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_organizer_payouts_org ON ticketing.organizer_payouts(organizer_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_organizer_payouts_status ON ticketing.organizer_payouts(status);
CREATE INDEX IF NOT EXISTS idx_organizer_payouts_event ON ticketing.organizer_payouts(event_id);

-- 4. Bang hoa don VAT dien tu (E-Invoices)
CREATE TABLE IF NOT EXISTS ticketing.e_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES ticketing.bookings(id) ON DELETE RESTRICT UNIQUE,
    account_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT,
    tax_code VARCHAR(32) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    company_address TEXT NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    invoice_lookup_code VARCHAR(64) NOT NULL UNIQUE,
    total_amount_vnd BIGINT NOT NULL,
    vat_rate NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    vat_amount_vnd BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('PENDING', 'ISSUED', 'CANCELLED')),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_e_invoices_account ON ticketing.e_invoices(account_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_e_invoices_lookup ON ticketing.e_invoices(invoice_lookup_code);

-- 5. Bo sung cot voucher_id & discount_amount_vnd vao ticketing.bookings
ALTER TABLE ticketing.bookings 
    ADD COLUMN IF NOT EXISTS voucher_id UUID REFERENCES ticketing.vouchers(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS discount_amount_vnd BIGINT NOT NULL DEFAULT 0;

-- 6. Bo sung goi SaaS (saas_plan) vao ticketing.organizer_profiles
ALTER TABLE ticketing.organizer_profiles 
    ADD COLUMN IF NOT EXISTS saas_plan VARCHAR(32) NOT NULL DEFAULT 'PROFESSIONAL' CHECK (saas_plan IN ('STARTER', 'PROFESSIONAL', 'ENTERPRISE'));

-- 7. Bo sung saas_plan vao ticketing.contact_leads cho luong tu phuc vu B2B
ALTER TABLE ticketing.contact_leads 
    ADD COLUMN IF NOT EXISTS saas_plan VARCHAR(32) DEFAULT 'PROFESSIONAL';

-- 8. Seed 2 ma giam gia mau cho moi truong test/demo
INSERT INTO ticketing.vouchers (
    id, code, discount_type, discount_value, min_order_amount_vnd, max_discount_amount_vnd,
    max_uses, used_count, valid_from, valid_to, is_active
) VALUES 
(
    '60000000-0000-4000-8000-000000000001',
    'SUMMER2026',
    'PERCENTAGE',
    10,
    100000,
    100000,
    1000,
    0,
    clock_timestamp() - interval '1 day',
    clock_timestamp() + interval '1 year',
    true
),
(
    '60000000-0000-4000-8000-000000000002',
    'WELCOME50K',
    'FIXED_VND',
    50000,
    150000,
    50000,
    500,
    0,
    clock_timestamp() - interval '1 day',
    clock_timestamp() + interval '1 year',
    true
)
ON CONFLICT (code) DO NOTHING;
