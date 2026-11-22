-- V23: Google OAuth2 Identities Integration (1-Cháº¡m OAuth2 & Hexagonal Adapters)

CREATE TABLE IF NOT EXISTS ticketing.oauth_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL DEFAULT 'GOOGLE',
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_oauth_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_oauth_identities_user ON ticketing.oauth_identities(user_id);
CREATE INDEX IF NOT EXISTS idx_oauth_identities_email ON ticketing.oauth_identities(email);
