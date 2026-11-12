# Hạ tầng local

PostgreSQL17.11 chạy bằng Compose, backend bằng JDK25 trên host. Đến hết Phần4 có V1–V6, catalog/auth/admin, booking GA/idempotency/history và outbox BookingHeld. Chưa có expiry worker/payment/Kafka.

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

`verify-local.ps1` kiểm tra HTTP200/UP, V1 đúng một lần và schema ticketing. `verify-part3.ps1` bổ sung2sự kiện,4ghế/GA100,401/403 và V1–V4. Flyway tự chạy trước startup; history public, baseline-on-migrate=false, clean-disabled=true. V1 không sửa; V2 catalog+bookings/items nền, V3 payments/outbox, V4 chặn JSON null bookingId. Seed riêng ngoài migration để không chạy trên production.

`powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part3.ps1` tạo DB ticketing_it_UUID mới, chạy toàn bộ test và drop đúng DB đó trong finally. Không xóa volume hay DB demo. Có thể chọn gate bằng `-Tests PostgresSchemaTest`, `PostgresSeedTest`, `PostgresCatalogTest`, `PostgresAdminTest`, `PostgresOutboxTest`. Kết quả tại backend/target/surefire-reports. Không có PG_IT_URL thì Maven chỉ chạy context H2, không phải nghiệm thu integration.

Launcher chạy bản sao JAR ở infra/.local-runtime (Git ignore) để Windows không khóa artifact trong backend/target khi build/test tiếp. Dừng JVM cũ trước khi chạy launcher lại.

```powershell
docker compose --env-file .env -f infra/docker-compose.yml stop postgres
```

Lệnh stop giữ volume. Không dùng `down -v` với dữ liệu cần giữ: lệnh đó xóa database demo. Muốn kiểm chứng lại từ DB sạch nên dùng volume/project demo riêng; lần nghiệm thu T1.3 dùng volume mới, xác nhận 0 bảng trước startup rồi kiểm tra restart trên cùng volume.

Baseline và ngân sách tài nguyên: [ADR 0001](../docs/adr/0001-stack-and-local-environment.md). PostgreSQL 5432 và backend 8080 chỉ bind 127.0.0.1. `.env` không commit. Không thêm Redis khi chưa có ADR.

## Phần4 — Booking GA

```powershell
# PostgreSQL thật, toàn bộ35test backend; không reset demo:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4.ps1
# Có thể chọn16test booking: thêm -Tests PostgresBookingTest
npm.cmd ci --prefix frontend
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
# JAR vừa build, DB UI riêng sạch, HTTP thật và React DOM:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4-ui.ps1
# App demo8080 phải đang chạy:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part4.ps1
npm.cmd run test:live --prefix tools/contracts
# Terminal giao diện:
npm.cmd run dev --prefix frontend
```

Mở http://127.0.0.1:5173, chọn GA và đặt1..6vé. verify-part4 giữ1vé lần đầu, sau đó replay key cố định; contract live giữ thêm1vé bằng key khác. Các test cô lập không thay inventory demo. V5 tạo idempotency_keys/booking_status_history và deferred commit guard; V6 chặn initial history sai. Migrate lần hai không chạy lại version đã áp dụng. README gốc có ví dụ POST/replay/conflict/GET/history.

test-part4-ui dùng cổng loopback tự chọn, JAR tạm và DB ticketing_ui_UUID do chính script tạo. finally dừng đúng process test, drop đúng DB đó; app demo8080 được giữ nguyên. Kết quả live là DOM+API/PostgreSQL thật, chưa phải browser visual QA. Không có worker nhả hold khi TTL qua hạn trong phần này.
