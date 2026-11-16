-- Flyway V11: Four-role RBAC, external identities and secure token family session store

-- 1. Create standardized roles table
CREATE TABLE IF NOT EXISTS ticketing.roles (
    id varchar(32) PRIMARY KEY,
    name varchar(64) NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Seed four fundamental roles
INSERT INTO ticketing.roles (id, name, description)
VALUES 
    ('CUSTOMER', 'Khách hàng', 'Đặt vé, thanh toán và quản lý vé cá nhân'),
    ('STAFF', 'Nhân viên soát vé', 'Soát vé tại cổng sự kiện được phân công'),
    ('ORGANIZER', 'Nhà tổ chức sự kiện', 'Quản lý sự kiện, vé và nhân viên'),
    ('ADMIN', 'Quản trị viên toàn hệ thống', 'Toàn quyền cấu hình, đối soát và kiểm toán')
ON CONFLICT (id) DO NOTHING;

-- 2. Create user_roles junction table (supporting multi-role accounts)
CREATE TABLE IF NOT EXISTS ticketing.user_roles (
    user_id uuid NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    role_id varchar(32) NOT NULL REFERENCES ticketing.roles(id) ON DELETE RESTRICT,
    granted_by uuid REFERENCES ticketing.users(id) ON DELETE SET NULL,
    granted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role ON ticketing.user_roles(role_id);

-- Backfill user_roles from existing users table
INSERT INTO ticketing.user_roles (user_id, role_id, granted_at)
SELECT id, COALESCE(demo_role, 'CUSTOMER'), clock_timestamp()
FROM ticketing.users
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 3. Loosen demo_role constraint on users table to accept STAFF and ORGANIZER
ALTER TABLE ticketing.users DROP CONSTRAINT IF EXISTS users_demo_role_check;
ALTER TABLE ticketing.users ADD CONSTRAINT users_demo_role_check 
    CHECK (demo_role IN ('CUSTOMER', 'ADMIN', 'STAFF', 'ORGANIZER'));

-- 4. Create external_identities table (for Google OIDC integration)
CREATE TABLE IF NOT EXISTS ticketing.external_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    provider_subject varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    display_name varchar(255),
    picture_url text,
    linked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    last_login_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_external_identity_provider_sub UNIQUE (provider, provider_subject),
    CONSTRAINT uq_external_identity_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_external_identities_sub ON ticketing.external_identities(provider, provider_subject);

-- 5. Create refresh_tokens table (Token Family & Reuse Detection)
CREATE TABLE IF NOT EXISTS ticketing.refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    family_id uuid NOT NULL,
    is_revoked boolean NOT NULL DEFAULT false,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    replaced_by uuid REFERENCES ticketing.refresh_tokens(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON ticketing.refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON ticketing.refresh_tokens(user_id);

-- 6. Create password_reset_tokens table
CREATE TABLE IF NOT EXISTS ticketing.password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user ON ticketing.password_reset_tokens(user_id);
