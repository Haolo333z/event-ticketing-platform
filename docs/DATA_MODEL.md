# DATA_MODEL — Thiết kế dữ liệu PostgreSQL

> EventFlow V2 đã hoàn thiện chuỗi 15 Flyway Migrations (V1–V15) với tổng cộng **35 bảng nghiệp vụ** trong schema `ticketing`. PostgreSQL 17 là nguồn sự thật (Single Source of Truth) chuẩn ACID; Kafka 4.2 phân phối sự kiện sau commit. PRD là nguồn nghiệp vụ, ARCHITECTURE là nguồn transaction boundary.

## Phân chia tồn kho

- Ghế định danh: một hàng seats cho một ghế; status AVAILABLE, HELD, SOLD và held_by_booking_id. Không cộng ghế vào bộ đếm GA.
- Vé GA: một hàng inventory_ga cho mỗi event/ticket_type, có capacity, held_count, sold_count.
- booking_items lưu snapshot ticket_type_id, seat_id hoặc quantity, unit_price_vnd và total_price_vnd tại lúc đặt. Đổi giá sau đó không sửa đơn cũ.
- Trạng thái booking là nguồn sự thật của vòng đời; seat/GA chứa hiệu ứng giữ/bán được đối chiếu theo booking_item.

## Quan hệ chính (V2 35 Bảng Nghiệp Vụ)

