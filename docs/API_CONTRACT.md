# API_CONTRACT — Hợp đồng HTTP v0.4

> T1.4 đã chốt OpenAPI 3.1 tại [contracts/openapi.json](contracts/openapi.json). File là hợp đồng cho triển khai, không chứng minh endpoint nghiệp vụ đã chạy. Quyết định chi tiết tại [ADR 0002](adr/0002-api-domain-contract.md); thay đổi phải cập nhật đồng bộ.

Đến T7 có16operation: catalog public/admin, booking GA/SEATED/mine/admin, simulate-payment/cancel/tickets, outbox/replay. T7 hoàn thiện admin bookings và thêm hai API đọc catalog/inventory private theo ADR0008; worker vẫn nội bộ. Không thêm provider callback/check-in hoặc đổi state machine.

## Quy tắc chung

Phần6 mở simulate-payment/cancel/tickets theo ADR0006. Cancel không nhận body (kể cả{}); repeat CANCELLED trả200, state khác không hợp lệ409. Ticket thêm seatId/seatLabel nullable, code opaque dùng tra cứu demo, không phải QR/chữ ký offline. Không thêm callback HTTP ngoài; local callback kiểm tra paymentResultId/booking/result bền, simulator chỉ nhận scenario/requestId và bearer owner local/test. Mọi timestamp/giá/trạng thái kết quả lấy server/DB.

T4 mở POST theo CreateBooking oneOf đã chốt: GA quantity hoặc SEATED seatIds1..6 khác nhau, cùng event/type; không nhận cả hai. Hash GA giữ tương thích, seat hash chuẩn hóa UUID/sắp thứ tự. Public availability giữ schema cũ id/label/status, không công khai owner. HELD quá hạn chưa nhả trả409 HOLD_EXPIRY_PROCESSING hoặc INVENTORY_UNAVAILABLE; scheduler riêng nhả rồi GET trả EXPIRED. Replay sau expiry vẫn202/body gốc. Chi tiết tại ADR0005; không thêm endpoint expiry công khai.

- Base path /api. API được bảo vệ nhận Authorization: Bearer demo-customer-token hoặc demo-admin-token trong local/test; server lấy account/role từ principal đã seed.
- POST /api/bookings bắt buộc Idempotency-Key (1–128 ký tự). Key scope theo account và operation. Cùng key/payload sau khi booking commit trả cùng status/body/bookingId, kể cả booking hiện đã EXPIRED; GET booking trả trạng thái mới. Payload khác trả 409 IDEMPOTENCY_KEY_REUSED. Conflict inventory có thể retry không ghi kết quả terminal.
- Mọi lỗi trả {code,message,correlationId,details?}. Không trả stack trace. Thời gian ISO-8601 UTC, tiền là số nguyên VND. Không tin giá trong request.
- Tài nguyên của customer chỉ chủ sở hữu hoặc admin được xem. Endpoint thao tác demo/replay chỉ có ở local/test và có role admin khi thích hợp.
- POST /api/bookings trả 202 vì hold đã commit nhưng quy trình payment vẫn pending; kèm Location: /api/bookings/{bookingId}. Đây là lựa chọn contract nhất quán từ tuần 3 đến tuần 6.
- Timeout khi chờ khóa trong transaction booking phải rollback. Khi chưa xác định được chính xác nguồn tranh chấp, trả 503 BOOKING_BUSY cùng Retry-After; client thử lại cùng Idempotency-Key. 409 IDEMPOTENCY_IN_PROGRESS chỉ dùng nếu implementation chứng minh được key đó vẫn đang được xử lý; không suy ra từ mọi lock timeout. Ngưỡng chờ và cách phân loại chốt ở T1.4, kiểm chứng IDEM-03.

## Danh mục endpoint

T7 bổ sung hai API đọc ADMIN theo ADR0008: GET `/api/admin/events` phân trang trả EventPage gồm mọi trạng thái, và GET `/api/admin/events/{id}/inventory` trả snapshot GA capacity/held/sold/available hoặc seats id/label/status, kể cả inactive/DRAFT/CLOSED. Không trả owner/account. GET `/api/admin/bookings` dùng BookingPage hiện có, filter status đúng6state; public catalog/availability giữ nguyên.

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
    {"eventId":"20000000-0000-4000-8000-000000000002","ticketTypeId":"30000000-0000-4000-8000-000000000002","quantity":2}

    HTTP/1.1 202 Accepted
    Location: /api/bookings/booking-uuid
    {"bookingId":"booking-uuid","status":"PENDING_PAYMENT","expiresAt":"2026-11-12T09:10:00Z","totalAmountVnd":200000}

