# ADR 0004 — Booking GA, idempotency và UI (T3.1–T3.5)

Ngày 2026-11-12. Giữ V1–V4, thêm V5 cho idempotency/history và V6 siết cạnh history ban đầu. V6 là migration mới vì CHECK SQL chấp nhận UNKNOWN khi from_status NULL; không sửa V5 đã chạy. Không chọn ghế, payment processing, expiry worker hay Kafka.

## Làm rõ trước triển khai

DATA_MODEL mô tả row idempotency hoàn chỉnh, trong khi ARCHITECTURE yêu cầu reserve key trước booking. V5 cho booking_id/response_status/response_body NULL **chỉ khi transaction chưa commit**. Deferred constraint trigger kiểm tra row cuối ở COMMIT, từ chối row chưa hoàn chỉnh; composite FK kiểm tra account khớp booking. Không có STARTED bền, không có transaction reserve riêng hay cleanup inline.

INSERT ... ON CONFLICT DO NOTHING trên PK(account,operation,key) trong READ COMMITTED. Request trùng chờ transaction đầu; statement SELECT sau đó đọc row đã commit, so SHA-256 canonical UUID lowercase + quantity. Replay trả đúng body JSON đã lưu và Location cũ, trước khi kiểm tra lại giá/trạng thái catalog; expired cũng replay nguyên response PENDING_PAYMENT ban đầu. Lỗi inventory/timeout rollback cả row key nên có thể retry cùng key.

Booking transaction: reserve key → share lock event → share lock ticket type (cùng thứ tự với admin) → giá/giờ bán từ DB → booking + payment_intent_id (chỉ UUID nhóm, chưa có payment attempt) → conditional UPDATE GA → item snapshot → history → BookingHeld outbox → hoàn thiện response idempotency → commit rồi mới HTTP202. Không tăng sold_count khi giữ vé. Giá/tổng tiền do server tính.

Phần4 chỉ nhận GA; seatIds hoặc type SEATED trả400 VALIDATION_ERROR. Request schema của POST hiện trỏ GaSelection; component SeatSelection/CreateBooking giữ làm thiết kế T4, chưa phải input đang nhận. Chưa có mã lỗi riêng cho cửa sổ bán: sự kiện không PUBLISHED (kể cả CLOSED)/không có type trả404, type inactive/hết giờ bán trả409 INVENTORY_UNAVAILABLE. Không tự expire PENDING_PAYMENT khi GET hoặc qua hạn; worker thuộc T4.3. UI hiện trạng thái server, không tự đổi EXPIRED.

## Timeout và retry

Policy cấu hình khởi đầu: lock1500ms, statement/attempt5000ms, total10000ms, connection acquisition2000ms, TTL600s, max quantity6. Mỗi transaction dùng TransactionTemplate riêng; trả sau commit/rollback. Chỉ retry40P01/40001 tối đa3attempt với backoff50/100ms+jitter, trong tổng deadline (chừa ngân sách pool acquisition trước mỗi attempt). 55P03/57014/pool timeout/connection error trả503 BOOKING_BUSY + Retry-After1; không coi là IDEMPOTENCY_IN_PROGRESS. Error handler booking không ánh xạ lỗi integrity nội bộ thành CATALOG_CONFLICT.

## Quyền và giao diện

POST và /api/me/bookings chỉ CUSTOMER. GET booking cho owner hoặc ADMIN; customer khác trả404. Danh tính chỉ từ DemoPrincipal. Test domain gắn principal fixture có user trong PostgreSQL; auth HTTP kiểm tra riêng bằng token seed.

UI dùng API thật, chỉ hiển thị giá đơn vị từ catalog và tổng từ response booking. Một lần gửi có key/payload cố định lưu sessionStorage trước POST; network error/503 giữ lại để retry cùng key, khóa thay đổi payload khi chưa biết kết quả. 409inventory/400 là kết quả rõ ràng, bỏ pending command để chọn lại. Không tự gửi lại bằng key mới khi kết quả chưa rõ. Không lưu bearer token trong storage hoặc hiện/log token. Poll GET booking và lịch sử của principal; không thêm thanh toán hay seat map.

Gate nghiệm thu: PostgreSQL thật cho success/owner/history, replay/separate-account/different payload/expired, rollback sau outbox, key và inventory timeout,50request vượt capacity, burst cùng key và bounded retry. Frontend có DOM interaction tests và kiểm tra live với backend. Số đo chỉ chứng minh các ca đã chạy trên máy demo, không khẳng định mọi tải đều không oversell.