~~~mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : defines
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ EXTERNAL_IDENTITIES : authenticates
    USERS ||--o| ORGANIZER_PROFILES : registers
    ORGANIZER_PROFILES ||--o{ EVENTS : owns
    VENUES ||--o{ EVENTS : hosts
    EVENT_CATEGORIES ||--o{ EVENTS : classifies
    EVENTS ||--o{ EVENT_MEDIA : includes
    USERS ||--o{ EVENT_STAFF_ASSIGNMENTS : assigned
    EVENTS ||--o{ EVENT_STAFF_ASSIGNMENTS : staffs
    USERS ||--o{ BOOKINGS : places
    EVENTS ||--o{ BOOKINGS : receives
    EVENTS ||--o{ TICKET_TYPES : offers
    EVENTS ||--o{ SEATS : contains
    TICKET_TYPES ||--o{ SEATS : classifies
    TICKET_TYPES ||--o| INVENTORY_GA : has
    BOOKINGS ||--|{ BOOKING_ITEMS : contains
    TICKET_TYPES ||--o{ BOOKING_ITEMS : selected
    SEATS o|--o{ BOOKING_ITEMS : historical_selection
    BOOKINGS o|--o{ SEATS : current_owner
    USERS ||--o{ IDEMPOTENCY_KEYS : scopes
    BOOKINGS ||--o| IDEMPOTENCY_KEYS : result
    BOOKINGS ||--o{ PAYMENTS : records
    BOOKINGS ||--o{ PAYMENT_ATTEMPTS : initiates
    PAYMENTS ||--o| PAYMENT_NOTIFICATIONS : records_ipn
    BOOKINGS ||--o{ REFUND_ATTEMPTS : requests
    BOOKING_ITEMS ||--o{ TICKETS : issues
    BOOKINGS ||--o{ TICKETS : owns
    TICKETS ||--o{ TICKET_CHECK_INS : checks_in
    BOOKINGS ||--o{ OUTBOX_EVENTS : emits
    OUTBOX_EVENTS ||--o{ INBOX_EVENTS : deduplicates_logically
    BOOKINGS ||--o{ BOOKING_STATUS_HISTORY : transitions
    BOOKINGS ||--o{ EMAIL_NOTIFICATIONS : receipts
    USERS ||--o{ AUDIT_LOGS : audits
    CONTACT_LEADS ||--o{ AUDIT_LOGS : audited_by
~~~

## Bảng, khóa và ràng buộc dự kiến

| Bảng | Cột chính | Ràng buộc và index |
|---|---|---|
| users | id UUID, demo_role, display_name | PK id; role CUSTOMER/ADMIN; chỉ seed local/test |
| events | id, title, sale_start_at, sale_end_at, starts_at, status | PK; sale_start < sale_end; index status/sale_start |
| ticket_types | id, event_id, mode SEATED/GA, price_vnd, active | FK event; price_vnd >= 0 |
| seats | id, event_id, ticket_type_id, label, status, held_by_booking_id, hold_expires_at | unique(event_id,label); FK booking có thể NULL; check trạng thái/owner/deadline; index status/hold_expires_at |
| inventory_ga | ticket_type_id, capacity, held_count, sold_count | PK/FK type; check capacity >= 0, counts >= 0 và held + sold <= capacity |
| bookings | id, account_id, event_id, status, expires_at, expired_at, total_amount_vnd, aggregate_version | FK user/event; amount >= 0; index(status,expires_at) |
| booking_items | id, booking_id, ticket_type_id, seat_id NULL, quantity, unit_price_vnd, total_price_vnd | FK; unique(seat_id) không áp dụng vì ghế được mua trong nhiều đơn thất bại/hết hạn; kiểm tra mode trong domain |
| idempotency_keys | account_id, operation, key, request_hash, booking_id, response_status, response_body, created_at | PK(account_id,operation,key); booking_id unique khi có; không lưu STARTED bền trong MVP |
| payments | id, booking_id, request_id, scenario, status, result_due_at, recorded_at, processed_at | unique(booking_id, request_id); id là paymentResultId trong event; recorded_at dùng DB clock, NULL khi SCHEDULED; không lưu thẻ |
| tickets | id, booking_id, booking_item_id, ticket_ordinal, code | FK booking/booking_item; check ticket_ordinal >= 1; unique(booking_item_id,ticket_ordinal); unique(code) |
| payment_compensations | id, booking_id, payment_id, status, reason, created_at, processed_at | FK booking/payment; unique(payment_id); status PENDING/COMPLETED/FAILED; retry cùng nghĩa vụ |
| outbox_events | id, aggregate_id, aggregate_version, type, schema_version, payload, status, created_at, published_at | unique(aggregate_id,aggregate_version); index pending created_at |
| inbox_events | consumer, event_id, aggregate_id, aggregate_version, envelope_hash, processed_at | PK(consumer,event_id) |
| booking_status_history | id, booking_id, from_status, to_status, occurred_at, cause_event_id | index(booking_id,occurred_at,id) |

Schema cụ thể cần migration và thử trên PostgreSQL. Không gắn FK đến booking trước khi booking row tồn tại: sinh UUID và insert booking PENDING_PAYMENT trước khi cập nhật seats. Trong một transaction, nếu giữ thất bại thì rollback cả booking, idempotency, inventory và outbox. Không dùng DEFERRABLE FK nếu cách insert này đã đủ.

### Check constraint minh họa

    CHECK (capacity >= 0 AND held_count >= 0 AND sold_count >= 0
           AND held_count + sold_count <= capacity)

    CHECK (
      (status = 'AVAILABLE' AND held_by_booking_id IS NULL AND hold_expires_at IS NULL)
      OR (status = 'HELD' AND held_by_booking_id IS NOT NULL AND hold_expires_at IS NOT NULL)
      OR (status = 'SOLD' AND held_by_booking_id IS NOT NULL AND hold_expires_at IS NULL)
    )

Check constraint bảo vệ hàng GA nhưng không thay thế conditional UPDATE và transaction. Với seat, FK owner/condition và lock bảo đảm một owner hiện hành; lịch sử ở booking_items.

### Vé và nghĩa vụ bù trừ

- Mỗi vé là một hàng tickets. Confirm một booking_item GA quantity N tạo đúng N hàng với ticket_ordinal từ 1..N; booking_item ghế có quantity = 1 và ticket_ordinal = 1. Không phát vé khi booking EXPIRED/REFUND_REQUIRED. Cùng transaction confirm/issue ticket, kiểm tra state và quantity; unique(booking_item_id,ticket_ordinal) và unique(code) chống phát trùng khi retry. Kiểm tra booking_id trên ticket khớp booking_id của booking_item trong domain/test hoặc bằng ràng buộc phù hợp trong migration.
- Một payment thành công cần bù trừ chỉ có một payment_compensations row, unique(payment_id). Retry cập nhật cùng nghĩa vụ PENDING/FAILED và ghi kết quả; COMPLETED là kết quả cuối, không hoàn lại lần hai. Nếu cần lịch sử từng attempt, thêm bảng/nhật ký attempt ở giai đoạn triển khai, không tạo nhiều nghĩa vụ cho một payment. `REFUND_REQUIRED` là trạng thái booking; trạng thái compensation mô tả tiến độ hoàn tiền mô phỏng, không tự chuyển booking sang CONFIRMED.

## Transaction và isolation

- Mặc định READ COMMITTED; mọi quyết định có tranh chấp lấy row lock, kiểm tra lại sau khóa và cập nhật có điều kiện. Không dùng đọc availability để quyết định bán.
- Tạo booking: reserve idempotency key trong transaction bằng INSERT ... ON CONFLICT DO NOTHING; request trùng chờ unique conflict được giải quyết, rồi đọc kết quả commit bằng statement kế tiếp để replay. Sinh booking UUID; insert booking trước, lock seat theo ID tăng dần bằng từng lock hoặc truy vấn được chứng minh, rồi hold và ghi outbox. Nếu hết thời gian chờ khóa, rollback trước khi trả lỗi; chỉ báo IDEMPOTENCY_IN_PROGRESS nếu đã xác định đúng key đang tranh chấp, không gán mọi lock timeout cho idempotency.
- Expiry: một transaction cho mỗi booking; khóa booking trước, kiểm tra due/state và payment result bền trước khi nhả; lock các seat theo ID. Nếu chuyển EXPIRED thành công, nhả toàn bộ hold đang thuộc booking, history/outbox cùng commit.
- Payment result: khóa booking trước, ghi result/recorded_at từ DB; handler có thể nhận event lặp. Nếu result bền trước deadline, expiry worker phải phân xử result đó trước khi nhả; consumer và worker dùng cùng processor. Sau khi đã commit EXPIRED và inventory được tái cấp, success muộn đi REFUND_REQUIRED, tuyệt đối không confirm lại.

## Migration và seed

V1 schema, V2 catalog/booking nền, V3 payments/outbox, V4 check JSON bookingId, V5 idempotency/history, V6 initial history, V7 composite seat owner FK. V8 tickets/compensations/correlation payment; V9 inbox/projection/recovery/refund history. Seed persona/sự kiện nằm ngoài migration, chỉ local/test. Migration đã chạy không sửa lại.

V5 cho booking_id/response_status/response_body NULL trong transaction đang reserve key. Deferred constraint trigger kiểm tra row cuối tại COMMIT: bắt buộc booking và response202/PENDING_PAYMENT với bookingId khớp. Vì vậy không có row STARTED hoặc response chưa hoàn chỉnh được commit. Hash GA chuẩn hóa UUID lowercase và quantity nguyên (2 và 2.0 cùng nội dung), giữ tương thích Phần4. Hash SEATED có prefix và danh sách UUID sắp thứ tự, đảo thứ tự ghế replay cùng key. V7 bổ sung FK seats(held_by_booking_id,event_id,ticket_type_id)→bookings(id,event_id,ticket_type_id), bảo vệ owner cùng event/type. Xem ADR0004/0005. Không có unique seat_id xuyên lịch sử vì ghế có thể đặt lại sau expiry.

## Dữ liệu giữ lại

V8: tickets FK(booking_item_id,booking_id),unique(item,ordinal)/code,ordinal1..6; domain bảo đảm ordinal<=quantity và chỉCONFIRMED mới issue cùng transaction state/inventory/history/outbox. SeatId/seatLabel của response lấy join booking_item/seats, không tạo allocation khác. payment_compensations FK(payment_id,booking_id),unique(payment_id),statusPENDING/COMPLETED/FAILED và attempt_count; T5 chỉ tạoPENDING,không refund worker. Payments correlation_id NOTNULL/defaultUUID cho legacy fixture,index terminal chưaprocessed hỗ trợmaintenance. Không sửa V1–V7.

MVP không xóa idempotency key/outbox/inbox trong 10 tuần để replay an toàn và dễ audit. Khi vận hành lâu dài cần chính sách retention dựa trên thời hạn khiếu nại, bản sao lưu và version, chốt bằng ADR trước khi bật cleanup. Không coi chỉ mục hoặc check constraint là bằng chứng đủ cho concurrency nếu chưa chạy test.

## Chốt mô hình v0 tại T1.4

Đây là đặc tả mục tiêu;13bảng đã chạy,1bảng inbox còn lại chưa migrate. Tất cả bảng nghiệp vụ ở ticketing,history Flyway ởpublic. UUIDserver sinh,tiền bigint VND0..10^12,thời gian timestamptzUTC. FK mặc định RESTRICT/NO ACTION,không cascade lịch sử. Ba cột idempotency có NULL tạm nhưng guard tạiCOMMIT; created_at/recorded_at do DB,không nhận timestamp client.

| Bảng | Bổ sung kiểu/cột và ràng buộc đã chốt |
|---|---|
| users | demo_role varchar(16) enum CUSTOMER/ADMIN, display_name varchar(100), created_at; token không lưu trong bảng, seed cấu hình profile |
| events | title varchar(200), description varchar(2000), venue varchar(200), sale_start_at/sale_end_at/starts_at timestamptz; status DRAFT/PUBLISHED/CLOSED; created_at; CHECK sale_start_at < sale_end_at <= starts_at |
| ticket_types | event_id UUID FK events; name varchar(100), mode GA/SEATED bất biến sau booking; price_vnd bigint, active boolean; UNIQUE(id,event_id) cho FK tổng hợp |
| seats | event_id/ticket_type_id UUID, label varchar(40), status AVAILABLE/HELD/SOLD, held_by_booking_id UUID NULL, hold_expires_at timestamptz NULL, version bigint >=0; FK(ticket_type_id,event_id) → ticket_types(id,event_id); UNIQUE(id,event_id,ticket_type_id); UNIQUE(event_id,label). Owner phải booking cùng event/type, kiểm tra dưới lock trong domain; state/owner/deadline CHECK như trên |
| inventory_ga | ticket_type_id UUID PK/FK; capacity/held_count/sold_count integer; check counts và tổng <=capacity. Chỉ type GA có row, SEATED không có; enforce mode bằng domain dưới lock/test, không bằng CHECK cross-table |
| bookings | account_id FK users, event_id FK events, ticket_type_id UUID; FK(ticket_type_id,event_id) → ticket_types; UNIQUE(id,event_id,ticket_type_id). payment_intent_id UUID UNIQUE, created_at, expires_at, expired_at NULL, total_amount_vnd bigint, aggregate_version bigint >=1; status đúng 6 state PRD; expired_at bắt buộc cho EXPIRED/REFUND_REQUIRED, không xóa sau refund; expires_at>created_at |
| booking_items | booking_id/event_id/ticket_type_id UUID; FK bộ ba → bookings; FK(ticket_type_id,event_id) → ticket_types; seat_id UUID NULL; FK(seat_id,event_id,ticket_type_id) → seats; quantity integer 1..6, giá bigint; CHECK total_price_vnd=quantity*unit_price_vnd; CHECK seat_id IS NULL OR quantity=1; UNIQUE(booking_id,seat_id) (NULL cho GA), UNIQUE(id,booking_id). Một GA item/booking dùng partial unique booking_id WHERE seat_id IS NULL. Không unique seat_id xuyên lịch sử |
| idempotency_keys | PK(account_id UUID FK users,operation varchar(64),key varchar(128)); request_hash char(64) SHA-256; booking_id UUID UNIQUE/FK bookings; response_status integer=202, response_body jsonb, created_at. Row chỉ commit khi booking commit, không row STARTED độc lập; account_id phải khớp booking trong domain. Scope operation=CREATE_BOOKING; canonical hash sort seatIds để đảo thứ tự ghế không bị coi payload khác |
| payments | id UUID là kết quả/attempt, booking_id FK bookings; request_id UUID client dedup, scenario SUCCESS/FAILURE/DELAY_TIMEOUT; status SCHEDULED/SUCCESS/FAILURE; created_at, result_due_at timestamptz NULL, recorded_at timestamptz NULL, processed_at timestamptz NULL; UNIQUE(booking_id,request_id), UNIQUE(id,booking_id). CHECK SCHEDULED ⇒ recorded_at IS NULL và due_at có giá trị; SUCCESS/FAILURE ⇒ recorded_at có giá trị. DELAY_TIMEOUT ghi SUCCESS khi due, không lấy timestamp client. paymentIntentId trả từ bookings; không coi request_id là paymentResultId |
| tickets | booking_id FK bookings, booking_item_id UUID; FK(booking_item_id,booking_id) → booking_items(id,booking_id); ticket_ordinal integer 1..6, code varchar(128), created_at; UNIQUE(item,ordinal), UNIQUE(code). Ordinal<=item.quantity và chỉ CONFIRMED enforce domain trong transaction confirm |
| payment_compensations | booking_id UUID FK bookings, payment_id UUID UNIQUE; FK(payment_id,booking_id) → payments(id,booking_id); status PENDING/COMPLETED/FAILED, reason varchar(500), created_at, processed_at NULL; attempt_count integer >=0. Chỉ result SUCCESS tạo nghĩa vụ; retry cùng row |
| outbox_events | id UUID=eventId; aggregate_id FK bookings, aggregate_version bigint>=1, type enum 7 event, schema_version integer=1, payload jsonb, correlation_id UUID, created_at=occurredAt, status PENDING/PUBLISHED/FAILED, published_at NULL, attempts integer>=0, next_attempt_at NULL, last_error varchar(500) NULL. UNIQUE(aggregate_id,aggregate_version). replay_requests jsonb array tối đa3 audit entries; mỗi entry có requestId,adminAccountId,reason,requestedAt; append dưới row lock, domain dedup requestId |
| inbox_events | PK(consumer varchar(100),event_id UUID); aggregate_id/version, envelope_hash, processed_at. event_id liên kết logic tới outbox.id, **không FK** vì retention độc lập; domain/business commit cùng inbox |
| booking_status_history | id UUID, booking_id FK bookings, from_status NULL hoặc enum 6 state, to_status enum 6 state, occurred_at, cause_event_id UUID NULL (logical reference, không FK); initial NULL→PENDING_PAYMENT; transitions khác đúng PRD §5, không thêm transition REFUND_REQUIRED→CONFIRMED |

Quan hệ outbox→inbox và cause_event_id trên ER là quan hệ logic, không yêu cầu SQL FK. Không tạo bảng projection/buffer chưa có use case. Bảng booking history giữ hai bước EXPIRED và REFUND_REQUIRED nếu late result xử lý cùng transaction. Khi success sau CANCELLED/PAYMENT_FAILED, giữ nguyên state và tạo compensation, không tự chuyển REFUND_REQUIRED (chỉ EXPIRED có cạnh đó).

Index chốt: index mọi FK chưa được bao phủ bởi prefix PK/unique (account/event/type/booking/payment/item); bookings(status,expires_at,id) partial WHERE status='PENDING_PAYMENT'; bookings(account_id,created_at DESC,id DESC); seats(event_id,ticket_type_id,status,id), seats(hold_expires_at) partial HELD; outbox(status,next_attempt_at,created_at,id) partial PENDING/FAILED; payments(booking_id,recorded_at), payments(result_due_at,id) partial SCHEDULED; history(booking_id,occurred_at,id). Không tạo unique inbox(event_id) toàn cục vì mỗi consumer có dedup riêng.

Schema không thể tự bảo đảm tổng item, số vé theo quantity, mode inventory hay transition cross-table. Các invariant này cần domain transaction và test GA/SEAT/EXP/PAY/TICKET/COMP trong TEST_PLAN. Toàn bộ event và HTTP ID mapping chốt tại [ADR 0002](adr/0002-api-domain-contract.md).

## V9 — T6 inbox và recovery

V1–V8 giữ nguyên. V9 chuẩn hóa next_attempt_at của outbox cũ (NULL → created_at) và default DB clock cho event mới; thêm 5 bảng, tổng 18 bảng nghiệp vụ:

- inbox_events: PK(consumer,event_id), hash SHA256 envelope, aggregate/version và thời điểm commit; không FK outbox để retention độc lập.
- event_projections: PK aggregate_id, last_applied_version của consumer audit nhận đủ 7 type.
- projection_events: PK(aggregate_id,aggregate_version), event_id unique; audit chỉ áp dụng version liên tiếp, không có side effect vé/inventory.
- message_failures: unique(consumer,topic,partition_id,record_offset), raw record/key, attempts=3, DEAD/RESOLVED, event_id nullable cho poison record và trạng thái gửi DLQ hữu hạn.
- compensation_attempts: FK compensation, PK(compensation_id,attempt), outcome FAILED/COMPLETED. payment_compensations thêm next_attempt_at; attempt_count tối đa3 do worker kiểm soát, unique(payment_id) vẫn bảo vệ một nghĩa vụ.

## V10 đến V15 — Nâng Cấp Toàn Diện EventFlow V2 (35 Bảng Nghiệp Vụ)

Chuỗi Migration V10–V15 hoàn tất mở rộng từ 18 bảng lên đủ đúng **35 bảng nghiệp vụ** trong schema `ticketing`:

### 1. Phân hệ Định danh & 4-Role RBAC (Flyway V10 - 6 bảng)
- `roles`: PK `id VARCHAR(32)` (`CUSTOMER`, `STAFF`, `ORGANIZER`, `ADMIN`), description, created_at.
- `users`: PK `id UUID DEFAULT gen_random_uuid()`, email UNIQUE, password_hash, display_name, phone, status (`ACTIVE`, `LOCKED`), created_at, updated_at.
- `user_roles`: Composite PK `(user_id, role_id)`, FK `users(id)`, FK `roles(id)`, granted_by, granted_at.
- `refresh_tokens`: PK `id UUID`, user_id, token_hash VARCHAR(64) UNIQUE, token_family UUID, is_revoked, expires_at, created_at (hỗ trợ Token Family Reuse Detection).
- `password_reset_tokens`: PK `id UUID`, user_id, token_hash UNIQUE, expires_at, used_at, created_at.
- `external_identities`: Composite PK `(provider, provider_subject)`, user_id FK, email, email_verified, linked_at (hỗ trợ Google OIDC).

### 2. Phân hệ Quản trị Nhà tổ chức & Địa điểm (Flyway V11 & V12 - 5 bảng)
- `organizer_profiles`: PK `id UUID`, user_id UNIQUE FK `users(id)`, organization_name, contact_email, phone, tax_code, status (`PENDING`, `APPROVED`, `REJECTED`), verified_at, created_at, updated_at.
- `venues`: PK `id UUID`, name, address, city, country, capacity, seating_layout_json (lưu trữ cấu hình tọa độ ghế, sân khấu), created_at.
- `event_categories`: PK `id UUID`, name UNIQUE, description, icon_url, display_order, is_active.
- `event_media`: PK `id UUID`, event_id FK `events(id)`, media_type (`POSTER`, `BANNER`, `GALLERY`), media_url, display_order, created_at.
- `event_staff_assignments`: Composite PK `(event_id, staff_user_id)`, assigned_by, assigned_at, revoked_at (quản lý phạm vi nhân sự soát vé theo sự kiện).

### 3. Cổng thanh toán Sandbox bên ngoài & Hoàn tiền (Flyway V13 - 3 bảng)
- `payment_attempts`: PK `id UUID`, booking_id FK `bookings(id)`, provider (`VNPAY`, `INTERNAL_SIMULATOR`), merchant_ref UNIQUE, amount_vnd, currency, status (`INITIATED`, `PENDING_GATEWAY`, `SUCCESS`, `FAILED`, `EXPIRED`), gateway_url, expires_at, created_at.
- `payment_notifications`: PK `id UUID`, provider, notification_ref, booking_id FK, payload_json, signature, is_valid_signature, processed_status (`SUCCESS`, `IGNORED_DUPLICATE`, `FAILED`, `LATE_PAYMENT_REFUND`), error_message, received_at (IPN journal bảo đảm 100% idempotent).
- `refund_attempts`: PK `id UUID`, booking_id FK `bookings(id)`, payment_id, amount_vnd, reason (`LATE_PAYMENT_AFTER_EXPIRY`, `ADMIN_MANUAL_REFUND`, `CUSTOMER_CANCELLATION`), status (`REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`), settled_at, created_at.

### 4. Soát vé Check-in Nguyên tử & Chống Quét Trùng (Flyway V14 - 1 bảng)
- `ticket_check_ins`: PK `id UUID`, ticket_id UNIQUE FK `tickets(id)`, event_id FK `events(id)`, staff_user_id FK `users(id)`, gate_name VARCHAR(64), device_info TEXT, checked_in_at TIMESTAMPTZ (ràng buộc UNIQUE ticket_id ngăn chặn triệt để double check-in).

### 5. Quản trị Doanh nghiệp, Báo cáo & Hóa đơn (Flyway V15 - 2 bảng + Audit Log)
- `contact_leads`: PK `id UUID`, full_name, email, phone, company_name, message, status (`NEW`, `CONTACTED`, `CONVERTED`, `CLOSED`), notes, created_at, updated_at.
- `email_notifications`: PK `id UUID`, booking_id FK, recipient_email, subject, template_type (`BOOKING_CONFIRMED_RECEIPT`, `PAYMENT_REFUND_APPROVED`), payload_json, status (`SENT`, `PENDING`, `FAILED`), sent_at (nhật ký biên nhận hóa đơn điện tử).
- `audit_logs`: PK `id UUID`, actor_id, actor_role, action, target_type, target_id, details_json, ip_address, created_at.

## Tổng Kết Danh Mục 35 Bảng Nghiệp Vụ V2

| STT | Tên Bảng | Migration | Mục Đích Nghiệp Vụ |
|:---:|---|:---:|---|
| 1 | `users` | V10 | Người dùng nền tảng (Customer, Staff, Organizer, Admin) |
| 2 | `roles` | V10 | Danh mục 4 vai trò chuẩn hóa RBAC |
| 3 | `user_roles` | V10 | Bảng liên kết phân quyền đa vai trò cho người dùng |
| 4 | `refresh_tokens` | V10 | Token gia hạn phiên an toàn (Token Family Reuse Detection) |
| 5 | `password_reset_tokens` | V10 | Token phục hồi mật khẩu bảo mật |
| 6 | `external_identities` | V10 | Định danh liên kết OAuth2 / Google OIDC |
| 7 | `organizer_profiles` | V11 | Hồ sơ pháp nhân & thông tin Nhà tổ chức |
| 8 | `venues` | V11 | Quản lý địa điểm tổ chức sự kiện & sơ đồ rạp |
| 9 | `event_categories` | V11 | Danh mục phân loại sự kiện (Concert, Theater, Conference, Sports) |
| 10 | `event_media` | V11 | Quản lý poster, banner và hình ảnh sự kiện |
| 11 | `events` | V1, V7, V11 | Vòng đời sự kiện (Draft, Published, Closed, Cancelled) |
| 12 | `event_staff_assignments` | V12 | Phân công nhân sự soát vé theo từng sự kiện |
| 13 | `ticket_types` | V1 | Cấu hình hạng vé (GA hoặc Ghế chỉ định) |
| 14 | `inventory_ga` | V1 | Quản lý sức chứa vé GA nguyên tử chống bán lố |
| 15 | `seats` | V1, V2, V7 | Sơ đồ ghế ngồi khán phòng và trạng thái giữ/bán |
| 16 | `bookings` | V1, V3, V7 | Đơn đặt vé người dùng và trạng thái thanh toán |
| 17 | `booking_items` | V1 | Chi tiết từng hạng mục đặt vé trong đơn hàng |
| 18 | `booking_status_history` | V5, V6 | Lịch sử chuyển dịch trạng thái đơn hàng |
| 19 | `idempotency_keys` | V5 | Khóa chống lặp yêu cầu đặt vé (Zero Duplicate Booking) |
| 20 | `payments` | V1, V3, V8 | Bản ghi kết quả thanh toán mô phỏng & lịch sử |
| 21 | `payment_attempts` | V13 | Khởi tạo giao dịch thanh toán cổng ngoài (VNPAY Sandbox) |
| 22 | `payment_notifications` | V13 | Nhật ký Server Callback IPN chống tấn công lặp lại |
| 23 | `refund_attempts` | V13 | Yêu cầu hoàn tiền thanh toán muộn & quản trị duyệt hoàn |
| 24 | `payment_compensations` | V8 | Nghĩa vụ bồi hoàn cho thanh toán muộn sau khi hết hạn |
| 25 | `compensation_attempts` | V9 | Lịch sử các lần thực thi bù trừ hoàn tiền |
| 26 | `tickets` | V8 | Vé điện tử phát hành sau khi đơn hàng CONFIRMED |
| 27 | `ticket_check_ins` | V14 | Giao dịch soát vé nguyên tử chống quét vé trùng lặp |
| 28 | `outbox_events` | V1, V3, V5, V8 | Mẫu Transactional Outbox phân phối sự kiện Kafka EDA |
| 29 | `inbox_events` | V9 | Bảng nhận sự kiện và khử trùng lặp tiêu thụ Kafka (Dedup) |
| 30 | `message_failures` | V4, V9 | Nhật ký lỗi xử lý thông điệp và quản lý Dead Letter Queue (DLQ) |
| 31 | `event_projections` | V8, V9 | Bảng tổng hợp chiếu trạng thái sự kiện cho kiểm toán |
| 32 | `projection_events` | V8, V9 | Chi tiết các sự kiện được chiếu vào mô hình đọc |
| 33 | `contact_leads` | V15 | Tiếp nhận leads tư vấn doanh nghiệp & giải pháp sự kiện |
| 34 | `email_notifications` | V15 | Nhật ký biên nhận hóa đơn điện tử / email outbox |
| 35 | `audit_logs` | V15 | Nhật ký kiểm toán toàn diện các hành động quản trị hệ thống |

