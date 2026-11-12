# EVENT_CATALOG — Hợp đồng sự kiện v0.4

> PostgreSQL là nguồn sự thật. Outbox ghi cùng transaction nghiệp vụ; Kafka phân phối ít nhất một lần. Event mô tả sự kiện đã commit, không tự quyết định quyền sở hữu ghế.

T2.5 có OutboxWriter bắt buộc transaction, envelope v1/index pending. Đến hết Phần5, booking GA/SEATED ghi BookingHeld aggregateVersion=1 cùng booking/item/inventory/key/history; worker expiry tăng version và ghi BookingExpired cùng release/state/history. expiredAt lấy DB clock và khớp booking/history; cause_event_id tham chiếu logic event đã ghi. Worker lặp không tạo thêm event hoặc release. Fault sau append rollback cả expiry và inventory. Không đổi schemaVersion hoặc payload; outbox vẫn PENDING, chưa có Kafka publisher/consumer hoặc payment producer. Catalog không phát event booking giả.

Đến Phần6 đã có producer7type v1: simulator record terminal+PaymentResultRecorded cùng commit,BookingLifecycle ghi confirmed/failed/cancelled/expired/refund cùng state/inventory/history/tickets/compensation. Row payments.processed_at là dedup local callback dưới booking lock; không thêm inbox T6. Scheduled result giữcorrelation_id đến lúc ghi result. Nếu processing lỗi,recordedresult/outboxgốc vẫn bền còn state/history/ticket/outboxxử lý rollback; maintenance retry. Success sauCANCELLED/PAYMENT_FAILED chỉ tạo obligationPENDING,không phát eventrefund khi không đổi state theo hợp đồng. Outbox vẫnPENDING,chưa gửiKafka/hoàn tiền.

## Envelope

    {
      "eventId":"uuid",
      "eventType":"BookingExpired",
      "schemaVersion":1,
      "aggregateId":"booking-uuid",
      "aggregateVersion":3,
      "occurredAt":"2026-11-12T09:10:00Z",
      "correlationId":"uuid",
      "payload":{"bookingId":"booking-uuid","expiredAt":"2026-11-12T09:10:00Z"}
    }

Payload chỉ có ID và dữ liệu cần thiết, không đưa token/thẻ/PII. Khi thay schema không tương thích, tăng schemaVersion và có consumer tương thích.

T1.4 chốt schema machine-readable [contracts/events.schema.json](contracts/events.schema.json) (JSON Schema 2020-12), ví dụ đủ7 type trong contracts/examples.json. Chỉ schemaVersion=1, aggregateVersion>=1, UUID/date-time UTC; thuộc tính lạ bị từ chối. aggregateId phải bằng payload.bookingId (kiểm tra domain, JSON Schema không so chéo giá trị). occurredAt và các mốc nghiệp vụ được server/DB ghi. Payload v1: BookingHeld có bookingId/expiresAt; PaymentResultRecorded có bookingId/paymentResultId; BookingConfirmed có bookingId/confirmedAt; BookingPaymentFailed có bookingId/failedAt; BookingCancelled có bookingId/cancelledAt; BookingExpired có bookingId/expiredAt; BookingRefundRequired có bookingId/paymentId/compensationId. Các ID trong event phải thuộc cùng booking.

paymentResultId là payments.id (UUID server), requestId client là payments.request_id; paymentIntentId nằm trên bookings để nhóm các lần mô phỏng. Late success sau CANCELLED/PAYMENT_FAILED tạo compensation và giữ state hiện tại theo PRD, không phát thêm event BookingRefundRequired vì booking không chuyển REFUND_REQUIRED; compensation processor/reconciler đọc nghĩa vụ pending. Audit replay lưu có giới hạn trên outbox, giữ nguyên envelope/eventId, không xóa inbox; xem ADR0002.

| Event | Ghi bởi | Consumer và hiệu ứng |
|---|---|---|
| BookingHeld | Booking transaction | Payment simulator/recovery orchestration; không giữ ghế lần hai |
| PaymentResultRecorded | Simulator transaction | Booking processor; nếu consumer chậm, expiry/reconciler cũng có thể xử lý result bền bằng cùng hàm |
| BookingConfirmed | Booking processor | Audit/operations; ticket đã được tạo idempotent trong cùng DB transaction xác nhận; replay event không phát vé |
| BookingPaymentFailed | Booking processor | Audit/operations; inventory đã được nhả khi commit |
| BookingCancelled | Booking processor | Audit/operations; inventory đã được nhả khi commit |
| BookingExpired | Expiry transaction | Audit/operations; không nhả lần hai |
| BookingRefundRequired | Booking processor | Audit thông báo; DB recovery worker đọc nghĩa vụ unique(payment_id), không phát vé |

