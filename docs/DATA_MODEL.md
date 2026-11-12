# DATA_MODEL — Thiết kế dữ liệu PostgreSQL

> Bản thiết kế v0.4; T1.3 đã triển khai V1 tạo schema nền ticketing, chưa tạo bảng nghiệp vụ. [PRD](PRD.md) là nguồn quy tắc nghiệp vụ, [ARCHITECTURE](ARCHITECTURE.md) là nguồn transaction boundary.

## Phân chia tồn kho

- Ghế định danh: một hàng seats cho một ghế; status AVAILABLE, HELD, SOLD và held_by_booking_id. Không cộng ghế vào bộ đếm GA.
- Vé GA: một hàng inventory_ga cho mỗi event/ticket_type, có capacity, held_count, sold_count.
- booking_items lưu snapshot ticket_type_id, seat_id hoặc quantity, unit_price_vnd và total_price_vnd tại lúc đặt. Đổi giá sau đó không sửa đơn cũ.
- Trạng thái booking là nguồn sự thật của vòng đời; seat/GA chứa hiệu ứng giữ/bán được đối chiếu theo booking_item.

## Quan hệ chính

~~~mermaid
erDiagram
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
    PAYMENTS ||--o| PAYMENT_COMPENSATIONS : compensates
    BOOKINGS ||--o{ PAYMENT_COMPENSATIONS : requires
    BOOKING_ITEMS ||--o{ TICKETS : issues
    BOOKINGS ||--o{ TICKETS : owns
    BOOKINGS ||--o{ OUTBOX_EVENTS : emits
    OUTBOX_EVENTS ||--o{ INBOX_EVENTS : deduplicates_logically
    BOOKINGS ||--o{ BOOKING_STATUS_HISTORY : transitions
    OUTBOX_EVENTS o|--o{ BOOKING_STATUS_HISTORY : causes_logically
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
| inbox_events | consumer_name, event_id, processed_at | PK(consumer_name,event_id) |
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

Migration phải thêm outbox schema và event envelope trước booking tuần 3. V1 đã tạo schema ticketing ở T1.3; Flyway history cố định ở public. Catalog/users và các bảng nghiệp vụ bắt đầu từ V2 trở đi; số thứ tự cuối chốt sau T1.4. Seed persona và sự kiện ở local/test riêng, không cho admin token hoạt động ngoài profile đó. Migration đã áp dụng không sửa lại; thêm migration mới.

## Dữ liệu giữ lại

MVP không xóa idempotency key/outbox/inbox trong 10 tuần để replay an toàn và dễ audit. Khi vận hành lâu dài cần chính sách retention dựa trên thời hạn khiếu nại, bản sao lưu và version, chốt bằng ADR trước khi bật cleanup. Không coi chỉ mục hoặc check constraint là bằng chứng đủ cho concurrency nếu chưa chạy test.

## Chốt mô hình v0 tại T1.4

Đây là đặc tả đầu vào migration, không phải SQL đã chạy. Tất cả bảng nghiệp vụ ở schema ticketing; Flyway history ở public. UUID do server sinh; tiền bigint VND trong [0,10^12], thời gian timestamptz UTC. Không dùng cascade delete với dữ liệu lịch sử; FK mặc định RESTRICT/NO ACTION. Bảng có id dùng UUID PK NOT NULL. Các cột không ghi NULL dưới đây là NOT NULL. created_at mặc định clock_timestamp() khi insert; không dùng thời gian client quyết định expiry/payment.

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
| inbox_events | PK(consumer_name varchar(100),event_id UUID); processed_at. event_id liên kết logic tới outbox.id, **không FK** vì message có thể được nhận độc lập/retention khác; domain/business commit cùng inbox |
| booking_status_history | id UUID, booking_id FK bookings, from_status NULL hoặc enum 6 state, to_status enum 6 state, occurred_at, cause_event_id UUID NULL (logical reference, không FK); initial NULL→PENDING_PAYMENT; transitions khác đúng PRD §5, không thêm transition REFUND_REQUIRED→CONFIRMED |

Quan hệ outbox→inbox và cause_event_id trên ER là quan hệ logic, không yêu cầu SQL FK. Không tạo bảng projection/buffer chưa có use case. Bảng booking history giữ hai bước EXPIRED và REFUND_REQUIRED nếu late result xử lý cùng transaction. Khi success sau CANCELLED/PAYMENT_FAILED, giữ nguyên state và tạo compensation, không tự chuyển REFUND_REQUIRED (chỉ EXPIRED có cạnh đó).

Index chốt: index mọi FK chưa được bao phủ bởi prefix PK/unique (account/event/type/booking/payment/item); bookings(status,expires_at,id) partial WHERE status='PENDING_PAYMENT'; bookings(account_id,created_at DESC,id DESC); seats(event_id,ticket_type_id,status,id), seats(hold_expires_at) partial HELD; outbox(status,next_attempt_at,created_at,id) partial PENDING/FAILED; payments(booking_id,recorded_at), payments(result_due_at,id) partial SCHEDULED; history(booking_id,occurred_at,id). Không tạo unique inbox(event_id) toàn cục vì mỗi consumer có dedup riêng.

Schema không thể tự bảo đảm tổng item, số vé theo quantity, mode inventory hay transition cross-table. Các invariant này cần domain transaction và test GA/SEAT/EXP/PAY/TICKET/COMP trong TEST_PLAN. Toàn bộ event và HTTP ID mapping chốt tại [ADR 0002](adr/0002-api-domain-contract.md).
