# Tài liệu đồ án đặt vé sự kiện

Bản v0.4 được cập nhật đến hết Phần6 (T5.5). Đã có V1–V8,13bảng,11API,booking GA/ghế,expiry,thanh toán mô phỏng,cancel,vé và nghĩa vụ bù trừ PENDING. Chưa có Kafka/inbox/refund worker. Đọc PROGRESS trước khi mở task; README gốc có lệnh chạy/thử API.

## Thứ tự đọc

Hợp đồng: [OpenAPI3.1](contracts/openapi.json), [event JSON Schema](contracts/events.schema.json), [state transitions](contracts/booking-transitions.json), ADR0002 và TRACEABILITY. Kiểm tra bằng npm ci/test --prefix tools/contracts. x-implemented phân biệt11operation đã chạy với3operation tương lai. [ADR0006](adr/0006-payment-results-and-tickets.md) chốt durable result/processor/expiry/ticket/compensation; [bằng chứng Phần6](evidence/part6-2026-11-12.json) lưu kết quả thật.

1. [PRD](PRD.md): phạm vi, nghiệp vụ, acceptance.
2. [ARCHITECTURE](ARCHITECTURE.md): ranh giới module, transaction, outbox/recovery.
3. [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md): hợp đồng triển khai.
4. [DESIGN](DESIGN.md): giao diện demo.
5. [TEST_PLAN](TEST_PLAN.md), [DEMO_AND_RECOVERY](DEMO_AND_RECOVERY.md): kiểm chứng và runbook.
6. [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md): cách thực hiện.
7. [UPGRADE_REPORT](UPGRADE_REPORT.md): đối chiếu thay đổi v0.2 -> v0.3 -> v0.4.

PRD mục5 là nguồn trạng thái booking; các file khác tham chiếu. T1.1 chốt version/tham số, T1.4 chốt ER/OpenAPI trước triển khai. Số đo đã chạy nằm trong PROGRESS/TEST_PLAN/evidence; các phần tương lai vẫn là mục tiêu, không coi đã nghiệm thu.
