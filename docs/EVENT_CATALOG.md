# EVENT_CATALOG — Hợp đồng sự kiện v0.4

> PostgreSQL là nguồn sự thật. Outbox ghi cùng transaction nghiệp vụ; Kafka phân phối ít nhất một lần. Event mô tả sự kiện đã commit, không tự quyết định quyền sở hữu ghế.

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

| Event | Ghi bởi | Consumer và hiệu ứng |
|---|---|---|
| BookingHeld | Booking transaction | Payment simulator/recovery orchestration; không giữ ghế lần hai |
| PaymentResultRecorded | Simulator transaction | Booking processor; nếu consumer chậm, expiry/reconciler cũng có thể xử lý result bền bằng cùng hàm |
| BookingConfirmed | Booking processor | Audit/operations; ticket đã được tạo idempotent trong cùng DB transaction xác nhận; replay event không phát vé |
| BookingPaymentFailed | Booking processor | Audit/operations; inventory đã được nhả khi commit |
| BookingCancelled | Booking processor | Audit/operations; inventory đã được nhả khi commit |
| BookingExpired | Expiry transaction | Audit/operations; không nhả lần hai |
| BookingRefundRequired | Booking processor | Compensation simulator idempotent, một nghĩa vụ trên mỗi payment, không phát vé |

## Thứ tự và deduplication

- Kafka key là bookingId. Outbox có unique(aggregate_id,aggregate_version); mỗi event của booking (kể cả PaymentResultRecorded) tăng version. Publisher MVP một instance, chọn theo thứ tự version trên mỗi booking, gửi/xác nhận tuần tự trước khi đánh dấu published; không dựa riêng vào SKIP LOCKED hoặc Kafka key để kết luận thứ tự. Nếu cần nhiều publisher, phải thiết kế claim theo aggregate và test thứ tự trước.
- Inbox PK(consumer_name,event_id); ghi inbox với hiệu ứng nghiệp vụ cùng transaction. Duplicate trả no-op.
- Không so sánh aggregateVersion của event với bookings.aggregate_version để bỏ qua tác vụ: DB có thể đã cập nhật sau khi event được commit nhưng trước khi consumer chạy. Payment consumer chỉ xử lý PaymentResultRecorded; nó dedup theo inbox/eventId, đọc payments result bền, khóa booking và áp dụng state machine. Một gap trong chuỗi version *toàn booking* không chứng minh thiếu payment event ở consumer này.
- Consumer projection/audit chỉ kiểm tra `eventVersion > last_applied_version + 1` khi thực sự tiêu thụ đầy đủ mọi event của booking, dùng version riêng của projection; version cũ/đã xử lý thì no-op. Nếu có gap thật, retry hữu hạn rồi đưa vào recovery/DLQ và dừng áp dụng event vượt gap cho booking đó; không tạo bảng buffer cho MVP. Test riêng duplicate, gap thật và loại event không thuộc subscription.
- Outbox index pending created_at để polling; retry backoff hữu hạn. Broker tắt: event vẫn pending. Gửi thành công nhưng chưa đánh dấu: có thể gửi lại.
- PaymentResultRecorded mang paymentResultId. Consumer đọc payments.recorded_at và kiểm tra row bền; không tin timestamp trong payload do client gửi.

## Transaction và bù trừ

Expiry/confirm/fail/cancel cùng cập nhật booking, inventory, history và outbox nguyên tử. Refund simulator cập nhật payment_compensations theo unique(payment_id); paymentResultId/eventId dedup tín hiệu đầu vào, không tạo thêm nghĩa vụ cho cùng payment. REFUND_REQUIRED vẫn giữ nguyên booking state để dễ quan sát; payment compensation có status riêng. Replay không tạo vé trùng hay nhả tồn kho lần hai.

## Triển khai theo tuần

Tuần 3 có bảng outbox và BookingHeld nhưng publisher có thể chưa chạy. Tuần 4 expiry ghi BookingExpired. Tuần 5 simulator ghi durable payment result, dùng cùng processor cục bộ để chứng minh state machine. Tuần 6 publisher, Kafka consumer, inbox, retry và recovery thay đường gọi kích hoạt bằng event; xử lý nghiệp vụ tập trung một hàm để không thay contract HTTP.
