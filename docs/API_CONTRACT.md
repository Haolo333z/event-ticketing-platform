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
## Danh Mục Endpoints EventFlow V2 Toàn Diện

EventFlow V2 chuẩn hóa hệ thống RESTful API phân định rõ theo 4 vai trò nghiệp vụ (CUSTOMER, STAFF, ORGANIZER, ADMIN) và các phân hệ nền tảng:

### 1. Phân hệ Định danh & Xác thực (Identity & RBAC)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Đăng ký tài khoản CUSTOMER mới, trả `AuthResponse` {token, user} |
| POST | `/api/auth/login` | Public | Đăng nhập email/password, trả Bearer JWT + HttpOnly Refresh Cookie |
| POST | `/api/auth/refresh` | Public / Cookie | Xoay vòng phiên an toàn (Token Family Reuse Detection) |
| POST | `/api/auth/logout` | Public / Cookie | Thu hồi refresh token trong PostgreSQL và xóa cookie |
| GET | `/api/auth/me` | Authenticated | Lấy thông tin tài khoản và danh sách roles của phiên đăng nhập |
| POST | `/api/auth/google` | Public | Xác thực đăng nhập Google OIDC, tự động liên kết tài khoản |

### 2. Phân hệ Khách hàng (Customer Commerce & E-Ticket)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| GET | `/api/events` | Public | Danh sách sự kiện đã xuất bản (PUBLISHED), hỗ trợ phân trang & tìm kiếm |
| GET | `/api/events/{id}` | Public | Chi tiết sự kiện, thông tin địa điểm và các loại vé đang mở |
| GET | `/api/events/{id}/availability` | Public | Snapshot tồn kho tham khảo (vé GA còn trống, trạng thái ghế ngồi) |
| POST | `/api/bookings` | CUSTOMER | Khóa hàng nguyên tử `SELECT FOR UPDATE`, tạo đơn giữ chỗ 10 phút, trả 202 Location |
| GET | `/api/bookings/{id}` | Owner / ADMIN | Chi tiết đơn đặt vé, trạng thái thanh toán và lịch sử chuyển dịch |
| GET | `/api/me/bookings` | CUSTOMER | Danh sách lịch sử các đơn đặt vé của tài khoản đăng nhập |
| POST | `/api/bookings/{id}/cancel` | Owner | Hủy giữ chỗ đơn hàng khi đang ở trạng thái `PENDING_PAYMENT` |
| GET | `/api/bookings/{id}/tickets` | Owner / ADMIN | Danh sách vé điện tử đã phát hành (chỉ khả dụng khi `CONFIRMED`) |
| GET | `/api/tickets/{id}/qr` | Owner / STAFF / ADMIN | Sinh Signed Opaque QR Token ký số HMAC-SHA256 chống giả mạo |

### 3. Phân hệ Cổng Thanh Toán Sandbox & Đối Soát (Payment Gateway)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| POST | `/api/payments/checkout` | CUSTOMER | Sinh URL thanh toán ký số HMAC-SHA512 chuyển hướng sang VNPAY Sandbox |
| GET | `/api/payments/vnpay/ipn` | Server-to-Server | Tiếp nhận Server Callback IPN, đối soát chữ ký số, xử lý idempotent |
| GET | `/api/payments/vnpay/return` | Public | Tiếp nhận kết quả thanh toán trả về trên trình duyệt người dùng |
| GET | `/api/bookings/{id}/payment-status`| Owner / ADMIN | Polling kiểm tra trạng thái thanh toán đơn hàng |
| POST | `/api/bookings/{id}/simulate-payment`| Owner (test) | Mô phỏng 3 kịch bản kiểm thử (SUCCESS, FAILURE, DELAY_TIMEOUT) |

### 4. Phân hệ Soát Vé Sự Kiện (Staff Portal)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| GET | `/api/staff/events` | STAFF / ADMIN | Danh sách sự kiện nhân viên được phân công làm việc |
| POST | `/api/staff/check-in` | STAFF / ADMIN | Giao dịch soát vé nguyên tử, kiểm tra chữ ký số, chống quét trùng vé |
| GET | `/api/staff/events/{id}/stats` | STAFF / ADMIN | Thống kê realtime: tổng vé bán, đã check-in, còn lại, tỷ lệ % |
| GET | `/api/staff/events/{id}/history` | STAFF / ADMIN | Nhật ký 50 lượt check-in gần nhất tại cổng soát vé |

