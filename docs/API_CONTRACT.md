# API_CONTRACT — Hợp đồng HTTP v0.4

> Bản đặc tả trước khi có OpenAPI machine-readable. Khi triển khai, OpenAPI là bản thực thi của hợp đồng này; thay đổi phải cập nhật đồng bộ.

## Quy tắc chung

- Base path /api. API được bảo vệ nhận Authorization: Bearer demo-customer-token hoặc demo-admin-token trong local/test; server lấy account/role từ principal đã seed.
- POST /api/bookings bắt buộc Idempotency-Key (1–128 ký tự). Key scope theo account và operation. Cùng key/payload sau khi booking commit trả cùng status/body/bookingId, kể cả booking hiện đã EXPIRED; GET booking trả trạng thái mới. Payload khác trả 409 IDEMPOTENCY_KEY_REUSED. Conflict inventory có thể retry không ghi kết quả terminal.
- Mọi lỗi trả {code,message,correlationId,details?}. Không trả stack trace. Thời gian ISO-8601 UTC, tiền là số nguyên VND. Không tin giá trong request.
- Tài nguyên của customer chỉ chủ sở hữu hoặc admin được xem. Endpoint thao tác demo/replay chỉ có ở local/test và có role admin khi thích hợp.
- POST /api/bookings trả 202 vì hold đã commit nhưng quy trình payment vẫn pending; kèm Location: /api/bookings/{bookingId}. Đây là lựa chọn contract nhất quán từ tuần 3 đến tuần 6.
- Timeout khi chờ khóa trong transaction booking phải rollback. Khi chưa xác định được chính xác nguồn tranh chấp, trả 503 BOOKING_BUSY cùng Retry-After; client thử lại cùng Idempotency-Key. 409 IDEMPOTENCY_IN_PROGRESS chỉ dùng nếu implementation chứng minh được key đó vẫn đang được xử lý; không suy ra từ mọi lock timeout. Ngưỡng chờ và cách phân loại chốt ở T1.4, kiểm chứng IDEM-03.

## Danh mục endpoint

| Method/path | Role | Ý nghĩa/response |
|---|---|---|
| GET /api/events, /api/events/{id} | public demo | Danh sách/chi tiết |
| GET /api/events/{id}/availability | public demo | Snapshot ghế/GA, không cam kết giữ |
| POST /api/bookings | CUSTOMER | 202 booking pending; idempotent |
| GET /api/bookings/{id} | owner/ADMIN | Trạng thái, expiresAt, history tóm tắt |
| GET /api/me/bookings | CUSTOMER | Lịch sử của principal, phân trang |
| POST /api/bookings/{id}/cancel | owner | Chỉ PENDING_PAYMENT trước khi result thành công; 200 với booking CANCELLED sau transaction |
| POST /api/bookings/{id}/simulate-payment | owner, local/test | SUCCESS/FAILURE/DELAY_TIMEOUT; 202 paymentIntentId, idempotency theo scenario request ID |
| GET /api/bookings/{id}/tickets | owner/ADMIN | Vé khi CONFIRMED; không có vé cho EXPIRED/REFUND_REQUIRED |
| POST /api/admin/events, PATCH /api/admin/events/{id} | ADMIN | Tạo/sửa sự kiện theo quy tắc admin |
| GET /api/admin/bookings | ADMIN | Danh sách có filter/trạng thái |
| GET /api/admin/operations/outbox | ADMIN, local/test | Trạng thái event |
| POST /api/admin/operations/events/{eventId}/replay | ADMIN, local/test | Replay có audit, giới hạn, 202 |

## Request/response mẫu

    POST /api/bookings
    Authorization: Bearer demo-customer-token
    Idempotency-Key: buy-001
    {"eventId":"uuid","ticketTypeId":"uuid","seatIds":["uuid-a","uuid-b"]}

    HTTP/1.1 202 Accepted
    Location: /api/bookings/booking-uuid
    {"bookingId":"booking-uuid","status":"PENDING_PAYMENT","expiresAt":"2026-11-12T09:10:00Z","totalAmountVnd":500000}

GA gửi quantity thay seatIds. Chỉ một trong hai hình thức cho một booking MVP; quantity nguyên dương và không vượt giới hạn cấu hình.

    POST /api/bookings/booking-uuid/simulate-payment
    {"scenario":"SUCCESS","requestId":"uuid"}
    HTTP/1.1 202 Accepted
    {"paymentIntentId":"uuid","status":"ACCEPTED"}

DELAY_TIMEOUT nghĩa là kết quả được ghi sau hạn để kiểm thử bù trừ; định nghĩa chính xác thời gian phải ghi trong test fixture.

## Lỗi chính

| HTTP/code | Điều kiện |
|---|---|
| 400 VALIDATION_ERROR | Thiếu/sai body, mode ghế/GA, key không hợp lệ |
| 401 UNAUTHENTICATED | Thiếu/token lạ |
| 403 FORBIDDEN | Role sai, thao tác demo/replay ngoài profile |
| 404 NOT_FOUND | Event/booking không tồn tại hoặc không thuộc principal |
| 409 IDEMPOTENCY_KEY_REUSED | Key cũ với payload khác |
| 409 INVENTORY_UNAVAILABLE | Không đủ inventory |
| 409 HOLD_EXPIRY_PROCESSING | Hold đến hạn chưa được nhả/commit |
| 409 INVALID_BOOKING_STATE | Cancel/payment không hợp lệ |
| 409 IDEMPOTENCY_IN_PROGRESS | Chỉ khi xác định được request cùng key đang xử lý và contract chọn trả sớm thay vì chờ |
| 429 RATE_LIMITED | Chỉ khi rate limit thực sự được triển khai |
| 503 BOOKING_BUSY | Quá hạn chờ khóa chưa phân loại được hoặc DB tạm bận; rollback và Retry-After |

## Quy tắc admin

Không giảm GA capacity dưới held+sold; không xóa/đổi ghế HELD hoặc SOLD; đổi giá chỉ ảnh hưởng booking mới, booking_items giữ snapshot. Sự kiện đang mở bán không được đổi mode của loại vé có booking. Test quyền, race admin chỉnh capacity và booking đồng thời.

## Điều kiện chốt

T1.4 chốt shape UUID, phân trang, giới hạn quantity, giới hạn body theo payload thực tế và CORS theo origin local cụ thể nếu frontend khác origin. Trước frontend tuần 3, có OpenAPI khớp các mã lỗi và response; không thêm endpoint giả không có task triển khai.
