# ARCHITECTURE — Kiến trúc hệ thống

> Phiên bản: 0.4 · Trạng thái: kiến trúc đề xuất đã qua phản biện

## 1. Quyết định kiến trúc

Dùng modular monolith hướng sự kiện: một codebase backend chia module theo nghiệp vụ, PostgreSQL làm nguồn sự thật và Kafka phân phối event bất đồng bộ. API và worker có thể chạy cùng ứng dụng trong MVP; chỉ tách tiến trình nếu cần kiểm chứng xử lý nền độc lập. Không tạo microservice chỉ để tăng số lượng công nghệ.

| Thành phần | Lựa chọn | Vai trò |
|---|---|---|
| Backend | Java LTS + Spring Boot | REST API, nghiệp vụ, bảo mật, worker |
| Database | PostgreSQL | booking, inventory, payment demo, outbox/inbox |
| Migration | Flyway | Schema có phiên bản |
| Broker | Apache Kafka | Event bất đồng bộ, key theo booking ID |
| Cache/tiện ích | Redis | Chỉ cho use case phụ rõ như rate limit/cache; không giữ quyền sở hữu vé |
| Frontend | React + TypeScript | UI customer/admin tối giản |
| Đóng gói | Docker Compose | Local tái lập |
| API docs | OpenAPI | Khám phá/thử API |

Version đã chốt tại T1.1 trong [ADR 0001](adr/0001-stack-and-local-environment.md): JDK 25.0.5+8 LTS / Spring Boot 4.1.2, PostgreSQL 17.11, Kafka 4.2.2, Node 26.13.0, React 19.3.1 / TypeScript 7.0.2 / Vite 8.3.8. ADR ghi migration/JDBC/client theo Boot BOM, tham số và tài nguyên máy. Redis không nằm trong Compose baseline; chỉ thêm qua ADR khi có use case và test cụ thể. PostgreSQL đảm bảo correctness.

## 2. Module

T7 UI/security review theo [ADR0008](adr/0008-demo-ui-and-security-review.md): admin query service đọc snapshot REPEATABLE_READ, frontend persona chỉ chọn token fixture; server giữ quyền. Không đổi transaction ghi inventory/payment/messaging, không thêm migration. TASKS không có T7.5; chỉ triển khai T7.1–T7.4, observability T8 chưa bắt đầu.

Đến Phần6 có catalog/admin/auth demo,bookingGA/SEATED,expiry,payment simulator/shared processor,cancel,ticket/compensation vàUI thật. T6 bổ sung V9:18bảng/13API; Kafka/inbox/audit/recovery/refund worker theo ADR0007. Triển khai/test tại ADR0003–0006 và PROGRESS.

BookingTransactions quản lý ranh giới transaction/retry chung cho command và expiry; controller chỉ chuyển HTTP. CatalogBookingAccess khóa SHARE event/type theo thứ tự admin; GaInventory conditional update hoặc SeatInventory khóa từng UUID rồi giữ tất cả/rollback. Item/history/BookingHeld/key/response cùng commit trước202. ExpiryService mỗi booking một transaction, kiểm tra state/due/payment trước InventoryRelease/history/BookingExpired. Isolation command/expiry READ COMMITTED; GET booking/mine REPEATABLE_READ. UI không tính tổng giá hay quyết định còn vé; payload/key giữ qua network/503/reload.

- Identity & Access: principal demo/seed, role CUSTOMER/ADMIN.
- Event Catalog: sự kiện, mở bán, địa điểm, khu vực/giá.
- Inventory: ghế và sức chứa GA; giữ, xác nhận, nhả.
- Booking: idempotency, state machine, lịch sử.
- Expiry Worker: quét booking đến hạn, nhả hold có điều kiện.
- Payment Simulator: kết quả success/failure/delay.
- Ticket: phát vé demo sau confirm.
- Messaging: outbox publisher, Kafka consumer, inbox, retry và lỗi cuối.
- Operations: health, metrics, correlation ID và lịch sử.

Các module giao tiếp qua interface hoặc event có hợp đồng. Không truy cập repository module khác nếu chưa ghi rõ hợp đồng.

## 3. Danh tính demo