POST hiện nhận GA quantity nguyên1..6 hoặc SEATED seatIds1..6UUID khác nhau. Ví dụ ghế: {"eventId":"uuid","ticketTypeId":"uuid","seatIds":["seat-uuid-1","seat-uuid-2"]}. Schema POST trỏ CreateBooking oneOf; mode phải khớp type và mọi ghế cùng event/type. Không nhận cả quantity/seatIds, field lạ hoặc ghế trùng. Hash chuẩn hóa thứ tự field, UUID hoa/thường, số nguyên GA (2.0 tương đương2); seatIds sắp theo UUID string. Hash GA cũ không đổi. Key chỉ gồm [A-Za-z0-9._:-].

GET booking trả bookingId/eventId/status/expiresAt/expiredAt/totalAmountVnd/history; customer khác404,ADMIN xem được. GET mine chỉCUSTOMER,page0/size20/max100. Worker5s/batch50 gọi chung processor để phân xử result bền trước release; không còn defer ở T5. GET/UI không tự expiry. Replay booking vẫnresponse202gốc; trạng thái hiện tại lấy GET theo Location.

Payment đã chạy trong local/test:

    POST /api/bookings/booking-uuid/simulate-payment
    {"scenario":"SUCCESS","requestId":"uuid"}
    HTTP/1.1 202 Accepted
    {"paymentIntentId":"uuid","status":"ACCEPTED"}

DELAY_TIMEOUT nghĩa là kết quả được ghi sau hạn để kiểm thử bù trừ; định nghĩa chính xác thời gian phải ghi trong test fixture.

DELAY_TIMEOUT lưu SCHEDULED,recorded_atNULL,result_due_at=expiresAt+1s; scheduler ghi SUCCESS bằng DB clock khi đến hạn. Cùng requestId/scenario replay202; khác scenario409 dù booking terminal. Sau khi record+PaymentResultRecorded commit, adapter xử lý bằng transaction riêng;500/503/network có thể để ledger bền chưa processed, client retry cùng requestId/scenario,maintenance/expiry phục hồi. Không tin status/timestamp/amount client. Terminal simulation sau expiry/cancel/failure được phép để test late success và compensation; không tự confirm lại.

GET tickets chỉ owner/ADMIN,[] nếu chưaCONFIRMED. Ticket gồm id/bookingId/bookingItemId/ticketOrdinal/code/seatId/seatLabel; hai field ghế NULL choGA,khôngNULL khi vé ghế. codeEF-UUID opaque, unique; không phải proof offline. Ticket quantity/allocation do booking_item server quyết định. Cancel khôngbody,repeatCANCELLED200;due pending hoặcstatekhác409. Nếu cancel gặp timely success bền thì commit confirm/vé trước khi trả409; không rollback confirm hợp lệ vì cancel bị từ chối.

## Lỗi chính

| HTTP/code | Điều kiện |
|---|---|
| 400 VALIDATION_ERROR | Thiếu/sai body, mode ghế/GA, key không hợp lệ |
| 401 UNAUTHENTICATED | Thiếu/token lạ |
| 403 FORBIDDEN | Role sai, thao tác demo/replay ngoài profile |
| 404 NOT_FOUND | Event/booking không tồn tại hoặc không thuộc principal |
| 409 IDEMPOTENCY_KEY_REUSED | Key cũ với payload khác |
| 409 INVENTORY_UNAVAILABLE | Không đủ inventory, type inactive hoặc ngoài cửa sổ bán |
| 409 HOLD_EXPIRY_PROCESSING | Hold đến hạn chưa được nhả/commit |
| 409 INVALID_BOOKING_STATE | Cancel/payment không hợp lệ |
| 409 IDEMPOTENCY_IN_PROGRESS | Chỉ khi xác định được request cùng key đang xử lý và contract chọn trả sớm thay vì chờ |
| 429 RATE_LIMITED | Chỉ khi rate limit thực sự được triển khai |
| 503 BOOKING_BUSY | Quá hạn chờ khóa chưa phân loại được hoặc DB tạm bận; rollback và Retry-After |

