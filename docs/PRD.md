# PRD — Hệ thống đặt vé sự kiện đa hình thức

> Phiên bản: 0.4 · Trạng thái: bản nền đã qua phản biện, trước khi scaffold code · Chủ dự án: Lê Hữu Hào

## 1. Tóm tắt

Xây dựng hệ thống đặt vé hỗ trợ ghế ngồi chỉ định và vé vào cửa không chỉ định chỗ. Hệ thống xử lý yêu cầu cạnh tranh không bán vượt tồn kho, theo dõi thanh toán mô phỏng và phục hồi khi worker hoặc broker tạm ngừng.

Đây là đồ án cá nhân 10 tuần, định hướng backend, chạy local bằng Docker Compose. Sản phẩm gồm API, giao diện demo gọn, dữ liệu mẫu, kịch bản trình diễn và báo cáo kỹ thuật. Mục tiêu là chứng minh tính nhất quán, xử lý tranh chấp và phục hồi; đây không phải nền tảng thương mại production-ready.

## 2. Người dùng và nhu cầu

| Người dùng | Nhu cầu chính |
|---|---|
| Khách hàng demo | Tìm sự kiện, chọn ghế hoặc số vé, tạo giữ chỗ, chạy thanh toán mô phỏng và xem trạng thái booking. |
| Quản trị viên demo | Tạo/cập nhật sự kiện, cấu hình ghế hoặc sức chứa, giá vé và xem inventory/booking. |
| Người đánh giá / nhà tuyển dụng | Chạy demo, quan sát concurrency, event flow, retry và phục hồi. |

## 3. Phạm vi

### Có trong MVP

1. Danh sách/chi tiết sự kiện có ghế và sự kiện vé GA.
2. Quản trị sự kiện, loại vé, giá, ghế và sức chứa.
3. Tạo booking có idempotency key; server tự xác định giá.
4. Giữ tài nguyên có thời hạn; một luồng expiry riêng giải phóng tài nguyên.
5. Thanh toán mô phỏng thành công, thất bại hoặc trì hoãn.
6. Booking state machine, lịch sử trạng thái, outbox/inbox, consumer idempotent và bù trừ khi thanh toán muộn.
7. Xác thực demo bằng mock bearer token gắn với tài khoản customer/admin đã seed, chỉ ở profile local/test.
8. OpenAPI, health check, log/metrics cơ bản, Docker Compose, hướng dẫn chạy và báo cáo.

### Ngoài MVP

- Cổng thanh toán/hoàn tiền thật, dữ liệu thẻ hoặc tiền thật.
- Đa nhà tổ chức, QR production, chuyển nhượng vé, định giá động, flash-sale Internet scale.
- Microservices riêng theo miền, Kubernetes, multi-region, phân vùng dữ liệu.
- Cam kết exactly-once xuyên PostgreSQL và broker; dùng at-least-once với xử lý lặp an toàn.

## 4. Luồng nghiệp vụ

### 4.1 Tạo booking

1. Client gửi yêu cầu kèm idempotency key; danh tính lấy từ principal đã xác thực.
2. API kiểm tra sự kiện, mở bán, số lượng, giá và quyền mua.
3. Trong transaction PostgreSQL, kiểm tra key/payload, khóa inventory theo thứ tự ổn định, chỉ nhận tài nguyên AVAILABLE, rồi ghi booking PENDING_PAYMENT, hold, history và outbox.
4. Booking nhiều ghế là all-or-nothing; thiếu một ghế hoặc không đủ vé GA thì rollback toàn bộ.
5. API trả 202 Accepted, bookingId và expiresAt; client theo dõi bằng API trạng thái.

### 4.2 Hết hạn và booking mới

- Expiry Worker chạy độc lập theo batch. Với booking đến hạn, worker khóa booking, kiểm tra lại trạng thái/thời điểm bằng đồng hồ PostgreSQL, nhả toàn bộ tài nguyên còn thuộc booking đó, chuyển sang EXPIRED, ghi history/outbox và commit trong cùng transaction.
- Booking mới không xem hold quá giờ là tài nguyên trống và không ghi đè hold cũ. Tài nguyên chỉ AVAILABLE sau khi transaction nhả hold đã commit.
- Nếu booking mới đến trước khi worker commit, API trả lỗi có thể thử lại như 409 INVENTORY_UNAVAILABLE hoặc 409 HOLD_EXPIRY_PROCESSING. Client tải lại availability rồi thử lại.
- Với GA, held_count chỉ giảm trong transaction expiry; inventory có thể tạm thời chưa đặt lại được khi worker đang xử lý. Đây là hành vi an toàn.

