-- V29__fix_utf8_categories_and_venues.sql
-- Chuẩn hóa UTF-8 danh mục sự kiện & địa điểm tổ chức, dọn dẹp sự kiện test rác và ưu tiên sự kiện mở bán

-- 1. Cập nhật bảng ticketing.event_categories (UTF-8 tiếng Việt có dấu chuẩn xác)
UPDATE ticketing.event_categories
SET name = 'Đại nhạc hội & Concert',
    description = 'Quy mô 5.000 - 50.000 khán giả mở bán đồng loạt',
    icon_url = 'music'
WHERE id = 'CONCERT';

UPDATE ticketing.event_categories
SET name = 'Nhà hát & Nghệ thuật Hàn lâm',
    description = 'Phân tầng khán phòng và giữ ghế chỉ định chính xác',
    icon_url = 'theater'
WHERE id = 'THEATER';

UPDATE ticketing.event_categories
SET name = 'Hội nghị Công nghệ & Triển lãm',
    description = 'Soát vé mã QR đa cổng, phân luồng khách VIP',
    icon_url = 'conference'
WHERE id = 'CONFERENCE';

UPDATE ticketing.event_categories
SET name = 'Sự kiện Thể thao & Giải đấu',
    description = 'Kiểm soát vé khán đài A, B, C, D tốc độ cao',
    icon_url = 'sports'
WHERE id = 'SPORTS';

-- 2. Cập nhật bảng ticketing.venues (UTF-8 tiếng Việt có dấu chuẩn xác)
UPDATE ticketing.venues
SET name = 'Nhà hát Lớn Hà Nội',
    address = 'Số 1 Tràng Tiền, Hoàn Kiếm',
    city = 'Hà Nội',
    country = 'Vietnam'
WHERE id = '50000000-0000-4000-8000-000000000001';

UPDATE ticketing.venues
SET name = 'Trung tâm Hội nghị Quốc gia',
    address = 'Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm',
    city = 'Hà Nội',
    country = 'Vietnam'
WHERE id = '50000000-0000-4000-8000-000000000002';

UPDATE ticketing.venues
SET name = 'Nhà hát Thành phố Hồ Chí Minh',
    address = 'Số 7 Công Trường Lam Sơn, Bến Nghé, Quận 1',
    city = 'TP. Hồ Chí Minh',
    country = 'Vietnam'
WHERE id = '50000000-0000-4000-8000-000000000003';

UPDATE ticketing.venues
SET name = 'Sân vận động Quốc gia Mỹ Đình',
    address = 'Đường Lê Đức Thọ, Mỹ Đình, Nam Từ Liêm',
    city = 'Hà Nội',
    country = 'Vietnam'
WHERE id = '50000000-0000-4000-8000-000000000004';

-- 3. Dọn dẹp/ẩn các bản ghi test rác cũ để giải phóng trang hiển thị đầu tiên
UPDATE ticketing.events
SET status = 'DRAFT'
WHERE status = 'PUBLISHED'
  AND (
    title ILIKE '%concurrency%'
    OR title ILIKE '%other event%'
    OR title ILIKE '%check-in test%'
    OR title ILIKE '%dummy%'
  );

-- 4. Gia hạn mở bán cho các sự kiện thật (đặc biệt là các sự kiện SEATED)
UPDATE ticketing.events
SET sale_start_at = clock_timestamp() - INTERVAL '1 day',
    sale_end_at = clock_timestamp() + INTERVAL '30 days',
    starts_at = clock_timestamp() + INTERVAL '35 days'
WHERE status = 'PUBLISHED'
  AND (
    title ILIKE '%Giao Hưởng%'
    OR title ILIKE '%Vũ -%'
    OR title ILIKE '%ghế chỉ định%'
    OR title ILIKE '%Mùa Hè%'
  );