## Thứ tự và deduplication

- Kafka key là bookingId. Outbox có unique(aggregate_id,aggregate_version); mỗi event của booking (kể cả PaymentResultRecorded) tăng version. Publisher MVP một instance, chọn theo thứ tự version trên mỗi booking, gửi/xác nhận tuần tự trước khi đánh dấu published; không dựa riêng vào SKIP LOCKED hoặc Kafka key để kết luận thứ tự. Nếu cần nhiều publisher, phải thiết kế claim theo aggregate và test thứ tự trước.
- Inbox PK(consumer,event_id); ghi inbox với hiệu ứng nghiệp vụ cùng transaction. Duplicate trả no-op.
- Không so sánh aggregateVersion của event với bookings.aggregate_version để bỏ qua tác vụ: DB có thể đã cập nhật sau khi event được commit nhưng trước khi consumer chạy. Payment consumer chỉ xử lý PaymentResultRecorded; nó dedup theo inbox/eventId, đọc payments result bền, khóa booking và áp dụng state machine. Một gap trong chuỗi version *toàn booking* không chứng minh thiếu payment event ở consumer này.
- Consumer projection/audit chỉ kiểm tra `eventVersion > last_applied_version + 1` khi thực sự tiêu thụ đầy đủ mọi event của booking, dùng version riêng của projection; version cũ/đã xử lý thì no-op. Nếu có gap thật, retry hữu hạn rồi đưa vào recovery/DLQ và dừng áp dụng event vượt gap cho booking đó; không tạo bảng buffer cho MVP. Test riêng duplicate, gap thật và loại event không thuộc subscription.
- Outbox index pending created_at để polling; retry backoff hữu hạn. Broker tắt: event vẫn pending. Gửi thành công nhưng chưa đánh dấu: có thể gửi lại.
- PaymentResultRecorded mang paymentResultId. Consumer đọc payments.recorded_at và kiểm tra row bền; không tin timestamp trong payload do client gửi.

## Transaction và bù trừ

Expiry/confirm/fail/cancel cùng cập nhật booking, inventory, history và outbox nguyên tử. Refund simulator cập nhật payment_compensations theo unique(payment_id); paymentResultId/eventId dedup tín hiệu đầu vào, không tạo thêm nghĩa vụ cho cùng payment. REFUND_REQUIRED vẫn giữ nguyên booking state để dễ quan sát; payment compensation có status riêng. Replay không tạo vé trùng hay nhả tồn kho lần hai.

## Triển khai theo tuần

Tuần 3 có bảng outbox và BookingHeld nhưng publisher có thể chưa chạy. Tuần 4 expiry ghi BookingExpired. Tuần 5 simulator ghi durable payment result, dùng cùng processor cục bộ để chứng minh state machine. Tuần 6 publisher, Kafka consumer, inbox, retry và recovery thay đường gọi kích hoạt bằng event; xử lý nghiệp vụ tập trung một hàm để không thay contract HTTP.

## T6 — Đường chạy Kafka hiện tại

Chi tiết thay các mô tả lịch sử “chưa Kafka” ở trên: [ADR0007](adr/0007-kafka-and-recovery.md). Producer dùng outbox sau commit, business topic `ticketing.booking.v1` và DLQ `ticketing.booking.dlq.v1`; schema v1 giữ nguyên. Consumer group mặc định `ticketing.payment-v1` và `ticketing.booking-audit-v1`, inbox consumer tương ứng `payment-v1`/`booking-audit-v1`.

Payment chỉ nhận PaymentResultRecorded. Audit nhận đủ 7 type và ghi projection riêng; trùng ID/hash no-op, gap/collision retry3delivery rồi parkDB trướcACK. Không bỏ payment vì version cũ so với booking. Business publisher retry3lần/backoff1/2s rồi FAILED giữ nguyên record; version sau bị chặn cho tới recovery. Consumer retry tại partition250ms, không có retry topic riêng. DLQ notification được gửi từ failureDB sau commit (tối đa3attempt), nên Kafka/DB outage không làm mất raw record.

Replay admin local/test giữ eventId/envelope gốc, tối đa3request/event, không xóa inbox; thiếu version phục hồi theo thứ tự. Refund mô phỏng đọc nghĩa vụ bền, retry3attempt/backoff1/2s, history cùng transaction; FAILED hết ngân sách cần điều tra, không tự tạo payment hay nghĩa vụ mới. Không dùng event BookingConfirmed để phát vé.
