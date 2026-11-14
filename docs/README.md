# Tài liệu đồ án đặt vé sự kiện

Bản v0.4 đã triển khai đến T8.4 và kiểm chứng tích hợp T9.1–T9.3. Có V1–V9, 18 bảng, 18 operation OpenAPI; booking GA/ghế, expiry, thanh toán/vé/bù trừ mô phỏng, Kafka/outbox/inbox/retry/DLQ, replay có audit, dashboard, log/health/metrics. T9.4 đang rà soát; đọc PROGRESS để phân biệt kết quả đã chạy với giới hạn. README gốc có lệnh chạy/thử API.

## Thứ tự đọc

Hợp đồng: [OpenAPI3.1](contracts/openapi.json), [event JSON Schema](contracts/events.schema.json), [state transitions](contracts/booking-transitions.json), ADR0002 và TRACEABILITY. Kiểm tra bằng npm ci/test --prefix tools/contracts; 18 operation hiện có đã được triển khai. [ADR0006](adr/0006-payment-results-and-tickets.md) chốt payment/expiry/ticket; [ADR0007](adr/0007-kafka-and-recovery.md) chốt messaging/recovery. Kết quả từng giai đoạn nằm tại PROGRESS và evidence; không dùng số test lịch sử thay cho lượt chạy mới.

1. [PRD](PRD.md): phạm vi, nghiệp vụ, acceptance.
2. [ARCHITECTURE](ARCHITECTURE.md): ranh giới module, transaction, outbox/recovery.
3. [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md): hợp đồng triển khai.
4. [DESIGN](DESIGN.md): giao diện demo.
5. [TEST_PLAN](TEST_PLAN.md), [DEMO_AND_RECOVERY](DEMO_AND_RECOVERY.md): kiểm chứng và runbook.
6. [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md): cách thực hiện.
7. [UPGRADE_REPORT](UPGRADE_REPORT.md): đối chiếu thay đổi v0.2 -> v0.3 -> v0.4.

PRD mục5 là nguồn trạng thái booking; các file khác tham chiếu. T1.1 chốt version/tham số, T1.4 chốt ER/OpenAPI trước triển khai. Số đo đã chạy nằm trong PROGRESS/TEST_PLAN/evidence; các phần tương lai vẫn là mục tiêu, không coi đã nghiệm thu.
