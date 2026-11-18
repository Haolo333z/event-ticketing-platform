-- V18: AI Assistant conversations & messages (Hexagonal AI Copilot & Concierge with Anti-IDOR user scoping)

CREATE TABLE IF NOT EXISTS ticketing.ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES ticketing.users(id) ON DELETE CASCADE,
    persona VARCHAR(32) NOT NULL CHECK (persona IN ('CUSTOMER_CONCIERGE', 'ORGANIZER_COPILOT', 'ADMIN_OPS_DIAGNOSTICS')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS ticketing.ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ticketing.ai_conversations(id) ON DELETE CASCADE,
    sender VARCHAR(16) NOT NULL CHECK (sender IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    adapter_used VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FALLBACK',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_ai_conv_user ON ticketing.ai_conversations(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_msg_conv ON ticketing.ai_messages(conversation_id, created_at ASC);
