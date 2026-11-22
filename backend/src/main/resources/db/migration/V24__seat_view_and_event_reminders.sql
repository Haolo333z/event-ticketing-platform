-- V24: Seat View 3D/SVG Preview & Event Reminders (2026 Customer Experience)

-- 1. Bổ sung các thông số đo lường góc nhìn phối cảnh sân khấu cho ghế
ALTER TABLE ticketing.seats
    ADD COLUMN IF NOT EXISTS distance_meters NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS elevation_angle NUMERIC(4,1) DEFAULT 12.0,
    ADD COLUMN IF NOT EXISTS view_quality_score INTEGER DEFAULT 85,
    ADD COLUMN IF NOT EXISTS view_notes VARCHAR(255);

-- 2. Tự động ước lượng thông số cho các ghế hiện có nếu chưa có giá trị
UPDATE ticketing.seats
SET distance_meters = CASE 
        WHEN row_label = 'A' THEN 10.5
        WHEN row_label = 'B' THEN 12.5
        WHEN row_label = 'C' THEN 14.5
        WHEN row_label = 'D' THEN 16.5
        ELSE 15.0 + COALESCE(seat_number, 1) * 0.2
    END,
    view_quality_score = CASE 
        WHEN row_label = 'A' THEN 96
        WHEN row_label = 'B' THEN 92
        WHEN row_label = 'C' THEN 88
        WHEN row_label = 'D' THEN 82
        ELSE 80
    END,
    view_notes = CASE
        WHEN row_label = 'A' THEN 'Tầm nhìn trực diện sân khấu VIP, cự ly gần nhất'
        WHEN row_label = 'B' THEN 'Góc nhìn trung tâm bao quát toàn cảnh'
        WHEN row_label = 'C' THEN 'Tầm nhìn tiêu chuẩn, âm thanh cân bằng'
        ELSE 'Góc nhìn khán đài rộng, không gian thoáng đãng'
    END
WHERE distance_meters IS NULL;

-- 3. Bảng đăng ký nhắc nhở giờ mở bán vé (Event Reminders)
CREATE TABLE IF NOT EXISTS ticketing.event_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE,
    remind_before_minutes INTEGER NOT NULL DEFAULT 15 CHECK (remind_before_minutes > 0),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'NOTIFIED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    notified_at TIMESTAMPTZ,
    CONSTRAINT uq_user_event_reminder UNIQUE (user_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_event_reminders_event_status ON ticketing.event_reminders(event_id, status);
CREATE INDEX IF NOT EXISTS idx_event_reminders_user ON ticketing.event_reminders(user_id);