- Seed sẵn principal customer/admin, mỗi principal có mock bearer token cố định trong cấu hình seed của profile local/test.
- Request dùng Authorization: Bearer token đã seed. Ví dụ local: demo-customer-token và demo-admin-token; đây là fixture công khai, không phải secret.
- Server ánh xạ token hợp lệ sang user ID/role đã seed. Token lạ bị từ chối; API không nhận user ID/role do client tự chọn làm danh tính.
- Mock auth tắt mặc định ngoài local/test; nếu profile khác bật mock auth, ứng dụng fail startup.
- UI chỉ hiển thị persona đang dùng; không hiển thị hoặc log token. Production authentication ngoài MVP.

## 4. Luồng tạo booking

1. POST /api/bookings xác thực principal, validate body và yêu cầu Idempotency-Key.
2. Transaction INSERT idempotency row với unique(account,operation,key) bằng ON CONFLICT DO NOTHING. Request cùng key đang xử lý chờ transaction đầu kết thúc qua unique constraint; sau commit đọc hash/result bằng statement tiếp theo để replay cùng 202/body/bookingId. Payload khác trả 409. Nếu transaction đầu rollback, request sau có thể tiếp tục. Không lưu STARTED bền hoặc conflict inventory terminal; replay sau EXPIRED vẫn trả kết quả tạo đơn ban đầu. Giới hạn thời gian chờ khóa theo cấu hình local transaction và đo tại T1.1/test tải; hết hạn phải rollback trước khi trả lỗi. Lock timeout cũng có thể đến từ ghế/GA, nên không mặc định mọi timeout là IDEMPOTENCY_IN_PROGRESS. Khi chưa phân loại được, trả 503 BOOKING_BUSY với Retry-After, client thử lại cùng key; không có booking/hold dở dang.
3. Sinh booking UUID và insert booking trong transaction trước khi cập nhật seats có FK owner. Với ghế, khóa từng seat theo ID ổn định tăng dần hoặc dùng truy vấn khóa đã kiểm chứng; với GA, cập nhật có điều kiện trên hàng inventory.
4. Chỉ nhận ghế AVAILABLE. Ghế HELD dù hold_expires_at đã qua vẫn HELD cho đến khi expiry transaction giải phóng; booking command không chiếm lại hold cũ.
5. Nếu không lấy đủ inventory, rollback cả booking và inventory.
6. Khi thành công, tạo PENDING_PAYMENT, booking items, payment intent demo, history, idempotency result và outbox trong transaction.
7. Trả 202 Accepted với bookingId và expiresAt.

Nếu Expiry Worker chưa commit việc nhả, API có thể trả 409 HOLD_EXPIRY_PROCESSING hoặc INVENTORY_UNAVAILABLE. Client refresh availability và retry; không ghi đè hold cũ.

## 5. Inventory và concurrency

### Ghế chỉ định

- Ghế có AVAILABLE/HELD/SOLD, held_by_booking_id, hold_expires_at và version.
- Khóa và kiểm tra toàn bộ ghế theo thứ tự ID bất biến trong một transaction; nếu một ghế không khả dụng thì rollback tất cả.
- Không khẳng định SORT ASC ở Java hoặc WHERE id IN (...) tự đảm bảo thứ tự khóa của UPDATE nhiều hàng. Khóa tường minh từng hàng theo seat_id tăng dần hoặc kiểm chứng execution plan/truy vấn khóa theo thứ tự; mọi nhánh giữ cùng thứ tự.
- Deadlock/serialization retry hữu hạn ở ranh giới transaction, cùng idempotency key; không retry vô hạn.
- Constraint và conditional update trong PostgreSQL là lớp bảo vệ cuối; frontend không đảm bảo inventory.

### Vé GA

- Một hàng giữ capacity, held_count, sold_count.
- Tăng held_count bằng conditional update khi held_count + sold_count + quantity <= capacity.
- Confirm chuyển held sang sold nguyên tử; fail/cancel/expiry chỉ giảm held nếu booking sở hữu số lượng đó.
- Mọi lần nhả phải kiểm tra state/owner để không giảm lặp.

## 6. Expiry Worker và nhận lại inventory

Expiry Worker tách biệt với lệnh tạo booking:

Phần5 đặt nền REQUIRES_NEW/booking SKIP LOCKED/DBclock/khóa inventory tăng dần. Phần6 thay defer bằng shared PaymentResultProcessor theo ADR0006: xử lý result bền trước release,maintenance trướcexpiry trên scheduler5s/batch50. Tests tắt lịch tự động và gọi có kiểm soát; live tests bật scheduler thật. State/history/outbox/tickets/compensation cùng transaction xử lý.

1. Chọn batch booking PENDING_PAYMENT đến hạn theo đồng hồ PostgreSQL; worker song song dùng khóa/skip-locked phù hợp.
2. Khóa booking, kiểm tra lại trạng thái, expires_at và payment result đã commit. Nếu có success với payments.recorded_at <= expires_at, dùng PaymentResultProcessor để confirm; không nhả.
3. Khóa inventory liên quan theo ID ổn định; chỉ nhả hold vẫn thuộc booking đó.
4. Trong cùng transaction: chuyển booking sang EXPIRED, lưu expired_at, tăng aggregate_version, ghi booking_status_history và outbox BookingExpired.
5. Commit. Chỉ sau commit, booking khác mới có thể nhận inventory vừa nhả.

Lệnh tạo booking chỉ nhận ghế AVAILABLE hoặc GA còn capacity theo bộ đếm. Nó không chạy cleanup inline và không lấy cắp hold quá hạn. Worker lặp lại phải tạo hiệu ứng nghiệp vụ tương đương một lần.

### Thứ tự khóa

- Payment/expiry với booking tồn tại: khóa booking trước, rồi inventory theo ID ổn định tăng dần.
- Booking mới: khóa idempotency trước, rồi inventory theo ID tăng dần; không khóa booking cũ.
- Nhiều ghế: khóa rõ theo seat_id tăng dần. WHERE id IN (...) không đủ để chứng minh thứ tự.
- Transaction ngắn, không gọi mạng ngoài khi giữ khóa; deadlock chỉ retry hữu hạn.

## 7. Payment, state machine và event muộn

Phần6 triển khai theo [ADR0006](adr/0006-payment-results-and-tickets.md): simulator record+PaymentResultRecorded commit trước local adapter gọi processor. Shared processor chạy cùng transaction expiry/cancel khi cần, recheck booking/result dưới lock. Maintenance ghi delayed result đến hạn và xử lý ledger chưa processed; scheduler hiện có gọi maintenance trước expiry. Không Kafka/inbox/refund worker ở T5. Vé/compensation cùng transaction chuyển state; đọc vé owner/ADMIN. Khi nhiều result pending đã bền, ưu tiên timely success trước failure chưa xử lý, sau đó recorded_at/id; terminal không đảo ngược.

PRD mục 5 là nguồn state machine duy nhất. Payment simulator API local/test ghi payment result và recorded_at bằng DB clock, cùng outbox, dưới khóa booking. Khi result được xử lý, PaymentResultProcessor khóa booking rồi inventory theo ID; tuần 5 gọi processor từ adapter cục bộ, tuần 6 Kafka consumer gọi cùng processor. Worker expiry cũng gọi processor nếu có result đúng hạn bền chưa được xử lý. Không có hai bản logic.

- recorded_at <= expires_at và booking còn PENDING_PAYMENT: chuyển held -> sold, CONFIRMED; phát ticket idempotent.
- Nếu EXPIRED đã commit, success được ghi sau đó: EXPIRED -> REFUND_REQUIRED, không phát vé/nhận lại ghế. Nếu pending nhưng result muộn, expire/release rồi chuyển REFUND_REQUIRED cùng transaction.
- Nếu payment success đúng hạn đã ghi nhưng do lỗi worker trước đó vẫn EXPIRED, không chiếm lại inventory; bù trừ và đánh dấu đối soát. Test bảo đảm worker kiểm tra result trước release.
- PAYMENT_FAILED, CANCELLED, duplicate và kết quả đến sai thứ tự tuân theo bảng PRD mục 5; một payment chỉ có một nghĩa vụ payment_compensations nhờ unique(payment_id), retry cập nhật cùng nghĩa vụ. Booking giữ REFUND_REQUIRED sau khi bù trừ hoàn tất.
- Confirm tạo đúng quantity vé trong cùng transaction chuyển trạng thái: GA có ticket_ordinal 1..N, ghế ordinal 1; unique(booking_item_id,ticket_ordinal) và unique(code). BookingConfirmed là event thông báo đã commit, không phải lệnh phát vé lần nữa.
- Transaction ngắn, DB clock sau khóa, conditional state transition; chỉ nhánh thắng được thay inventory. Một transaction cho một booking, không cho cả batch worker.


