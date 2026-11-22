-- V21: Soát vé Offline-First & Chống Bot / Đầu cơ giờ mở bán

-- 1. Bổ sung các cột hỗ trợ Offline Sync vào bảng ticket_check_ins
ALTER TABLE ticketing.ticket_check_ins
    ADD COLUMN IF NOT EXISTS is_offline BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS synced_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS client_checked_in_at TIMESTAMPTZ;

-- 2. Bổ sung cờ bảo vệ chống bot giờ cao điểm vào events
ALTER TABLE ticketing.events
    ADD COLUMN IF NOT EXISTS anti_bot_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Bảng anti_bot_challenges
CREATE TABLE ticketing.anti_bot_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_token VARCHAR(128) NOT NULL UNIQUE,
    client_ip VARCHAR(64) NOT NULL,
    account_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    event_id UUID REFERENCES ticketing.events(id) ON DELETE CASCADE,
    question_text VARCHAR(255) NOT NULL,
    solution_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SOLVED', 'EXPIRED', 'FAILED')),
    admission_token VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    solved_at TIMESTAMPTZ
);

CREATE INDEX idx_anti_bot_token ON ticketing.anti_bot_challenges(challenge_token);
CREATE INDEX idx_anti_bot_admission ON ticketing.anti_bot_challenges(admission_token) WHERE admission_token IS NOT NULL;
CREATE INDEX idx_anti_bot_ip_created ON ticketing.anti_bot_challenges(client_ip, created_at);

-- 4. Bảng rate_limit_audits
CREATE TABLE ticketing.rate_limit_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_ip VARCHAR(64) NOT NULL,
    account_id UUID REFERENCES ticketing.users(id) ON DELETE SET NULL,
    endpoint VARCHAR(128) NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_until TIMESTAMPTZ
);

CREATE INDEX idx_rate_limit_ip_window ON ticketing.rate_limit_audits(client_ip, endpoint, window_started_at);