### 4.3 Ghi nhận payment và phân xử đúng hạn

- Payment simulator chỉ chạy local/test qua API demo. Transaction ghi payments.recorded_at bằng đồng hồ PostgreSQL và outbox PaymentResultRecorded; requestId chống ghi result lặp.
- Một success được ghi bền trước hoặc đúng expires_at là đúng hạn. Consumer xử lý chậm không đổi recorded_at. Expiry Worker, sau khi khóa booking, kiểm tra payment result đã commit; nếu có success đúng hạn, dùng cùng PaymentResultProcessor để CONFIRMED thay vì nhả inventory.
- Nếu worker đã commit EXPIRED trước khi simulator ghi success, success là muộn: EXPIRED -> REFUND_REQUIRED, không phát vé. Nếu result ghi muộn trước worker, processor trước tiên expire/release rồi chuyển REFUND_REQUIRED trong transaction có history đủ hai bước.
- Cùng booking không được vừa CONFIRMED vừa EXPIRED; payment failure/cancel chỉ release khi transition từ PENDING_PAYMENT thắng. Event lặp/no-op không phát vé hoặc nhả lần hai.
- Thanh toán/hoàn tiền thật ngoài MVP. Khi nối provider thật, cần ADR về timestamp đáng tin cậy và đối soát settlement.


## 5. Trạng thái và bất biến nghiệp vụ

| Trạng thái | Ý nghĩa |
|---|---|
| PENDING_PAYMENT | Đang giữ inventory và chờ payment đến hạn. |
| CONFIRMED | Payment được chấp nhận đúng hạn; inventory đã bán và vé có thể phát hành. |
| PAYMENT_FAILED | Payment thất bại; inventory nhả đúng một lần. |
| CANCELLED | Booking hủy hợp lệ; inventory nhả đúng một lần. |
| EXPIRED | Hold hết hạn; inventory đã nhả và expired_at được lưu. |
| REFUND_REQUIRED | Booking hết hạn nhưng nhận payment success muộn; không phát vé, cần bù trừ. |

Chuyển trạng thái: PENDING_PAYMENT -> CONFIRMED, PAYMENT_FAILED, CANCELLED hoặc EXPIRED; EXPIRED -> REFUND_REQUIRED.

~~~mermaid
stateDiagram-v2
    PENDING_PAYMENT --> CONFIRMED: success đúng hạn
    PENDING_PAYMENT --> PAYMENT_FAILED: payment failure
    PENDING_PAYMENT --> CANCELLED: customer cancel
    PENDING_PAYMENT --> EXPIRED: hết hạn
    EXPIRED --> REFUND_REQUIRED: success sau expiry
~~~


- Không oversell: sold + held không vượt capacity; ghế không có hai booking đang hoạt động.
- Không nhận lại hold trực tiếp: tài nguyên chỉ có thể được nhận sau khi lần nhả đã commit.
- Nhả inventory tối đa một lần về mặt nghiệp vụ; retry, event trùng hoặc worker lặp không làm sai bộ đếm.
- Idempotency theo account/operation/key: payload giống thì trả kết quả cũ; payload khác bị từ chối.
- Booking nhiều ghế là nguyên tử.
- EXPIRED và REFUND_REQUIRED không được phát vé.
- Confirm GA quantity N phát đúng N vé duy nhất; ghế định danh phát một vé cho mỗi booking_item. Event thông báo BookingConfirmed bị replay không tạo vé mới.
- Một payment thành công cần bù trừ chỉ có một nghĩa vụ payment_compensations; retry hoàn tiền mô phỏng không tạo hai lần hoàn tất.
- Lấy danh tính/role từ mock token đã seed trong local/test; client không tự khai báo user/role.
- Outbox còn chờ có thể gửi lại; consumer lặp được deduplicate; lỗi cuối có thể điều tra/replay có kiểm soát.