### Thứ tự xử lý khi worker và payment cùng đến

~~~mermaid
sequenceDiagram
    participant S as Payment simulator
    participant D as PostgreSQL
    participant W as Expiry Worker
    participant C as Payment processor
    S->>D: Lock booking; ghi result và outbox
    W->>D: Lock booking; kiểm tra result bền
    alt success recorded đúng hạn
        W->>C: Phân xử confirm cùng transaction
    else chưa có success đúng hạn
        W->>D: EXPIRED và nhả hold, commit
    end
    C->>D: Consumer xử lý event lặp an toàn
~~~

## 8. Outbox/inbox và phục hồi

- Outbox được ghi cùng transaction nghiệp vụ.
- Publisher gửi batch, retry lỗi tạm với backoff; event chưa gửi không bị xóa. Có thể phát lặp nếu gửi xong nhưng chưa kịp đánh dấu.
- Consumer at-least-once; inbox có unique event_id. Inbox và hiệu ứng nghiệp vụ commit cùng transaction.
- Kafka key theo bookingId; publisher MVP một instance, gửi theo thứ tự aggregateVersion của từng booking và xác nhận gửi tuần tự trước khi đánh dấu published. Không suy ra thứ tự chỉ từ key hoặc SKIP LOCKED. Mỗi consumer dùng inbox(eventId) và chính sách version theo hợp đồng của mình: PaymentResultRecorded đọc result bền/state dưới khóa booking, không bỏ event vì bookings.aggregate_version đã tăng; projection chỉ kiểm tra gap nếu nhận đủ các event của aggregate. Gap thật không được áp dụng vượt qua, retry hữu hạn rồi vào recovery.
- Lỗi vượt ngưỡng đi vào DLQ/recovery; replay có giới hạn, audit, deduplication và kiểm tra state.
- Không gọi DB + Kafka là exactly-once.

## 9. Dữ liệu và API

Hợp đồng tại OpenAPI/event schema/DATA_MODEL/ADR0002/TRACEABILITY. Đến Phần6 migrate13/14bảng; payment_intent_id nhóm request, payments.id là result, request_id là dedup client. Body/CORS/auth/timeout áp dụng cho booking/payment; replay audit API,inbox,Kafka/refund worker thuộc T6 trở đi. Migration đã chạy giữ nguyên.

Bảng dự kiến: users, events, ticket_types, seats cho ghế định danh, inventory_ga cho vé không ghế, bookings, booking_items, idempotency_keys, payments, payment_compensations, tickets, outbox_events, inbox_events, booking_status_history. Seed mock token bằng cấu hình local/test. Chi tiết PK/FK/check/index ở [DATA_MODEL](DATA_MODEL.md).

- UUID; thời gian UTC/timestamptz; quyết định hết hạn dùng DB clock; tiền VND lưu số nguyên.
- Booking giữ expires_at, expired_at, state và aggregate_version; không xóa dấu vết EXPIRED sau khi chuyển REFUND_REQUIRED.
- API tối thiểu: events/detail/availability, POST bookings, GET booking, GET my-bookings, POST cancel, POST simulate-payment, GET tickets, admin event CRUD/bookings/outbox/replay. Hợp đồng và error code ở [API_CONTRACT](API_CONTRACT.md).
- API được bảo vệ dùng principal từ mock bearer token local/test. Contract phải có trong OpenAPI trước khi frontend phụ thuộc.

## 10. Cấu trúc thư mục đề xuất

backend/src/main/java/.../{identity,events,inventory,bookings,expiry,payments,tickets,messaging,operations,shared}
backend/src/main/resources/{db/migration,application.yml}
backend/src/test/java/...
frontend/src/{app,features,components,api}
infra/docker-compose.yml

Giữ ranh giới module khi scaffold; không dồn domain vào một bộ controller/service/repository chung.

## 11. Triển khai và quan sát

