# ADR0007 — Kafka, inbox và phục hồi (T6.1–T6.5)

Ngày 2026-11-12. PostgreSQL vẫn là nguồn sự thật; không đổi state machine, giá, hold policy hoặc event schema v1.

## Các điểm thống nhất trước triển khai

- Spring Kafka theo BOM Spring Boot; broker apache/kafka:4.2.2, KRaft một node local. Topic `ticketing.booking.v1` và `ticketing.booking.dlq.v1`, 3 partition, retention 7 ngày, RF/minISR=1. Retry tại partition thay vì topic retry riêng để tránh đảo thứ tự. Đây là điều chỉnh chi tiết triển khai của ADR0001, không đổi hợp đồng event.
- Publisher gửi và chờ ACK từng event, key=bookingId; chỉ gửi version thấp nhất chưa PUBLISHED của mỗi booking. ACK rồi mới đánh dấu DB. Cửa sổ crash giữa ACK và DB có thể gửi trùng; inbox xử lý at-least-once. Không xóa outbox khi lỗi. Mỗi chu kỳ tối đa 3 lần, backoff 1/2 giây rồi FAILED; admin replay có audit mở chu kỳ mới, tối đa 3 request khác nhau/event.
- Một publisher MVP. Session advisory lock PostgreSQL điều phối publisher qua các instance; đây là khóa điều phối riêng, được giữ khi gọi Kafka, **không phải transaction/khóa booking hoặc inventory**. Không gọi Kafka trong transaction nghiệp vụ. Mất connection/ACK không rõ kết quả giữ event để gửi lại cùng ID.
- Consumer `payment-v1` chỉ xử lý PaymentResultRecorded, dùng bộ xử lý payment hiện có, không kiểm tra gap theo booking version. Consumer `booking-audit-v1` nhận đủ 7 loại event và lưu audit/projection với last_applied_version riêng; gap retry hữu hạn rồi park. BookingConfirmed chỉ là thông báo, không phát vé.
- Inbox PK(consumer,eventId), hash envelope; insert inbox và nghiệp vụ/projection cùng transaction PostgreSQL. Offset chỉ ACK sau commit. Trùng ID/nội dung no-op; trùng ID khác nội dung là lỗi. Event/key/UUID/schema/payload phải khớp; không tin status hay thời gian thanh toán từ Kafka.
- Consumer lỗi thử tổng cộng 3 lần, sau đó lưu nguyên record và vị trí Kafka vào `message_failures` trước khi commit offset. Worker riêng gửi thông báo DLQ sau commit; DLQ cũng retry hữu hạn. Nếu PostgreSQL không lưu được lỗi, không ACK record. Recovery gốc là record bền trong DB; không phụ thuộc DLQ đang online.
- Replay cùng envelope/eventId, không xóa inbox. Phục hồi gap theo version tăng dần (replay missing version rồi higher versions). API chỉ admin local/test; audit requestId/reason/admin/time vào outbox.replay_requests. Malformed record không có outbox hợp lệ cần điều tra, không sửa raw record bằng API.
- Khi messaging bật, simulator chỉ ghi kết quả bền + outbox rồi trả 202; Kafka consumer xử lý bất đồng bộ. Scheduled payment worker chỉ ghi kết quả đến hạn. Expiry tiếp tục kiểm tra/xử lý kết quả bền dưới booking lock, nên Kafka ngừng không làm mất quyền của payment đúng hạn. Khi messaging tắt, adapter đồng bộ Phần 6 vẫn dùng được để kiểm thử hồi quy.
- Refund mô phỏng là DB recovery worker trên cùng nghĩa vụ unique(payment_id), bao gồm success sau CANCELLED/PAYMENT_FAILED vốn không tạo BookingRefundRequired. Mỗi attempt và kết quả commit cùng transaction, tối đa 3 attempt/backoff; không gọi dịch vụ tiền thật. COMPLETED không chạy lại; booking REFUND_REQUIRED giữ nguyên, không nhận lại inventory/phát vé. History lưu riêng; FAILED hết ngân sách cần điều tra, không tự tạo nghĩa vụ mới.

## Kiểm chứng bắt buộc

Quét SBOM đầu tiên phát hiện 6 advisory ở dependency nén do kafka-clients kéo vào: at.yawk.lz4:lz4-java1.11.2 và org.xerial.snappy:snappy-java1.1.10.7. Chốt dependencyManagement bản vá1.11.4/1.1.10.9 (đã kiểm tra có trên Maven Central), giữ Kafka client4.2.2 và Boot BOM. Chạy lại build/Kafka regression và OSV sau override; không bỏ qua advisory để CI xanh.

Kafka/PostgreSQL thật trong tài nguyên test riêng: stop/start broker, ACK/DB crash window, thứ tự version, duplicate, restart consumer, poison/gap/retry/DLQ/replay, payment version >1, rollback inbox/nghiệp vụ, refund lặp và history. Chạy lại suite GA/ghế/payment, hợp đồng, frontend. Không đánh dấu Done trước khi ghi bằng chứng thật.

Nguồn API: [Spring Kafka error handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html), [Kafka producer configuration](https://kafka.apache.org/42/configuration/producer-configs/).
