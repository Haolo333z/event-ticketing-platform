# ADR 0002 — Hợp đồng API/domain v0 (T1.4)

- Ngày: 2026-11-12. Accepted ở mức thiết kế; chưa áp dụng vào endpoint nghiệp vụ.
- Nguồn trạng thái: PRD §5. OpenAPI: [openapi.json](../contracts/openapi.json); event schema: [events.schema.json](../contracts/events.schema.json).
- Không sửa V1 hay tạo V2 ở T1.4. DB schema nghiệp vụ trong DATA_MODEL là đầu vào cho T2.1 và các migration sau.

## Danh tính, truy cập và dữ liệu

Mock bearer chỉ local/test, server ánh xạ principal từ token seed, ngoài profile không đăng ký simulator/replay và trả 403 nếu bị gọi; cấu hình mock auth ngoài profile phải fail startup. Public chỉ đọc catalog đã published (admin có thể đọc draft qua kết quả create/patch). CUSTOMER tạo booking, hủy/thanh toán booking của mình; ADMIN được đọc booking/vé và thao tác admin, không tự hủy hoặc thanh toán thay customer. Sai role trả 403; customer truy cập booking người khác trả 404. Không nhận accountId/userId/role/giá từ body booking; JSON có thuộc tính lạ bị 400.

UUID theo format uuid, timestamp UTC kết thúc Z, VND integer 0..10^12 (trong giới hạn chính xác JavaScript), tổng booking cũng <=10^12. Mỗi booking MVP chỉ một event/ticket type, chọn quantity GA 1..6 HOẶC seatIds 1..6 duy nhất; không trộn hai cách. booking_items vẫn nhiều hàng khi đặt nhiều ghế; GA một hàng quantity N. Tổng giá/ticket type/mode lấy từ DB.

Phân trang page từ 0 (tối đa 10000), size mặc định 20, tối đa 100; response items/page/size/totalElements. Catalog order startsAt ASC,id ASC; booking/outbox order createdAt DESC,id DESC. Không cam kết snapshot xuyên nhiều trang.

Admin create nhận tối đa 20 ticket type và tổng 200 ghế/event; tên 100 ký tự, label ghế 40, title/venue 200, description 2000. Unicode tính code point; nhãn duy nhất trong event. Patch chỉ sửa field được gửi; ticketTypes là cập nhật theo ID đã có, không thay toàn bộ collection; không thêm/xóa ghế hoặc đổi mode trong v0. Tạo catalog đầy đủ qua POST; tăng phạm vi CRUD cần đồng bộ OpenAPI trước. Kiểm tra saleStartAt < saleEndAt <= startsAt; capacity không dưới held+sold; giá snapshot booking không thay đổi.

## Giới hạn request và CORS

Body tính theo byte UTF-8: booking/simulator/replay tối đa **8192 bytes**, POST/PATCH catalog **131072 bytes**. Chỉ application/json; Content-Encoding chỉ identity (không nhận gzip để tránh vượt giới hạn sau giải nén). Kiểm tra cả Content-Length và luồng body chunked thực nhận, dừng ở limit+1; 413 PAYLOAD_TOO_LARGE. Sai media type/encoding trả 415 UNSUPPORTED_MEDIA_TYPE; JSON sai/field lạ trả 400. Endpoint không nhận body phải từ chối body không rỗng (400). Giới hạn JSON nesting 10; max header 16 KiB, query search chưa mở trong v0.

Fixture UTF-8 trong contracts/examples.json dùng Unicode, 20 ticket type/200 ghế, có các chuỗi dài; tools/contracts đo byte thực. Đây là cơ sở chọn headroom 128 KiB, không phải số đo tải. JSON schema giới hạn ghế mỗi type; **tổng 200 ghế/event và nhãn unique phải kiểm tra domain**, không suy ra chỉ từ schema.

