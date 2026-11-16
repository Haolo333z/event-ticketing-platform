-- V12__organizer_venues_and_staff_assignments.sql
-- Module Quản trị Nhà tổ chức (Organizer), Địa điểm (Venues), Thể loại (Categories) & Phân công Nhân sự (Staff Assignments)

-- 1. Bảng hồ sơ đối tác Nhà tổ chức sự kiện
CREATE TABLE IF NOT EXISTS ticketing.organizer_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT UNIQUE,
    organization_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    tax_code VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 2. Bổ sung organizer_id vào bảng events
ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS organizer_id UUID REFERENCES ticketing.organizer_profiles(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS events_organizer_idx ON ticketing.events(organizer_id);

-- 3. Bảng venues (Địa điểm tổ chức sự kiện)
CREATE TABLE IF NOT EXISTS ticketing.venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(64) NOT NULL DEFAULT 'Vietnam',
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    seating_layout_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Seed các địa điểm chuẩn hóa ban đầu
INSERT INTO ticketing.venues (id, name, address, city, country, capacity) VALUES
('50000000-0000-4000-8000-000000000001', 'Nhà hát Lớn Hà Nội', 'Số 1 Tràng Tiền, Hoàn Kiếm', 'Hà Nội', 'Vietnam', 600),
('50000000-0000-4000-8000-000000000002', 'Trung tâm Hội nghị Quốc gia', 'Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm', 'Hà Nội', 'Vietnam', 3800),
('50000000-0000-4000-8000-000000000003', 'Nhà hát Thành phố Hồ Chí Minh', 'Số 7 Công Trường Lam Sơn, Bến Nghé, Quận 1', 'TP. Hồ Chí Minh', 'Vietnam', 500),
('50000000-0000-4000-8000-000000000004', 'Sân vận động Quốc gia Mỹ Đình', 'Đường Lê Đức Thọ, Mỹ Đình, Nam Từ Liêm', 'Hà Nội', 'Vietnam', 40000)
ON CONFLICT (id) DO NOTHING;

-- 4. Bảng event_categories (Thể loại sự kiện)
CREATE TABLE IF NOT EXISTS ticketing.event_categories (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon_url TEXT
);

-- Seed 4 thể loại sự kiện ban đầu
INSERT INTO ticketing.event_categories (id, name, description, icon_url) VALUES
('CONCERT', 'Đại nhạc hội & Concert', 'Quy mô 5.000 - 50.000 khán giả mở bán đồng loạt', 'music'),
('THEATER', 'Nhà hát & Nghệ thuật Hàn lâm', 'Phân tầng khán phòng và giữ ghế chỉ định chính xác', 'theater'),
('CONFERENCE', 'Hội nghị Công nghệ & Triển lãm', 'Soát vé mã QR đa cổng, phân luồng khách VIP', 'conference'),
('SPORTS', 'Sự kiện Thể thao & Giải đấu', 'Kiểm soát vé khán đài A, B, C, D tốc độ cao', 'sports')
ON CONFLICT (id) DO NOTHING;

-- 5. Bổ sung category_id và venue_id vào bảng events
ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS category_id VARCHAR(32) REFERENCES ticketing.event_categories(id) ON DELETE SET NULL;
ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS venue_id UUID REFERENCES ticketing.venues(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS events_category_idx ON ticketing.events(category_id);
CREATE INDEX IF NOT EXISTS events_venue_idx ON ticketing.events(venue_id);

-- 6. Bảng event_media (Poster, hero banner, thư viện hình ảnh)
CREATE TABLE IF NOT EXISTS ticketing.event_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE,
    media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('POSTER', 'HERO_BANNER', 'SEAT_MAP', 'GALLERY')),
    file_path TEXT NOT NULL,
    file_url TEXT NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_event_media_event ON ticketing.event_media(event_id, media_type);

-- 7. Bảng event_staff_assignments (Phân công nhân viên soát vé sự kiện)
CREATE TABLE IF NOT EXISTS ticketing.event_staff_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE,
    staff_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    assigned_by UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_event_staff_active ON ticketing.event_staff_assignments(event_id, staff_user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_staff_assigned_lookup ON ticketing.event_staff_assignments(staff_user_id, event_id) WHERE revoked_at IS NULL;
