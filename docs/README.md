# Tài liệu đồ án đặt vé sự kiện

Bản v0.4 là tài liệu thiết kế trước khi viết mã, đã cập nhật các điểm cần sửa sau rà soát v0.3. Chưa có ứng dụng, schema SQL, OpenAPI chạy được hoặc kết quả kiểm thử. Đọc [PROGRESS](PROGRESS.md) trước khi mở task.

## Thứ tự đọc

1. [PRD](PRD.md): phạm vi, nghiệp vụ, acceptance.
2. [ARCHITECTURE](ARCHITECTURE.md): ranh giới module, transaction, outbox/recovery.
3. [DATA_MODEL](DATA_MODEL.md), [API_CONTRACT](API_CONTRACT.md), [EVENT_CATALOG](EVENT_CATALOG.md): hợp đồng triển khai.
4. [DESIGN](DESIGN.md): giao diện demo.
5. [TEST_PLAN](TEST_PLAN.md), [DEMO_AND_RECOVERY](DEMO_AND_RECOVERY.md): kiểm chứng và runbook.
6. [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md): cách thực hiện.
7. [UPGRADE_REPORT](UPGRADE_REPORT.md): đối chiếu thay đổi v0.2 -> v0.3 -> v0.4.

PRD mục 5 là nguồn trạng thái booking; các file khác tham chiếu. T1.1 chốt version và tham số, T1.4 chốt ER/OpenAPI trước khi triển khai. Mọi số liệu trong tài liệu là mục tiêu hoặc giả định, chưa được đo.