### 5. Phân hệ Quản Trị Nhà Tổ Chức (Organizer Management)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| GET | `/api/organizer/profile` | ORGANIZER / ADMIN| Lấy hồ sơ đối tác doanh nghiệp của Nhà tổ chức |
| POST | `/api/organizer/profile` | Authenticated | Khởi tạo / cập nhật hồ sơ đối tác (tự động cấp vai trò ORGANIZER) |
| GET | `/api/venues` | Authenticated | Danh mục địa điểm biểu diễn tiêu chuẩn và layout ghế ngồi |
| GET | `/api/categories` | Public | Danh mục phân loại sự kiện (Concert, Theater, Conference, Sports) |
| GET | `/api/organizer/events` | ORGANIZER / ADMIN| Danh sách sự kiện thuộc quyền sở hữu của Nhà tổ chức (cô lập IDOR) |
| POST | `/api/organizer/events` | ORGANIZER / ADMIN| Tạo sự kiện mới (trạng thái ban đầu `DRAFT`) |
| GET | `/api/organizer/events/{id}` | ORGANIZER / ADMIN| Chi tiết sự kiện thuộc quyền sở hữu |
| PUT | `/api/organizer/events/{id}` | ORGANIZER / ADMIN| Chỉnh sửa thông tin sự kiện |
| POST | `/api/organizer/events/{id}/publish`| ORGANIZER / ADMIN| Xuất bản mở bán sự kiện ra Public Catalog (`PUBLISHED`) |
| POST | `/api/organizer/events/{id}/close`| ORGANIZER / ADMIN| Đóng bán vé sự kiện (`CLOSED`) |
| POST | `/api/organizer/events/{id}/staff`| ORGANIZER / ADMIN| Phân công nhân sự soát vé cho sự kiện |
| DELETE | `/api/organizer/events/{id}/staff/{userId}`| ORGANIZER / ADMIN| Thu hồi quyền soát vé của nhân sự tại sự kiện |

### 6. Phân hệ Quản Trị Hệ Thống Toàn Nền Tảng (Admin Platform)
| Method | Path | Role | Mô Tả & Response |
|---|---|---|---|
| GET | `/api/admin/users` | ADMIN | Danh sách người dùng toàn sàn, tìm kiếm họ tên/email/phone, lọc vai trò |
| POST | `/api/admin/users/{id}/roles`| ADMIN | Cấp / thu hồi vai trò người dùng (chống `CANNOT_DEMOTE_SELF`) |
| PUT | `/api/admin/users/{id}/status`| ADMIN | Khóa / mở khóa tài khoản (chống `CANNOT_LOCK_SELF`) |
| GET | `/api/admin/refunds` | ADMIN | Danh sách các yêu cầu hoàn tiền đang chờ phê duyệt |
| POST | `/api/admin/refunds/{id}/process`| ADMIN | Phê duyệt hoàn tiền (`APPROVE` -> COMPLETED, `REJECT` -> FAILED) |
| GET | `/api/admin/reports/summary` | ADMIN | Chỉ số KPI toàn nền tảng: tổng doanh thu, vé bán, occupancy, check-in |
| GET | `/api/admin/reports/events` | ADMIN | Báo cáo chi tiết doanh thu và tỷ lệ lấp đầy theo từng sự kiện |
| GET | `/api/admin/reports/export-csv` | ADMIN | Xuất báo cáo CSV chuẩn RFC 4180 có UTF-8 BOM, chống CSV Formula Injection |
| GET | `/api/admin/leads` | ADMIN | Danh sách leads tư vấn doanh nghiệp, lọc trạng thái |
| PUT | `/api/admin/leads/{id}/status` | ADMIN | Cập nhật trạng thái leads (`NEW`, `CONTACTED`, `CONVERTED`, `CLOSED`) |
| POST | `/api/contact/leads` | Public | Tiếp nhận yêu cầu tư vấn & leads từ trang Liên hệ khách hàng |
| GET | `/api/admin/events` | ADMIN | Quản lý toàn bộ sự kiện trên sàn (kể cả DRAFT, CLOSED) |
| GET | `/api/admin/events/{id}/inventory`| ADMIN | Tồn kho chi tiết phục vụ kiểm toán |
| GET | `/api/admin/bookings` | ADMIN | Quản trị đơn hàng toàn sàn, lọc theo trạng thái |
| GET | `/api/admin/operations/outbox`| ADMIN | Danh sách sự kiện Outbox phục vụ vận hành EDA |
| GET | `/api/admin/operations/recovery`| ADMIN | Quản lý lỗi tiêu thụ Kafka và trạng thái Dead Letter Queue (DLQ) |
| POST | `/api/admin/operations/events/{id}/replay`| ADMIN | Replay sự kiện có ghi nhật ký kiểm toán (giới hạn tối đa 3 lần/event) |


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