Mục tiêu Docker Compose gồm frontend/backend/PostgreSQL/Kafka và tiện ích đã chốt T1.1. Local hiện chạy PostgreSQL/Kafka bằng Compose, backend JDK và frontend Vite chạy host; cổng demo bind127.0.0.1. Có health check, migration rõ thứ tự và seed riêng. Replay/simulator đã triển khai ở các phần trước; log/health T8.1 và metrics T8.2 bên dưới. Không log token hoặc bí mật.


Event producer/consumer và failure policy: [EVENT_CATALOG](EVENT_CATALOG.md). Test và recovery: [TEST_PLAN](TEST_PLAN.md), [DEMO_AND_RECOVERY](DEMO_AND_RECOVERY.md).

## T6 — Ranh giới Kafka và PostgreSQL

[ADR0007](adr/0007-kafka-and-recovery.md) chốt publisher session-leader và gửi/ACK ngoài transaction nghiệp vụ, inbox+business commit trước offset, hai group payment/audit, retry tại partition, durable failure trước ACK rồi DLQ sau commit. `MESSAGING_ENABLED=true` local mặc định: simulator trả202 sau ghi result/outbox, consumer xử lý bất đồng bộ. Expiry vẫn đọc ledger bền dưới booking lock. Khi Kafka offline có thể chờ consumer hoặc expiry; không suy HTTP202 là đã có vé.

RefundRecoveryService chỉ local/test, không gọi mạng/tiền thật; pending/failed nghĩa vụ được khóa và attempt/history/status commit nguyên tử, tối đa3lần. No-op khi COMPLETED. Audit projection không phát vé hoặc điều khiển inventory.

## T8.1 — Log và health

