# Hạ tầng local

## T6 — Kafka và test cô lập

Local: `powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -HoldSeconds 20 -ExpiryIntervalSeconds 1`; frontend `npm.cmd run dev --prefix frontend`, http://127.0.0.1:5173. Giữ `.env` hiện có; local bật messaging mặc định, bootstrap127.0.0.1:9092. Đổi cổng cần đổi đồng bộ KAFKA_PORT/bootstrap.

- `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part7.ps1`: toàn backend với PostgreSQL/Kafka thật; hai DB và broker/volume UUID riêng. `-Tests KafkaRecoveryTest` chỉ gate Kafka. Không stop broker demo.
- `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1 -Kafka`: HTTP OpenAPI, checkout GA/ghế/vé, expiry/late success, restart JVM và refund worker; DB/topic/group riêng, không ngắt broker demo.
- `powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part7.ps1`: tạo fixture local, GA2, chờ consumer confirm, replay notification hai lần cùng request, đối chiếu PostgreSQL.

Log suite: backend/target/surefire-reports; evidence: backend/target/part7-kafka-evidence.json. UI giữ startup/restart log trong TEMP tên ticketing_payment_ui_UUID, dọn JVM/DB/copyJAR do script tạo. [Runbook recovery](../docs/DEMO_AND_RECOVERY.md#t6--quy-trình-đã-triển-khai).

Các mục Phần3–6 dưới đây là hướng dẫn và bằng chứng lịch sử; T6 thay ghi chú “chưa Kafka/inbox/refund worker”.

PostgreSQL17.11 chạy Compose,backend JDK25 trên host. Phần6 có V1–V8,GA/ghế/expiry và payment simulator/processor/cancel/tickets/compensationPENDING. Chưa có Kafka/inbox/refund worker; ghi chú ở từng mục Phần4/5 là lịch sử của các phần đó.

Từ thư mục gốc trên Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
```

Script tạo `.env` nếu chưa tồn tại với mật khẩu ngẫu nhiên, không ghi mật khẩu ra console; giữ file đã có. Nó nạp các dòng KEY=value vào môi trường process, đợi PostgreSQL healthy, build/test backend rồi chạy JVM foreground với timezone UTC. `ExecutionPolicy Bypass` chỉ áp dụng process được gọi, không đổi policy hệ thống. Ctrl+C dừng backend. Nếu backend đang chạy từ lần trước, dừng nó trước để tránh chiếm cổng 8080.

Có thể dùng `-RestartExisting` để thay đúng JVM runtime của workspace khi cổng8080 bận; app khác bị từ chối. Build thành công trước khi thay JVM, không stop theo tên Java chung. `-HoldSeconds 20 -ExpiryIntervalSeconds 1` cho demo nhanh, chỉ override process sau khi đọc `.env`, không ghi lại file. Nếu không truyền, dùng TTL600s/interval5s mặc định hoặc cấu hình `.env`. Launcher chạy context test H2 khi thiếuPG_IT_URL; nghiệm thu đầy đủ phải dùng test-part5.ps1.

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

## Phần5 — Ghế và worker expiry

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5.ps1
# Chỉ23test mới: thêm -Tests PostgresSeatExpiryTest
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4-ui.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5-ui.ps1
# Backend demo nhanh ở terminal riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part5.ps1 -WaitSeconds 45
```

test-part5.ps1 tái sử dụng runner DB UUID độc lập, toàn suite58test. test-part5-ui tạo DB/cổng riêng, TTL4s/worker1s, kiểm tra22response contract, chọn/giữ ghế trong React DOM qua HTTP thật, chờ schedulerEXPIRED/nhả/rebook, cả GA expiry; query invariant và restart JVM test cùng DB kiểm tra không migrate lại. Cuối cùng chỉ dọn process/JAR/DB do script tạo. Không reset demo hoặc stop runtime8080.

Scheduler tắt trong test profile để test tự điều khiển, nhưng test-part5-ui bật bằng CLI. Local scheduler mặc định bật; EXPIRY_ENABLED=false chỉ để điều tra/test có kiểm soát, hold sẽ chưa nhả khi tắt. Mỗi booking một transaction; log pass có candidates/expired/deferredPayment/busy/failed. Result payment bền giữ inventory chờ T5.2; chưa có simulator/confirm/refund. V7 thêm composite owner FK; event payload/schemaVersion không đổi. verify-part4 vẫn kiểm tra replay GA gốc, GET sau expiry có thể làEXPIRED. Các ghi chú “chưa worker” trong mục Phần4 bên trên là phạm vi lịch sử, không phải hiện trạng Phần5.

## Phần6 — Thanh toán mô phỏng và vé

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6.ps1
# Chỉ30test mới: thêm -Tests PostgresPaymentTest
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1
# Demo nhanh, terminal backend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part6.ps1 -WaitSeconds 45
```

test-part6 tạo DB riêng và chạy88test backend, không reset demo. test-part6-ui dùng DB/cổng/JAR riêng,TTL4s/worker1s,40response HTTP contract và checkout React+API thật; kiểm tra GA3/ghế2vé,duplicate,failure/cancel,delayed refund0vé. Tạo paymentSCHEDULED rồi restart đúng JVM test,cùng DB; ledger phục hồi và không migrate lại. SQL kiểm tra state/ordinal/owner/inventory/compensation và8version migration. finally chỉ dọn DB/JAR/process do script tạo.

Scheduler hiện tại gọi PaymentMaintenance trước ExpiryService; logPaymentPass có candidates/processed/failed,ExpiryPass có expired/processedPayments/busy/failed. T5 không cònDEFERRED_PAYMENT. Test properties/CLI chốt lịch tắt hoặc bật từng ca, kể cảenvEXPIRY_ENABLED=true. Nếu tắt scheduler thì delayed result/expiry/recovery chưa tự chạy; immediate simulator vẫn gọi local processor sau record. Không có refund worker: compensation giữPENDING.

verify-part6 tạo catalog riêng trên demo và giữ dữ liệu/vé để bạn xem, không xóa seed. Vé cócodeEF-UUID để tra cứu demo qua API có owner/admin,không thêm check-in/QRproduction. README gốc có ví dụPOSTsimulate/GETtickets/cancel khôngbody và giải thích retryrequestId. Chưa commit/push hay chạy GitHub CI mới.
