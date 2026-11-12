# DATA_MODEL — Thiết kế dữ liệu PostgreSQL

> Bản thiết kế v0.4; SQL migration chưa được triển khai. [PRD](PRD.md) là nguồn quy tắc nghiệp vụ, [ARCHITECTURE](ARCHITECTURE.md) là nguồn transaction boundary.

## Phân chia tồn kho

- Ghế định danh: một hàng seats cho một ghế; status AVAILABLE, HELD, SOLD và held_by_booking_id. Không cộng ghế vào bộ đếm GA.
- Vé GA: một hàng inventory_ga cho mỗi event/ticket_type, có capacity, held_count, sold_count.
- booking_items lưu snapshot ticket_type_id, seat_id hoặc quantity, unit_price_vnd và total_price_vnd tại lúc đặt. Đổi giá sau đó không sửa đơn cũ.
- Trạng thái booking là nguồn sự thật của vòng đời; seat/GA chứa hiệu ứng giữ/bán được đối chiếu theo booking_item.

## Quan hệ chính

~~~mermaid
erDiagram
    USERS ||--o{ BOOKINGS : places
    EVENTS ||--o{ TICKET_TYPES : offers
    EVENTS ||--o{ SEATS : contains
    BOOKINGS ||--|{ BOOKING_ITEMS : contains
    TICKET_TYPES ||--o{ BOOKING_ITEMS : selected
    BOOKINGS ||--o{ PAYMENTS : records
    PAYMENTS ||--o| PAYMENT_COMPENSATIONS : compensates
    BOOKING_ITEMS ||--o{ TICKETS : issues
    BOOKINGS ||--o{ OUTBOX_EVENTS : emits
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
| payments | id, booking_id, payment_result_id, scenario, status, recorded_at, processed_at | unique(booking_id, payment_result_id); recorded_at dùng DB clock; không lưu thẻ |
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

Migration phải thêm outbox schema và event envelope trước booking tuần 3. Gợi ý: V1 catalog/users; V2 inventory/booking/idempotency/outbox/payments; V3 tickets/payment_compensations/inbox; số thứ tự cuối chốt sau T1.4. Seed persona và sự kiện ở local/test riêng, không cho admin token hoạt động ngoài profile đó. Migration đã áp dụng không sửa lại; thêm migration mới.

## Dữ liệu giữ lại

MVP không xóa idempotency key/outbox/inbox trong 10 tuần để replay an toàn và dễ audit. Khi vận hành lâu dài cần chính sách retention dựa trên thời hạn khiếu nại, bản sao lưu và version, chốt bằng ADR trước khi bật cleanup. Không coi chỉ mục hoặc check constraint là bằng chứng đủ cho concurrency nếu chưa chạy test.