## Quy tắc admin

Không giảm GA capacity dưới held+sold; không xóa/đổi ghế HELD hoặc SOLD; đổi giá chỉ ảnh hưởng booking mới, booking_items giữ snapshot. Sự kiện đang mở bán không được đổi mode của loại vé có booking. Test quyền, race admin chỉnh capacity và booking đồng thời.

## Điều kiện chốt

T1.4 đã chốt UUID, phân trang page=0/size=20/max100, GA/seat tối đa6. Body booking/simulator/replay 8192 byte, catalog 131072 byte UTF-8; 413 PAYLOAD_TOO_LARGE, 415 UNSUPPORTED_MEDIA_TYPE, lỗi bất ngờ 500 INTERNAL_ERROR. Admin catalog conflict dùng 409 CATALOG_CONFLICT; replay quá 3 lần/event dùng 409 REPLAY_LIMIT_REACHED. RATE_LIMITED và IDEMPOTENCY_IN_PROGRESS giữ reserved, không phát khi chức năng tương ứng chưa được triển khai. Response vé trả [] nếu chưa CONFIRMED; owner sai trả404, role sai trả403. Request không khai báo field lạ; server không tin userId/role/giá client.

CORS local/test chỉ http://127.0.0.1:5173 và http://localhost:5173, credentials=false; allow/expose headers ghi trong OpenAPI x-local-cors. Body/CORS policy áp dụng cả booking. Event không PUBLISHED (kể cả CLOSED) hoặc type không thuộc event trả404; type inactive/ngoài giờ bán trả409. Không lấy giá, account hoặc role từ request.

SET LOCAL lock_timeout=1500ms, statement_timeout=5s; 55P03/57014 rollback rồi503 BOOKING_BUSY + Retry-After:1; client retry cùng key. Không suy loại contention từ SQLSTATE. BookingCommandService chỉ retry40P01/40001 tối đa3attempt với backoff trong ngân sách10s; không retry domain409 hoặc55P03. Hikari acquisition2s, transaction attempt5s; frontend abort15s giữ lại key/payload vì kết quả có thể chưa rõ. PostgreSQL thật đã kiểm tra row/key lock timeout và serialization retry; chưa kiểm thử mất mạng đúng lúc COMMIT hoặc pool exhaustion. Các ngưỡng không phải cam kết thời gian HTTP tuyệt đối khi mạng/DB lỗi.

MVP createEvent nhận catalog lồng ticketTypes/seats; patchEvent chỉ cập nhật type ID hiện có. Không có endpoint xóa/thêm ghế riêng trong contract v0. Thay đổi mở rộng phải cập nhật hợp đồng trước. Tổng ghế/event<=200, type<=20, các cross-field invariant xem DATA_MODEL/ADR. paymentIntentId lấy từ bookings, PaymentResultRecorded.paymentResultId là payments.id, requestId client lưu payments.request_id. DELAY_TIMEOUT ghi result khi DB clock >= expiresAt+1s. Simulator cho phép ghi kết quả sau expiry/cancel/fail để kiểm chứng compensation, không tự confirm lại. HTTP202 không hứa booking sẽ confirmed.

## T6 — Operations và xử lý bất đồng bộ

GET `/api/admin/operations/outbox?page=0&size=20` và POST `/api/admin/operations/events/{eventId}/replay` đã triển khai theo OpenAPI. Chỉ admin local/test. Replay body `{requestId,reason}` (UUID và1..500ký tự không chỉ khoảng trắng); cùngrequest/reason202 kết quả cũ, khácreason409IDEMPOTENCY_KEY_REUSED, quá3request409REPLAY_LIMIT_REACHED. Giữ nguyên eventID và inbox. Audit lưu admin/time/reason trong outbox. API không đợi Kafka và không hứa side effect chạy lại.

Khi messaging bật, simulate-payment202 chỉ chứng nhận durable result đã commit. Khách poll GET booking/tickets; expiry dùng cùngprocessor có thể xử lý payment đúng hạn ngay khi broker chưa phục hồi. Không thay state machine hoặc payload event. REFUND_REQUIRED giữ nguyên sau refund mô phỏng; tiến độ kỹ thuật xem DB compensation/history (dashboard thuộc phần sau).
