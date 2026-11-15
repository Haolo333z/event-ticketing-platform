-- Flyway V10: Real user authentication and contact leads persistence

ALTER TABLE ticketing.users
  ADD COLUMN IF NOT EXISTS email varchar(150) UNIQUE,
  ADD COLUMN IF NOT EXISTS password_hash varchar(255),
  ADD COLUMN IF NOT EXISTS phone varchar(20);

UPDATE ticketing.users
SET email = 'customer@eventflow.vn',
    password_hash = '$2a$10$zEit4X9NWrp.3iBgG.hTpOb5Um7dVsVGqvWGrJ4G3UlKpEckrYy8i',
    phone = '0901234567'
WHERE id = '10000000-0000-4000-8000-000000000001';

UPDATE ticketing.users
SET email = 'admin@eventflow.vn',
    password_hash = '$2a$10$chuude7VP4cqC0fTaHNuVOCZH7xKZe3EMGnT3STT6p5OXCw2VGIb2',
    phone = '0909999999'
WHERE id = '10000000-0000-4000-8000-000000000002';

CREATE TABLE IF NOT EXISTS ticketing.contact_leads (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name varchar(100) NOT NULL,
    email varchar(150) NOT NULL,
    phone varchar(20) NOT NULL,
    company_name varchar(200),
    event_scale varchar(50),
    message text,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS contact_leads_created_idx ON ticketing.contact_leads(created_at DESC);
