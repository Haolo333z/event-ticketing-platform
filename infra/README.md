# Hạ tầng và script Windows

Hướng dẫn hiện hành: [README gốc](../README.md), gồm môi trường, .env, địa chỉ, demo, test sạch và troubleshooting. Chạy từ gốc repo bằng powershell -NoProfile -ExecutionPolicy Bypass -File infra/<tên>.ps1. Không đổi policy toàn máy.

## Chạy / dừng

| Script | Vai trò / giới hạn |
|---|---|
| local-env.ps1 | Sinh .env/password ngẫu nhiên nếu chưa có; giữ file đã có; đọc KEY=value như dữ liệu, không in password |
| start-local.ps1 | Compose up/wait PG+Kafka, Maven verify, copy JAR chạy foreground UTC. -Port hoặc SERVER_PORT (8080); -HoldSeconds/-ExpiryIntervalSeconds override process; -RestartExisting chỉ thay JVM runtime cùng workspace sau build thành công |
| start-frontend.ps1 | Đọc .env gốc, dùng Vite đã npm ci; foreground 127.0.0.1:5173 strict. -BindAddress ::1 cho clone song song, mở localhost:5173 |
| verify-local.ps1 | **Chỉ đọc** HTTP200/UP, V1 đúng một lần, schema ticketing. -Port hoặc SERVER_PORT; không thay test nghiệp vụ |
| stop-local.ps1 | Kiểm tra listener/command line đúng workspace trước khi dừng Java/Vite; từ chối tiến trình lạ. -Port/-FrontendAddress ::1 cho clone. -StopServices thêm compose stop, **giữ volume** |

Compose pin postgres:17.11/apache/kafka:4.2.2, localhost5432/9092. Project mặc định event-ticketing-local; volume postgres_demo/kafka_demo có prefix project. COMPOSE_PROJECT_NAME trong .env tách cả container/volume; đổi DB_PORT/KAFKA_PORT/bootstrap/backend/Vite URL khi chạy song song. PostgreSQL memory1GiB, Kafka2GiB/heap512MiB. Không Redis. Không reset DB/xóa volume để chữa lỗi. Launcher thiếu PG_IT_URL/KAFKA_IT_* có thể skip integration; dùng gate đầy đủ.

## Gate hiện hành

```powershell
npm.cmd ci --prefix frontend
npm.cmd ci --prefix tools/contracts
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live
```

| Script | Cô lập / tác động |
|---|---|
| test-invariants.ps1 | Gate T9.1: clone HEAD vào TEMP, tạo .env/project/volume/cổng riêng, chạy full backend và UI GA/ghế/expiry/payment/Kafka/restart đã có. Không nạp cấu hình .env workspace/khởi động demo, chỉ đọc hash để kiểm tra bảo toàn; từ chối code/test chưa khớp HEAD. Thu JUnit/artifact/log tại reports/t9-1/runId; dừng project riêng và giữ volume/copy để kiểm tra |
| test-recovery.ps1 [-Live] | Toàn backend qua test-part8/test-part7; -Live thêm test-part8-ui |
| test-part8.ps1 [-Tests ...] | Hai DB UUID trên PG cấu hình, broker/volume UUID riêng; dọn đúng tài nguyên test tự tạo, không xóa demo |
| test-part8-ui.ps1 | DB/JAR/JVM riêng, HTTP port tự chọn, topic/group UUID trên broker cấu hình. HTTP contract, dashboard/pagination, recovery/replay trùng, SQL/log/prod guard. Dọn DB/JVM/JAR test; topic tới retention7ngày |
| test-metrics.ps1 | Gate T8.2; xem source/tham số để chọn ca |
| test-part6-ui.ps1 -Kafka | Regression GA/ghế/payment/expiry/late/refund, restart JVM; DB/topic/group riêng, không ngắt broker demo |

Báo cáo backend/target/surefire-reports, frontend/reports; log TEMP do script báo. Frontend unit/live dùng React/jsdom với API thật, không thay kiểm tra hình ảnh Chrome. OpenAPI: docs/contracts/openapi.json, tools/contracts.

## Script lịch sử

Các test-part3…test-part7 và *-ui giữ để tái hiện từng phần; ưu tiên gate hiện hành, không dùng counter lịch sử để báo kết quả mới. verify-part3 chỉ đọc nhưng kỳ vọng số seed cố định. **verify-part4…verify-part8 ghi fixture/booking**, một số cố định8080; verify-part8 -CreatePaginationFixtures thêm DRAFT. Không dùng làm kiểm tra chỉ đọc trên demo cần giữ.

## Phục hồi / quan sát

Health root/readiness theo DB/Kafka đang bật, liveness theo tiến trình. Log JSON/correlation ID xuyên HTTP→outbox→consumer. Metrics chỉ ADMIN local/test, tên/đơn vị trong [API_CONTRACT](../docs/API_CONTRACT.md). Quản trị demo→Khôi phục xem lỗi/inbox/DLQ/audit; replay tối đa3request mới, giữ inbox/state guard. [Runbook](../docs/DEMO_AND_RECOVERY.md).

T8.4 không đổi schema/API/nghiệp vụ. Bằng chứng README chạy từ clone sạch và giới hạn tại [PROGRESS](../docs/PROGRESS.md).