T8.3 bổ sung hai GET chỉ ADMIN local/test: `/api/admin/operations/recovery?page=0&size=20&status=OPEN` (OPEN/RESOLVED/ALL) và `/api/admin/operations/events/{id}`. Danh sách gồm lỗi publisher hiện tại hoặc event đã có audit replay và recovery consumer/DLQ bền. OPEN là outbox FAILED/PENDING cần theo dõi hoặc consumer DEAD; RESOLVED là outbox PUBLISHED có audit hoặc consumer RESOLVED. Chi tiết lấy outbox gốc, correlation, trạng thái publisher, consumer receipt, metadata lỗi/DLQ và audit tối đa3request. Chỉ trả lý do được ánh xạ an toàn từ trạng thái/class lỗi đã lưu; không trả raw_event, record_key, topic, payload, exception text tùy ý. Record không có outbox hợp lệ chỉ được xem, không có API sửa/raw replay. Pagination dùng snapshot REPEATABLE_READ, order createdAt DESC/id DESC/source để ổn định khi timestamp trùng.

POST replay và response202 ACCEPTED giữ nguyên hợp đồng T6.4, không thêm force/reset hoặc thay budget3. 202 chỉ xác nhận yêu cầu đã ghi audit/xếp hàng; không chứng minh consumer đã chạy lại. Cùng requestId/reason không ghi audit hay reset attempts lần nữa, kể cả khi budget đã hết. Request mới cho event đã xử lý vẫn có thể republish cùng envelope trong budget, inbox làm nghiệp vụ no-op. UI giữ requestId/reason khi chưa rõ kết quả, chỉ gửi lại cùng yêu cầu khi người dùng bấm; không tự POST khi tải/refresh màn hình. Audit hiển thị nội dung lý do do admin nhập dưới dạng text, không ghi vào log.

Health vận hành T8.1 nằm ngoài18operation hiện tại trong OpenAPI: GET `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` công khai trên bind localhost và không cần bearer token. UP trả200, DOWN/OUT_OF_SERVICE trả503; chỉ status (root có thể thêm tên groups), không có components/details. Readiness yêu cầu PostgreSQL và Kafka khi messaging bật; liveness chỉ là trạng thái tiến trình. Health không bảo đảm mọi record đã xử lý hoặc backlog bằng0. X-Correlation-Id vẫn được trả và ghi log như request API.

Metrics T8.2 cũng là Actuator diagnostics ngoài18operation: GET `/actuator/metrics` liệt kê tên, GET `/actuator/metrics/{name}` trả `measurements`, `baseUnit`, `availableTags`; query `tag=key:value` lọc series hiện có, không tạo label mới. Chỉ ADMIN local/test: thiếu/lạ token401, customer403; profile khác mặc định tắt access endpoint. Các endpoint Actuator khác vẫn không mở. Tên/đơn vị/label/semantics ở ARCHITECTURE mục T8.2. DB gauges dùng dữ liệu commit/cached tối đa1s mặc định, query lỗi là NaN/available0; counter/timer reset khi JVM restart. Khi DB auth không truy cập được, metrics HTTP có thể bị lỗi auth DB trước khi đọc gauge; dùng health để theo dõi mất kết nối, không giả rằng endpoint metrics luôn scrape được khi PG chết.

GET `/api/admin/operations/outbox?page=0&size=20` và POST `/api/admin/operations/events/{eventId}/replay` đã triển khai theo OpenAPI. Chỉ admin local/test. Replay body `{requestId,reason}` (UUID và1..500ký tự không chỉ khoảng trắng); cùngrequest/reason202 kết quả cũ, khácreason409IDEMPOTENCY_KEY_REUSED, quá3request409REPLAY_LIMIT_REACHED. Giữ nguyên eventID và inbox. Audit lưu admin/time/reason trong outbox. API không đợi Kafka và không hứa side effect chạy lại.

Khi messaging bật, simulate-payment202 chỉ chứng nhận durable result đã commit. Khách poll GET booking/tickets; expiry dùng cùngprocessor có thể xử lý payment đúng hạn ngay khi broker chưa phục hồi. Không thay state machine hoặc payload event. REFUND_REQUIRED giữ nguyên sau refund mô phỏng; tiến độ kỹ thuật xem DB compensation/history (dashboard thuộc phần sau).
