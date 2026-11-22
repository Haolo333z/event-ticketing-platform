-- V22: AI Ops NÃ¢ng Cao (Dá»± BÃ¡o ChÃ¡y VÃ© & PhÃ¡t Hiá»‡n Cá»¥m Äáº§u CÆ¡ / Scalping Anomaly Detection)

-- 1. Tráº¡ng thÃ¡i tÃ i khoáº£n ngÆ°á»i dÃ¹ng (há»— trá»£ khÃ³a tÃ i khoáº£n Ä‘áº§u cÆ¡ 1 cháº¡m)
ALTER TABLE ticketing.users
  ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
  CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED'));

-- 2. Báº£ng cáº£nh bÃ¡o Ä‘áº§u cÆ¡ / gom vÃ© báº¥t thÆ°á»ng
CREATE TABLE IF NOT EXISTS ticketing.scalping_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type VARCHAR(64) NOT NULL,
    event_id UUID REFERENCES ticketing.events(id) ON DELETE CASCADE,
    target_account_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    client_ip VARCHAR(64),
    risk_level VARCHAR(16) NOT NULL,
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    explanation_vi TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT chk_scalping_alert_type CHECK (alert_type IN ('MASS_HOLD_SAME_IP', 'HOARDING_ABANDON_BURST', 'MULTI_ACCOUNT_SAME_CARD')),
    CONSTRAINT chk_scalping_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_scalping_status CHECK (status IN ('NEW', 'INVESTIGATING', 'DISMISSED', 'ACTION_TAKEN'))
);

CREATE INDEX IF NOT EXISTS idx_scalping_alerts_event ON ticketing.scalping_alerts(event_id);
CREATE INDEX IF NOT EXISTS idx_scalping_alerts_risk ON ticketing.scalping_alerts(risk_level, status);
CREATE INDEX IF NOT EXISTS idx_scalping_alerts_target_account ON ticketing.scalping_alerts(target_account_id);
