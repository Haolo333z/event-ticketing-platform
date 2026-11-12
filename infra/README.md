# Hạ tầng local

Thư mục dành cho Docker Compose ở các task môi trường tiếp theo. T1.2 chỉ tạo cấu trúc; chưa có Compose, DB, migration hay Kafka đang chạy từ repository này.

Baseline và ngân sách tài nguyên: [ADR 0001](../docs/adr/0001-stack-and-local-environment.md). Host ports phải bind 127.0.0.1. File .env ở gốc không tự được Spring/Vite đọc; chỉ nối cấu hình khi triển khai môi trường. Không thêm Redis khi chưa có ADR.
