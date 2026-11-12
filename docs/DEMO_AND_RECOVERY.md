# DEMO_AND_RECOVERY — Vận hành và trình diễn

> Runbook dự kiến trước khi có source code. Lệnh chính xác và screenshot bổ sung ở tuần 8–10; không nhận đây là quy trình đã chạy.

## Chuẩn bị

1. Chốt version ở T1.1, khởi động Docker Compose chỉ trên localhost. Đảm bảo PostgreSQL/Kafka healthy, backend migration thành công.
2. Seed một event ghế, một event GA, customer/admin mock token local/test; không triển khai profile demo ra Internet.
3. Mở OpenAPI, UI, health, dashboard; kiểm tra profile local/test và dữ liệu seed.
4. Reset dữ liệu demo bằng script được tài liệu hóa; không xóa dữ liệu người dùng thật.

## Kịch bản 5–10 phút

1. Xem event và availability; đặt hai ghế bằng customer token, thấy PENDING_PAYMENT và 202.
2. Gửi hai request cạnh tranh cùng ghế; một thành công, một conflict. Gửi cùng idempotency key để quan sát replay.
3. Đặt GA với nhiều request vượt capacity, xem count sau test.
4. Đợi hết hạn: trước khi worker commit, ghế cũ chưa đặt lại được; sau commit, ghế AVAILABLE và booking EXPIRED.
5. Kích hoạt payment success muộn; booking REFUND_REQUIRED, không có vé; xem history/outbox.
6. Dừng broker, tạo booking, kiểm tra outbox pending; bật lại và quan sát publisher/consumer hồi phục, không gây hiệu ứng lặp.

## Recovery

| Sự cố | Quan sát | Hành động có kiểm soát |
|---|---|---|
| Kafka tắt | outbox pending/oldest age tăng | bật broker, publisher retry; không sửa booking thủ công |
| Worker expiry tắt | overdue hold count/age tăng | bật worker, kiểm tra release đúng một lần |
| Consumer lỗi | lag/DLQ, inbox | sửa nguyên nhân, replay event bằng admin local có audit |
| Payment success muộn | REFUND_REQUIRED, payment record | chạy bù trừ mô phỏng idempotent; không phát vé |
| Retry request mơ hồ | idempotency key | gửi lại đúng key/payload, GET booking kiểm tra state |

Chỉ replay sau khi xác định event/aggregate và nguyên nhân; không bỏ inbox/state guard. Mọi lệnh thao tác dữ liệu cần log actor, correlation ID và kết quả. Các thao tác demo/replay bị vô hiệu ngoài local/test.

## Giới hạn

Đây là mô phỏng payment/refund, không thu tiền. GA là một hàng khóa nên throughput có thể giới hạn; báo cáo số đo thật. README triển khai sẽ ghi lệnh chạy và reset chính xác khi có source.