### Event × trạng thái và quy tắc admin

| State hiện tại | Success đúng hạn | Success muộn | Failure | Hủy | Expiry |
|---|---|---|---|---|---|
| PENDING_PAYMENT | CONFIRMED | EXPIRED rồi REFUND_REQUIRED | PAYMENT_FAILED | CANCELLED | EXPIRED nếu không có success đúng hạn đã ghi |
| CONFIRMED | no-op | no-op | ghi lỗi đối soát, không release | ngoài MVP | no-op |
| PAYMENT_FAILED/CANCELLED | bù trừ nếu đã ghi tiền, không confirm | bù trừ | no-op | no-op | no-op |
| EXPIRED | REFUND_REQUIRED nếu payment đã thành công mà chưa xử lý; không nhận lại inventory | REFUND_REQUIRED | no-op | no-op | no-op |
| REFUND_REQUIRED | no-op, bù trừ idempotent | no-op | no-op | no-op | no-op |

Sau khi EXPIRED đã commit, kể cả payment có recorded_at trước hạn nhưng xử lý bị lỗi và ghế đã bán lại, không tự confirm: bù trừ và ghi lỗi đối soát. Worker đúng phải phát hiện payment đúng hạn trước khi release; test bắt race này.

- Admin không giảm GA capacity dưới held+sold, không xóa ghế HELD/SOLD, không đổi mode loại vé có booking. Giá mới chỉ áp dụng cho đơn mới; booking_items giữ giá snapshot.
- Hủy customer chỉ cho PENDING_PAYMENT và chưa có success bền; CONFIRMED không hủy/hoàn thật trong MVP.
- Flash sale trong MVP là kịch bản mở bán theo thời gian và tải đồng thời; hàng chờ quy mô Internet nằm ngoài phạm vi.
- Tham số khởi đầu đã chốt ở T1.1/T1.4: TTL hold 10 phút, worker mỗi 5 giây, batch tối đa 50 booking, tối đa 6 vé/booking. Giới hạn HTTP/CORS/deadline được chốt tại [ADR0002](adr/0002-api-domain-contract.md); HTTP schema tại [OpenAPI](contracts/openapi.json), 5 cạnh state tại [booking-transitions](contracts/booking-transitions.json). Đây là cấu hình khởi đầu phải kiểm chứng khi có nghiệp vụ, không phải kết quả benchmark.

## 6. Mục tiêu kiểm chứng demo

- Tối thiểu 50 yêu cầu đồng thời trên cùng inventory, không oversell; báo cáo cấu hình máy và giới hạn.
- Tuần 3 kiểm tra GA cạnh tranh và idempotency request trùng.
- Tuần 4 kiểm tra ghế cạnh tranh, booking nhiều ghế all-or-nothing và expiry worker.
- Theo dấu correlationId, bookingId, eventId từ API qua outbox tới consumer.
- Không để booking PENDING_PAYMENT vô thời hạn khi worker/consumer hoạt động.
- Ghi latency, thành công/xung đột/lỗi theo kịch bản; chốt ngưỡng sau khi cố định môi trường.

## 7. Giả định và rủi ro

- Payment mô phỏng; MVP dùng VND và số nguyên.
- PostgreSQL là nguồn sự thật; broker có thể gửi lặp.
- Mock token chỉ dùng cho demo local/test, không phải auth production.
- Worker chậm có thể khiến hold đã đến hạn tạm thời chưa đặt lại được. API trả conflict có thể thử lại; không tự nhận tài nguyên thay worker.
- Trong 10 tuần, ưu tiên inventory correctness, idempotency, outbox/recovery trước UI nâng cao.

## 8. Định nghĩa hoàn thành

Người khác có thể chạy README, dùng persona demo đã seed để mua vé ghế và GA, quan sát xử lý tranh chấp, xem EXPIRED -> REFUND_REQUIRED khi payment muộn, kiểm tra event/recovery flow, mở OpenAPI và đọc báo cáo có kết quả cùng giới hạn thực tế.


Tham chiếu triển khai: [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md) và [TEST_PLAN](TEST_PLAN.md).