CORS chỉ cho API /api/** trong local/test, đúng hai origin `http://127.0.0.1:5173`, `http://localhost:5173`; methods GET/POST/PATCH/OPTIONS; allowed headers Authorization, Content-Type, Idempotency-Key, X-Correlation-Id; expose Location, Retry-After, X-Correlation-Id; credentials=false, maxAge=600s. Không wildcard hay phản chiếu Origin tùy ý. Preflight hợp lệ trả 204 trước auth; origin khác không có Access-Control-Allow-Origin. CORS không thay thế auth. Frontend dùng đúng port 5173 (strictPort khi triển khai); preview 4173 chưa nằm trong allowlist.

## Ngưỡng chờ và ánh xạ lỗi

Chốt cấu hình khởi đầu có thể override: DB lock_timeout **1500ms**, statement_timeout **5s** (SET LOCAL trong transaction); transaction attempt tối đa 5s, tổng deadline nghiệp vụ 10s, connection acquisition tối đa 2s, ngân sách request 15s. Booking không dùng pool wait 30s của scaffold; khi triển khai chuyển connectionTimeout sang 2s và test lại. Không áp deadline bằng HTTP timeout rồi bỏ transaction tiếp tục chạy: phải cancel/rollback và chỉ trả lỗi sau khi transaction đã kết thúc.

Thử PostgreSQL 17.11 bằng hai kết nối advisory lock trong tools/contracts/measure-lock.ps1 để kiểm tra lock_timeout/SQLSTATE/rollback, không tạo bảng nghiệp vụ. Số đo bao gồm overhead Docker/psql, chưa đại diện lock ghế/GA dưới tải. Ngưỡng phải được kiểm chứng IDEM-03/LOAD-01 và điều chỉnh bằng ADR khi có code.

| Nguồn lỗi | Hành vi sau rollback |
|---|---|
| 55P03 lock timeout ở unique idempotency hoặc inventory | 503 BOOKING_BUSY, Retry-After: 1; retry cùng key. Không đoán loại lock từ SQLSTATE |
| Chứng minh riêng được key đang xử lý | v0 vẫn chọn chờ/503; 409 IDEMPOTENCY_IN_PROGRESS được giữ reserved, chưa phát trong baseline |
| 57014 statement cancel, hết transaction/deadline, pool exhaustion hoặc kết nối tạm mất | 503 BOOKING_BUSY; không cache lỗi terminal; nếu commit outcome chưa rõ phải lookup/retry cùng key |
| 40P01/40001 | Retry toàn transaction tối đa 3 lần trong deadline tổng, backoff 50/100ms+jitter; hết ngân sách trả 503 |
| Domain inventory/state conflict | 409 theo code rõ ràng; không retry tự động lỗi terminal |
| Lỗi không dự kiến | 500 INTERNAL_ERROR, correlationId, không stack trace |

Retry-After là số giây. Correlation ID hợp lệ UUID được nhận lại; thiếu/sai thì server sinh UUID, trả X-Correlation-Id và body lỗi. Các lỗi trước controller cũng dùng Error envelope khi application xử lý được; lỗi parse header ở HTTP container có thể chỉ trả HTTP 400/431 và đóng kết nối.

Tên cấu hình sẽ dùng khi triển khai: `API_COMMAND_BODY_MAX_BYTES=8192`, `API_CATALOG_BODY_MAX_BYTES=131072`, `API_ALLOWED_ORIGINS=http://127.0.0.1:5173,http://localhost:5173`, `BOOKING_LOCK_TIMEOUT_MS=1500`, `BOOKING_STATEMENT_TIMEOUT_MS=5000`, `BOOKING_ATTEMPT_TIMEOUT_MS=5000`, `BOOKING_TOTAL_TIMEOUT_MS=10000`, `API_REQUEST_TIMEOUT_MS=15000`, Hikari `connection-timeout=2000`. Không thêm các biến chưa được backend đọc vào .env.example trong task thiết kế này. Startup phải validate lock_timeout < statement_timeout <= attempt_timeout <= total_timeout < request_timeout và connection wait nằm trong ngân sách tổng; retry chỉ chạy khi còn đủ ngân sách.

## Payment, event và replay đã làm rõ

- bookings giữ payment_intent_id UUID duy nhất, tạo cùng hold; simulate trả ID này. payments.id là ID của một lần mô phỏng kết quả (cũng là paymentResultId trong event); payments.request_id là requestId client, unique(booking_id,request_id). Cùng requestId/scenario replay 202 cũ, khác scenario 409. Mỗi requestId mới đại diện một lần thanh toán mô phỏng riêng, vì vậy compensation unique theo payments.id.
- DELAY_TIMEOUT được lưu SCHEDULED, result_due_at=booking.expires_at+1s; scheduler chỉ ghi SUCCESS và recorded_at bằng DB clock khi tới hạn. recorded_at NULL khi scheduled; result terminal có recorded_at. Không sleep trong transaction hoặc giữ lock chờ đến hạn. Nếu scheduler trễ, vẫn dùng recorded_at thực lúc ghi bền.
- Envelope 7 event type version 1 được kiểm tra JSON Schema. aggregateId/payload.bookingId phải khớp; PaymentResultRecorded.paymentResultId tra payments.id của chính booking. JSON Schema kiểm tra shape; domain kiểm tra liên kết ID và thời điểm. Mỗi event tăng bookings.aggregate_version trong cùng transaction, kể cả payment result. Không bỏ payment theo version hiện tại của booking.
- Replay giữ nguyên eventId/envelope, một event/lần, tối đa 3 request replay khác nhau/event. Audit jsonb replay_requests trên outbox lưu requestId, admin accountId, reason, requestedAt; khóa row khi append và đặt lại pending. Cùng requestId replay response, khác reason 409 IDEMPOTENCY_KEY_REUSED; quá giới hạn 409 REPLAY_LIMIT_REACHED. Audit giới hạn 3 phần tử; inbox không bị xóa để ép thực thi. Event đã published mà consumer đã xử lý thì replay có thể no-op đúng thiết kế.

## Đối chiếu và giới hạn nghiệm thu

[TRACEABILITY](../TRACEABILITY.md) nối PRD → bảng/API/event → task → test. T1.4 nghiệm thu tài liệu, OpenAPI, fixture và thử timeout DB; không khẳng định CORS/body limit/HTTP error mapping đã được backend enforce. Các gate runtime thuộc T2.2/T2.4/T3.x/T5.x/T6.x/T7.x. Không có endpoint giả được triển khai trong task này.

Nguồn kỹ thuật: [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0.html), [Swagger Parser](https://apidevtools.com/swagger-parser/), [PostgreSQL lock_timeout/statement_timeout](https://www.postgresql.org/docs/17/runtime-config-client.html).
