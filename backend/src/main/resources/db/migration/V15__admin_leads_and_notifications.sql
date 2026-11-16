-- V15: Phân hệ Quản trị Admin, Tiếp nhận Leads, Nhật ký Email & Audit Logs

-- 1. Bổ sung các cột trạng thái và ghi chú cho bảng contact_leads
ALTER TABLE ticketing.contact_leads 
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONTACTED', 'CONVERTED', 'CLOSED')),
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

CREATE INDEX IF NOT EXISTS idx_contact_leads_status ON ticketing.contact_leads(status, created_at DESC);

-- Bổ sung cột status cho bảng users
ALTER TABLE ticketing.users 
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED'));

-- 2. Bảng email_notifications (Nhật ký biên nhận hóa đơn điện tử / email outbox log)
CREATE TABLE IF NOT EXISTS ticketing.email_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES ticketing.bookings(id) ON DELETE SET NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    template_type VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'SENT' CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_email_notifications_booking ON ticketing.email_notifications(booking_id);
CREATE INDEX IF NOT EXISTS idx_email_notifications_recipient ON ticketing.email_notifications(recipient_email, sent_at DESC);

-- 3. Bảng audit_logs (Nhật ký kiểm toán thao tác quản trị nhạy cảm)
CREATE TABLE IF NOT EXISTS ticketing.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    payload_diff JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON ticketing.audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target ON ticketing.audit_logs(target_type, target_id);
