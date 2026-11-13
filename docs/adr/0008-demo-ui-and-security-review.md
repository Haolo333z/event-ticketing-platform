# ADR0008 — UI demo và rà bảo mật T7

Ngày 2026-11-13. Đầu vào commit4a89926, working tree sạch; T6 đã push/CI xanh theo thông tin người dùng.

## Phạm vi và điểm không thống nhất

Yêu cầu gọi T7.1–T7.5 nhưng TASKS chỉ có T7.1–T7.4. Ưu tiên tài liệu theo chỉ dẫn người dùng: hoàn thành4task này, không tự tạo T7.5 hoặc đổi tên T8.1. Log cấu trúc/metrics/recovery view T8 vẫn ngoài phạm vi; T7.4 kiểm tra không lộ secret/log lỗi và HTTP policy hiện có.

## Quyết định trước code

- Giữ mock bearer token local/test và principal do server ánh xạ. Bộ chọn persona trên UI chỉ chọn fixture request; không gửi userId/role và không phải hàng rào phân quyền. Không lưu token vào storage. Customer giữ nguyên pending command/requestId qua việc đổi màn hình, không tự POST lại.
- Dashboard cần sự kiện DRAFT/CLOSED/inactive và tồn kho của chúng; không mở rộng API public để tránh lộ catalog chưa công bố. Thêm GET `/api/admin/events?page&size` trả EventPage hiện có, và GET `/api/admin/events/{id}/inventory` trả AdminInventory mới (GA capacity/held/sold/available; ghế id/label/status). Hai route chỉ ADMIN, đọc snapshot REPEATABLE_READ, không trả owner/token. Public availability không đổi.
- Hoàn thiện GET `/api/admin/bookings` đã có trong OpenAPI: phân trang và filter6status, role ADMIN kiểm tra ở server/service. Không thêm PII/accountId vào Booking. Dashboard xem history qua GET booking hiện có, outbox qua API T6. Replay UI thuộc T8.3; không thêm nút ép side effect.
- Form tạo/sửa catalog dùng POST/PATCH hiện có, hỗ trợ GA/ghế, giá/capacity/active và thời gian. Mode và danh sách ghế bất biến khi sửa theo ADR0003. Server vẫn kiểm tra capacity>=held+sold và giá snapshot. Lỗi mạng khi tạo catalog không tự retry vì API đó không có idempotency; yêu cầu xem lại danh sách trước khi tạo tiếp.
- Customer UI phân biệt loading/error/empty, trạng thái availability cũ khi refresh lỗi, EXPIRED và REFUND_REQUIRED từ server; countdown không tự nhả/đổi state. Giữ API thật và storage của booking/payment.
- T7.4 rà body UTF-8/size/depth/content-type, field lạ/giá/quyền giả, query validation, lỗi HTTP có correlationId và không lộ stack/SQL/token. Bảo vệ cả lỗi database trong auth filter (ngoài ControllerAdvice). Negative tests kiểm tra response và log; không triển khai rate limit/Redis hoặc observability T8.
- JSON command chỉ nhận một object; từ chối key lặp/trailing JSON để không có hai cách hiểu payload. Giới hạn UTF-8 hiện có giữ nguyên. Reason replay tối đa500 Unicode code point, khớp JSON Schema/PostgreSQL varchar thay vì đếm UTF-16.
- Không đổi schema/event/state machine/hold policy, không thêm migration, không dùng tiền thật. OpenAPI và test hợp đồng cập nhật trước phần code phụ thuộc.

## Gate

SEC-01 local/test/unknown token/customer→admin/prod hoặc profile trộn bật mock phải fail startup; bind loopback. Customer DOM loading/error/empty/expiry/refund và GA/ghế/payment live. Dashboard DOM + HTTP/PostgreSQL thật, CRUD catalog và inventory, booking filter/pagination/detail, outbox. HTTP/log negative cases, secret/dependency scan và toàn bộ suite T6 hồi quy. Ghi số liệu thật trong PROGRESS; GitHub CI mới chỉ sau khi người dùng commit/push.