Log console khi ứng dụng chạy là JSON Logstash của Spring Boot ([hỗ trợ MDC/key-value chính thức](https://docs.spring.io/spring-boot/reference/features/logging.html)). `logback.xml` đặt mức bootstrap INFO trước khi đọc YAML; sau khi có Spring Environment, `logging.config` chuyển sang `logback-spring.xml` dùng JSON encoder. Không khởi tạo JSON encoder cần Environment ở bootstrap. Tắt riêng logger dump YAML để không lộ giá trị cấu hình kể cả khi logger cha bật DEBUG; không tắt log nghiệp vụ/lỗi. Các dòng bootstrap trước Boot có thể ở dạng text.

Các mốc `http_request`, `outbox_committed`, `outbox_published`, `outbox_publish_failed`, `consumer_processed`, `consumer_failed`, `consumer_rejected`, `consumer_parked` chỉ ghi metadata được chọn: correlationId, bookingId, eventId (ID của message, không phải ID sự kiện bán vé), stage/outcome, HTTP route mẫu/status và consumer. Không ghi raw URI/query, header, token, idempotency key, body, payload payment, exception message hoặc stack trace ở các mốc này. UUID từ client được kiểm tra trước khi dùng; ngữ cảnh MDC được phục hồi trong finally để không lẫn request/message trên thread dùng lại. HTTP tạo booking có cả ba ID sau commit; API booking khác ghi bookingId khi route có UUID hợp lệ, correlationId nối với event ID trong log outbox. Request không liên quan booking không gắn ID giả.

Outbox committed chỉ log sau COMMIT; rollback không được báo thành công. Publisher dùng ID từ outbox bền, consumer dùng envelope đã validate; log consumer thành công sau inbox/nghiệp vụ commit, duplicate là outcome riêng. Không thay payload event hoặc ranh giới giao dịch. HTTP idempotency replay có correlation của request mới và tham chiếu event gốc; không sửa correlation bền của event cũ.

`/actuator/health` là tổng hợp; `/actuator/health/liveness` chỉ phản ánh trạng thái tiến trình; `/actuator/health/readiness` gồm readinessState, PostgreSQL và Kafka khi messaging bật. Kafka lỗi làm tổng hợp/readiness trả503 DOWN nhưng liveness vẫn200 UP; messaging tắt thì không yêu cầu broker. Probe chỉ trả status, không công khai chi tiết cấu hình/lỗi/cluster. DB acquisition dùng timeout pool2s + validation1s; Kafka probe có timeout2s. Đây không phải kiểm tra backlog, độ trễ publisher, consumer đang tiến triển hay khả năng ghi mọi partition; các metric thuộc T8.2, chưa triển khai trong T8.1.

## T8.2 — Hợp đồng metrics

Dùng [Micrometer/Actuator](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) đã có trong stack, không thêm registry/dịch vụ. GET `/actuator/metrics` và `/actuator/metrics/{name}` chỉ mở read-only ở local/test, yêu cầu ADMIN; profile khác mặc định access NONE, backing bean có thể vẫn tồn tại. Không đưa UUID, correlation, account, token, idempotency key, raw URI/query/payload vào label. Các tag tự thêm đều có tập giá trị cố định. Consumer attempt dùng [header do Spring Kafka container tạo](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#delivery-attempts-header); không đếm network retry bên trong Kafka producer.

| Tên Micrometer | Loại / đơn vị | Label và cách đo |
|---|---|---|
| `ticketing.bookings` | Gauge / bookings | `status`:6state PRD, số booking đã commit hiện tại |
| `ticketing.holds.overdue` | Gauge / bookings | Không label; PENDING_PAYMENT có expires_at <= DB statement_timestamp; đếm booking giữ chỗ, không đếm vé/ghế |
| `ticketing.holds.overdue.oldest.age` | Gauge / seconds | Tuổi quá hạn lớn nhất = now-min(expires_at), rỗng=0 |
| `ticketing.outbox.messages` | Gauge / messages | `status`:PENDING/PUBLISHED/FAILED |
| `ticketing.outbox.unpublished.oldest.age` | Gauge / seconds | now-min(created_at) cho PENDING **hoặc FAILED**, kể cả event bị chặn version; rỗng=0, không âm |
| `ticketing.outbox.attempts` | Gauge / attempts | Tổng attempts đang lưu, có thể giảm khi replay reset; không gọi là counter retry bền |
| `ticketing.messaging.failures` | Gauge / messages | `consumer`:payment-v1/booking-audit-v1, `status`:DEAD/RESOLVED; từ recovery DB |
| `ticketing.messaging.dlq.messages` | Gauge / messages | `state`:PENDING/PUBLISHED/EXHAUSTED; dlq_published_at có giá trị thì PUBLISHED, chưa gửi đủ3attempt thì EXHAUSTED, còn lại PENDING; bao gồm cả recovery đã RESOLVED |
| `ticketing.messaging.retries` | Counter / retries | `stage`:outbox/consumer/dlq, `consumer`:none/payment-v1/booking-audit-v1; đếm **delivery bắt đầu sau lần đầu**. Outbox/DLQ dựa attempts trước send; consumer dựa header deliveryAttempt do container tạo. Duplicate/new replay lần đầu không tự tính là retry |
| `ticketing.messaging.dlq.deliveries` | Counter / deliveries | `outcome`:ACKNOWLEDGED/FAILED; số lần send DLQ có/không ACK, không phải số message unique |
| `ticketing.metrics.snapshot.available` | Gauge / boolean | 1 nếu snapshot DB mới nhất đọc được, 0 nếu lỗi |
| `ticketing.metrics.snapshot.age` | Gauge / seconds | Thời gian JVM từ snapshot thành công gần nhất, NaN nếu chưa có |
| `http.server.requests` | Timer / seconds | Timer Spring MVC sẵn có: COUNT, TOTAL_TIME, MAX; uri là route mẫu/nhãn fallback, method/status/outcome/error có giới hạn. Không dùng URI thực chứa ID. Chưa bật histogram/p95 |

Snapshot DB là một SQL read-only dùng cùng đồng hồ/snapshot PostgreSQL, không gắn vào scheduler expiry. Cache tối đa1s mặc định (`operations.metrics.snapshot-ttl-ms`,0..5000ms), statement timeout2s/pool acquisition2s. Khi lỗi, các gauge nghiệp vụ trảNaN và available=0; không giả làm0 hoặc giữ giá trị cũ như mới. Snapshot age cho biết độ cũ, không phải tuổi hold/outbox. DB gauges phản ánh hiện trạng, không phải tổng số chuyển trạng thái từ khi chạy. Counter/timer là process-local, reset khi JVM restart; dùng gauges/recovery DB để đối soát bền. Một scrape nhiều endpoint có thể cắt qua các snapshot/cache khác nhau, không là transaction toàn bộ HTTP scrape. Không thay giao dịch booking, expiry, publisher/inbox hoặc retry budget.
