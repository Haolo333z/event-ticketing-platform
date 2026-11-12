# Tài liệu đồ án đặt vé sự kiện

Bản v0.4 là thiết kế mục tiêu, được cập nhật đến hết Phần5 (T4.5). Đã có PostgreSQL/Flyway11bảng/V1–V7,8API catalog/admin/booking GA/SEATED, UI API thật và worker expiry. Chưa có thanh toán, phát vé hoặc Kafka. Đọc [PROGRESS](PROGRESS.md) trước khi mở task; [README gốc](../README.md) có lệnh chạy/thử API.

## Thứ tự đọc

Hợp đồng T1.4: [OpenAPI 3.1](contracts/openapi.json), [event JSON Schema](contracts/events.schema.json), [state transitions](contracts/booking-transitions.json), [ADR0002](adr/0002-api-domain-contract.md) và [bảng đối chiếu](TRACEABILITY.md). Kiểm tra bằng `npm ci --prefix tools/contracts` rồi `npm test --prefix tools/contracts`. x-implemented phân biệt8operation đã chạy với6operation tương lai; POST nhận GA hoặc SEATED theo oneOf. [ADR0005](adr/0005-seats-and-expiry.md) chốt worker/khóa ghế/ranh giới payment; [bằng chứng Phần5](evidence/part5-2026-11-12.json) lưu kết quả thật.

1. [PRD](PRD.md): phạm vi, nghiệp vụ, acceptance.
2. [ARCHITECTURE](ARCHITECTURE.md): ranh giới module, transaction, outbox/recovery.
3. [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md): hợp đồng triển khai.
4. [DESIGN](DESIGN.md): giao diện demo.
5. [TEST_PLAN](TEST_PLAN.md), [DEMO_AND_RECOVERY](DEMO_AND_RECOVERY.md): kiểm chứng và runbook.
6. [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md): cách thực hiện.
7. [UPGRADE_REPORT](UPGRADE_REPORT.md): đối chiếu thay đổi v0.2 -> v0.3 -> v0.4.

PRD mục5 là nguồn trạng thái booking; các file khác tham chiếu. T1.1 chốt version/tham số, T1.4 chốt ER/OpenAPI trước triển khai. Số đo đã chạy nằm trong PROGRESS/TEST_PLAN/evidence; các phần tương lai vẫn là mục tiêu, không coi đã nghiệm thu.
