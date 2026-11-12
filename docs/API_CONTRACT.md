# API_CONTRACT — Hợp đồng HTTP v0.4

> T1.4 đã chốt OpenAPI 3.1 tại [contracts/openapi.json](contracts/openapi.json). File là hợp đồng cho triển khai, không chứng minh endpoint nghiệp vụ đã chạy. Quyết định chi tiết tại [ADR 0002](adr/0002-api-domain-contract.md); thay đổi phải cập nhật đồng bộ.

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

T1.4 đã chốt UUID, phân trang page=0/size=20/max100, GA/seat tối đa6. Body booking/simulator/replay 8192 byte, catalog 131072 byte UTF-8; 413 PAYLOAD_TOO_LARGE, 415 UNSUPPORTED_MEDIA_TYPE, lỗi bất ngờ 500 INTERNAL_ERROR. Admin catalog conflict dùng 409 CATALOG_CONFLICT; replay quá 3 lần/event dùng 409 REPLAY_LIMIT_REACHED. RATE_LIMITED và IDEMPOTENCY_IN_PROGRESS giữ reserved, không phát khi chức năng tương ứng chưa được triển khai. Response vé trả [] nếu chưa CONFIRMED; owner sai trả404, role sai trả403. Request không khai báo field lạ; server không tin userId/role/giá client.

CORS local/test chỉ http://127.0.0.1:5173 và http://localhost:5173, credentials=false; allow/expose headers ghi trong OpenAPI x-local-cors. Chính sách HTTP/giới hạn/deadline trong ADR 0002 là yêu cầu triển khai tiếp theo, chưa được enforce trong backend scaffold.

SET LOCAL lock_timeout=1500ms, statement_timeout=5s; 55P03/57014 rollback rồi503 BOOKING_BUSY + Retry-After:1; client retry cùng key. Không suy loại contention từ SQLSTATE. Chỉ deadlock40P01/serialization40001 retry hữu hạn trong tổng deadline; không tự retry domain409. Connection timeout2s, attempt5s, tổng nghiệp vụ10s/request15s là cấu hình khởi đầu, cần test khi có booking thật.

MVP createEvent nhận catalog lồng ticketTypes/seats; patchEvent chỉ cập nhật type ID hiện có. Không có endpoint xóa/thêm ghế riêng trong contract v0. Thay đổi mở rộng phải cập nhật hợp đồng trước. Tổng ghế/event<=200, type<=20, các cross-field invariant xem DATA_MODEL/ADR. paymentIntentId lấy từ bookings, PaymentResultRecorded.paymentResultId là payments.id, requestId client lưu payments.request_id. DELAY_TIMEOUT ghi result khi DB clock >= expiresAt+1s. Simulator cho phép ghi kết quả sau expiry/cancel/fail để kiểm chứng compensation, không tự confirm lại. HTTP202 không hứa booking sẽ confirmed.
