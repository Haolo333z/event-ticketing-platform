# ADR 0003 — Triển khai catalog và nền outbox (T2.1–T2.5)

Ngày 2026-11-12. Giữ V1 nguyên trạng. V2 tạo catalog và bookings/booking_items nền để FK owner của seats và outbox có đích hợp lệ; chỉ tạo storage, không triển khai booking command T3.1. V3 ở T2.5 tạo payments ledger/outbox. Tickets, compensation, inbox/history/idempotency migration thuộc task sau.

Điểm cần làm rõ trước code: T2.4 nhắc chặn xóa ghế HELD/SOLD, trong khi OpenAPI v0 không có DELETE hay sửa seat/mode. Giữ contract v0: từ chối field seats/mode trong PATCH (400); DELETE không có route (405). Không tự mở endpoint mới. Test chứng minh request đó không đổi ghế. Capacity GA và giá được cập nhật qua ticketTypes theo ID, khóa event và inventory trong service transaction; giá item lịch sử không bị update.

Catalog không có event type trong envelope7 loại booking. T2.5 cung cấp OutboxWriter bắt buộc transaction và booking FK, kiểm tra commit/rollback với fixture booking PostgreSQL thật; không phát BookingHeld giả khi admin tạo event. Không có publisher Kafka hoặc API booking trong Phần3.

Seed chỉ profile local/test và không có production token mặc định. Seed INSERT ON CONFLICT DO NOTHING, không overwrite giá/inventory đã sửa. Mock auth cấu hình bằng demo.auth.enabled, default false; profile local/test bật; fail startup nếu bật cùng bất kỳ profile ngoài local/test. Không ghi token vào DB/log.

PostgreSQL integration test dùng database riêng do script tạo, không reset volume demo người dùng. Test migration chạy hai lần, kiểm tra constraint, seed, API và outbox. Các test này phải chạy ở từng gate trước khi chuyển task.

V4 bổ sung constraint chống JSON null bookingId vì CHECK trong PostgreSQL chấp nhận biểu thức SQL NULL. Không sửa V3 đã áp dụng. OutboxWriter dùng propagation MANDATORY, xác minh version của booking; producer tăng aggregate_version trong transaction trước khi append. Writer lưu payload và metadata envelope, không tự thực hiện hold/confirm.
