# Hạ tầng local

T1.3 chạy PostgreSQL 17.11 bằng Compose và backend bằng JDK 25 trên host. Chưa chạy Kafka hoặc tạo schema nghiệp vụ.

Từ thư mục gốc trên Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
```

Script tạo `.env` nếu chưa tồn tại với mật khẩu ngẫu nhiên, không ghi mật khẩu ra console; giữ file đã có. Nó nạp các dòng KEY=value vào môi trường process, đợi PostgreSQL healthy, build/test backend rồi chạy JVM foreground với timezone UTC. `ExecutionPolicy Bypass` chỉ áp dụng process được gọi, không đổi policy hệ thống. Ctrl+C dừng backend. Nếu backend đang chạy từ lần trước, dừng nó trước để tránh chiếm cổng 8080.

Trong terminal khác:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
docker compose --env-file .env -f infra/docker-compose.yml ps
```

`verify-local.ps1` yêu cầu HTTP 200/status UP, V1 thành công đúng một lần và schema ticketing tồn tại. Migration do Flyway tự chạy trước startup; history cố định ở public, `baseline-on-migrate=false`, `clean-disabled=true`. V1 chỉ tạo schema nền; domain migration bắt đầu từ V2 sau T1.4. Test Maven dùng H2 và tắt Flyway để kiểm tra context độc lập; script này mới kiểm tra PostgreSQL/Flyway thật.

```powershell
docker compose --env-file .env -f infra/docker-compose.yml stop postgres
```

Lệnh stop giữ volume. Không dùng `down -v` với dữ liệu cần giữ: lệnh đó xóa database demo. Muốn kiểm chứng lại từ DB sạch nên dùng volume/project demo riêng; lần nghiệm thu T1.3 dùng volume mới, xác nhận 0 bảng trước startup rồi kiểm tra restart trên cùng volume.

Baseline và ngân sách tài nguyên: [ADR 0001](../docs/adr/0001-stack-and-local-environment.md). PostgreSQL 5432 và backend 8080 chỉ bind 127.0.0.1. `.env` không commit. Không thêm Redis khi chưa có ADR.
