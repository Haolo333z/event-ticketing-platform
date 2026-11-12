# PROGRESS — Nhật ký tiến độ

> Cập nhật sau mỗi task hoặc quyết định ảnh hưởng phạm vi. Ngày dùng múi giờ Việt Nam.

## Trạng thái hiện tại

- **Giai đoạn:** Môi trường local tối thiểu đã chạy
- **Tiến độ:** T1.1, T1.2, T1.3 Done; PostgreSQL/health/Flyway nền đã kiểm chứng; chưa có nghiệp vụ
- **Task đang làm:** Chưa có
- **Task tiếp theo:** T1.4 — Chốt API/domain v0 (Todo, chưa bắt đầu)
- **Blocker:** Không có blocker cho T1.3. Thay đổi T1.3 mới kiểm chứng local, chưa có CI cho commit mới.

- **Task trong TASKS:** 3/45 Done. Có scaffold, health endpoint, PostgreSQL và migration schema nền; chưa có bảng/nghiệp vụ booking.

## Đã hoàn thành

- PRD.md: expiry tách biệt, điều kiện nhận lại inventory, demo auth local/test và late payment.
- ARCHITECTURE.md: Expiry Worker, lock order, race payment/expiry, outbox version, mock auth.
- DESIGN.md: persona demo, thông báo chờ nhả inventory và trạng thái EXPIRED/REFUND_REQUIRED.
- RULES.md: không chiếm hold quá hạn trực tiếp, auth demo có giới hạn, state transition thống nhất.
- TASKS.md: concurrency test GA/idempotency tuần 3; seat/expiry worker tuần 4.
- PROGRESS.md: cập nhật quyết định và bước tiếp theo.

## Quyết định đã thống nhất

- Dự án cá nhân 10 tuần, ưu tiên backend, demo local, không dùng .NET.
- Stack đã chốt tại [ADR 0001](adr/0001-stack-and-local-environment.md): Temurin JDK 25.0.5+8 LTS, Spring Boot 4.1.2, Maven Wrapper 3.9.16, PostgreSQL 17.11, Kafka 4.2.2, Node 26.13.0/npm 11.20.0, React 19.3.1, TypeScript 7.0.2, Vite 8.3.8; Docker 29.9.1/Compose 5.6.0. Không dùng Redis trong baseline.
- Modular monolith hướng sự kiện; payment mô phỏng.
- PostgreSQL là nguồn sự thật; consumer at-least-once và idempotent.
- Mock bearer token customer/admin seed sẵn, chỉ local/test; cổng demo bind localhost; không phải auth production.
- Booking mới không giải phóng/ghi đè hold hết hạn. Expiry Worker phải commit nhả inventory trước khi tài nguyên được nhận lại.
- Late success dẫn tới EXPIRED -> REFUND_REQUIRED; lưu history, không phát vé, không nhận lại inventory.
- GA quantity N tạo N vé theo ticket_ordinal trong transaction confirm; BookingConfirmed chỉ thông báo. Một payment chỉ có một nghĩa vụ bù trừ.
- Payment consumer dùng inbox và kết quả bền, không bỏ event theo bookings.aggregate_version; projection chỉ kiểm tra gap nếu nhận đầy đủ event của booking.

## Rủi ro / việc cần xác nhận

- T1.1 đã chốt version, TTL 600s, worker 5s/batch 50, tối đa 6 vé/booking, pool max 10/min idle 2. Đây là cấu hình ban đầu, chưa benchmark; timeout/body limit/CORS tiếp tục chốt ở T1.4.
- Worker trễ có thể khiến inventory hết hạn chưa đặt lại được; API trả conflict có thể thử lại để giữ invariant.
- Lock order, rollback nhiều ghế, race worker/payment và aggregate version cần được chứng minh bằng integration/concurrency tests.
- Có scaffold source và test mẫu; chưa có schema cuối, OpenAPI hoàn chỉnh hoặc test nghiệp vụ/integration/concurrency. Test context không chứng minh health/API/DB/Kafka hoạt động.
- Lock timeout cần cấu hình và đo; phải rollback trước khi trả lỗi, phân biệt chờ idempotency với tranh chấp inventory. Body limit/CORS chốt ở T1.4, không áp giá trị tùy ý.

## Nhật ký

### 2026-11-12 — Xác minh CI T1.2 và thực hiện T1.3

- **CI đúng commit:** `08b144767695876019be5727d679355752395ce3`, thông điệp “chốt môi trường và tạo khung dự án”, branch main, event push; [CI run 43157891733](https://github.com/Haolo333z/event-ticketing-platform/actions/runs/43157891733), attempt 1, completed/success. Đối chiếu head_sha từ GitHub API và git log, không lấy kết quả Dependabot thay CI.
- **Jobs/steps:** backend (130981977983), frontend (130981978273), secrets (130981978288) đều success. Các bước verify/test/SBOM, OSV, npm ci/test/build/audit, Gitleaks full history/working tree đều success. T1.2 đạt acceptance và chuyển Done.
- **T1.3 triển khai:** Compose project event-ticketing-local, PostgreSQL 17.11, volume demo riêng, healthcheck, RAM limit 1 GiB/max_connections 50. Backend dùng Spring MVC/Actuator/JDBC/Flyway starter và PostgreSQL driver theo Boot BOM. Chỉ expose health, ẩn chi tiết; bind backend 8080 và PostgreSQL 5432 trên 127.0.0.1. V1 tạo schema ticketing, history cố định public, tắt Flyway clean/baseline tự động. Không triển khai schema nghiệp vụ trước T1.4.
- **Cấu hình/lệnh:** script infra/start-local.ps1 tạo .env nếu thiếu với mật khẩu ngẫu nhiên, giữ .env đã có, nạp env cho backend/Compose, đợi DB rồi build/test và chạy java -Duser.timezone=UTC. infra/verify-local.ps1 kiểm tra HTTP/status/schema/migration. Lệnh đầy đủ tại infra/README.md và README gốc.
- **Kiểm tra sạch thực tế:** trước task không có Docker volume; tạo volume event-ticketing-local_postgres_demo riêng. Sau sửa cấu hình, tái tạo đúng volume demo do task này tạo, không tác động container test-backend/test-frontend có sẵn. DB ticketing_demo có 0 bảng người dùng trước startup. PostgreSQL báo 17.11. Flyway log “Successfully applied 1 migration”, version v1; query public.flyway_schema_history có đúng một V1 success, schema ticketing tồn tại. HTTP health 200/status UP. Script verify-local PASS.
- **Kiểm tra restart:** khởi động lại JAR trên cùng volume: validate 1 migration, “Schema public is up to date. No migration necessary”; verify-local tiếp tục PASS, V1 vẫn đúng một lần. Lần xác minh cuối PostgreSQL healthy và hai cổng chỉ bind 127.0.0.1. Backend đang chạy PID 20128 tại lúc bàn giao; có thể `Stop-Process -Id 20128` trước khi chạy lại script (chỉ dùng PID khi vẫn là tiến trình Java này).
- **Build/scan:** Maven Wrapper verify BUILD SUCCESS, context test 1/0 fail. Test context dùng H2, tắt Flyway; không dùng kết quả H2 để nghiệm thu PostgreSQL. SBOM mới có 63 dependency runtime, OSV 2.7.1 No issues found. Gitleaks quét tập file Git tracked và untracked không bị ignore: no leaks found; .env local ngẫu nhiên bị Git ignore và không nằm trong tập bàn giao. git diff --check đạt. CI success nêu trên chỉ thuộc commit T1.2, chưa phải CI T1.3.
- **Lỗi đã xử lý:** Windows ExecutionPolicy chặn script, dùng Bypass riêng process, không đổi policy hệ thống. JDBC gửi Asia/Saigon bị DB từ chối, launcher cố định UTC. PostgreSQL search_path mặc định có $user=ticketing nên schema ticketing mới có thể làm Flyway chuyển vị trí history ở lần restart; đã đặt default-schema=public và nghiệm thu lại DB sạch, không sửa migration đã áp dụng. PowerShell 5 trả health Content dạng byte[], script giải mã UTF-8 trước parse JSON.
- **Phạm vi còn lại:** T1.4 vẫn Todo; chưa có bảng domain, auth demo, Kafka, concurrency test hoặc benchmark. T1.3 đạt start/health/migration sạch và restart; chưa commit/push thay đổi lần này.

Lệnh khởi động từ thư mục gốc (dừng backend hiện tại trước nếu còn chạy):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
```

Terminal khác:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

### 2026-11-12 — T1.2: Scaffold repository và CI

- **Bằng chứng JDK mới:** `java -version` trả `openjdk version "25.0.5"`, `Temurin-25.0.5+8`, LTS; `javac -version` trả `25.0.5`. `JAVA_HOME/bin/javac.exe` tồn tại và Maven Wrapper build bằng JDK này thành công. Kết quả Java 26 trong nhật ký T1.1 là quan sát cũ, đã được thay thế bởi kiểm tra mới này.
- **Kiểm tra hiện trạng trước sửa:** Git và docs đã tồn tại; origin vẫn là `https://github.com/Haolo333z/event-ticketing-platform.git`; có README gốc và các thay đổi tài liệu T1.1 chưa commit. Giữ các thay đổi này, bổ sung README; không git init, không thay remote, không commit/push.
- **Đã tạo:** backend Spring Boot 4.1.2, Maven Wrapper 3.3.4 pin Maven 3.9.16 và SHA-256 distribution, Java target 25; package dành sẵn cho 10 module trong ARCHITECTURE; resources/migration chưa có SQL. Frontend React 19.3.1/TypeScript 7.0.2/Vite 8.3.8, lockfile, Node test runner; chưa cần plugin JSX vì scaffold dùng createElement. infra/README dành cho Compose về sau. Thêm .gitignore, .gitattributes, .env.example chỉ placeholder, Gitleaks config, CI và Dependabot.
- **CI:** ba job backend/frontend/secrets trên push, PR, workflow_dispatch; verify + context test + CycloneDX SBOM + OSV backend; npm ci/test/build/audit (high trở lên); Gitleaks full Git history và working tree. Scanner failure không bị bỏ qua; permission contents:read; tải scanner có kiểm tra checksum trong workflow. Không cần NVD API key hoặc license Gitleaks Action.
- **Kiểm tra local đạt:** Maven Wrapper `verify cyclonedx:makeAggregateBom` BUILD SUCCESS, 1 test context/0 fail; tạo executable JAR và SBOM 21 components. `npm ci`, `npm test` (1 pass), `npm run build` (TypeScript + Vite), `npm audit --audit-level=high` (0 vulnerabilities). OSV Scanner 2.7.1 scan SBOM: 21 packages, No issues found. Gitleaks 8.30.1: 2 commits và working tree, no leaks found, exit 0 cả hai. actionlint 1.7.12 kiểm tra workflow exit 0. git diff --check đạt; Git ignore .env/node_modules/target/dist, không ignore .env.example.
- **Lỗi đã sửa:** TypeScript 7 cần khai báo types node/react/react-dom tường minh; build frontend đã đạt sau sửa. JVM có cảnh báo dynamic agent khi chạy test, test vẫn đạt.
- **Còn thiếu để Done:** workflow mới chưa được commit/push và chưa có GitHub Actions run thành công; do acceptance yêu cầu CI chạy, T1.2 giữ In Progress. Scan local là kết quả tại thời điểm kiểm tra, không bảo đảm không có lỗ hổng mới. Scanner backend hiện kiểm tra dependency runtime từ SBOM (test scope chưa nằm trong SBOM).
- **Bước tiếp theo:** review/commit/push các file đã chuẩn bị, xem ba job CI; nếu tất cả xanh thì cập nhật T1.2 Done và tổng 2/45. Chưa thực hiện T1.3, chưa start DB/Kafka, health endpoint hay migration.

Lệnh kiểm tra lại (PowerShell, từ thư mục gốc):

```powershell
cd backend
.\mvnw.cmd -B -ntp verify cyclonedx:makeAggregateBom
cd ../frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd audit --audit-level=high
cd ..
git diff --check
git status --short
```

Sau khi review và commit, chạy `git push` để kích hoạt CI; kiểm tra tab Actions của repository. Không đánh dấu CI đã chạy từ kết quả actionlint/local.

### 2026-11-12 — T1.1: Chốt stack và môi trường

- **Hoàn thành:** ADR tương thích/phiên bản, quyết định không Redis, tham số ban đầu và ngân sách máy; T1.1 Done theo acceptance tài liệu và kiểm tra môi trường.
- **Files thay đổi:** docs/adr/0001-stack-and-local-environment.md, TASKS.md, PROGRESS.md; ARCHITECTURE.md dẫn tới quyết định đã chốt.
- **Kiểm tra thực tế:** Java/javac 26.0.1; Node 26.13.0/npm 11.20.0; Git 2.55.0.windows.3. `mvn` không có trên PATH. Docker Client/Server 29.9.1, Compose 5.6.0; daemon Linux x86_64, 12 CPU, RAM 7,65 GiB. Windows 11, i5-11400H 6 core/12 thread, RAM 15,78 GiB (trống 2,58 GiB), D: trống 130,62 GiB. Không có container chạy hoặc listener ở bốn cổng dự kiến tại thời điểm đo.
- **Tương thích:** tài liệu Boot 4.1.2 hỗ trợ Java 17–27; Java 26 phù hợp Boot nhưng non-LTS. Kafka hỗ trợ đầy đủ Java 25 nên chọn JDK 25 LTS. Tra Boot BOM, PostgreSQL support, Maven, Adoptium, Node/Vite và npm registry; manifest postgres:17.11 và apache/kafka:4.2.2 có linux/amd64. Chi tiết nguồn và lệnh trong ADR.
- **Còn thiếu/giới hạn:** chưa cài/chọn JDK 25, chưa có wrapper, chưa pull/start DB/Kafka hay build/test ứng dụng. RAM trống thấp, cần đóng ứng dụng nặng trước demo. Không có benchmark để khẳng định pool/worker đáp ứng tải.
- **Bước tiếp theo:** chuẩn bị JDK 25 theo lệnh trong ADR; T1.2 vẫn Todo. Lần này dừng ở T1.1; T1.3 kiểm chứng health/migration, T1.4 chốt hợp đồng và timeout.

### 2026-11-12 — Hiệu chỉnh tài liệu v0.4

- **Hoàn thành tài liệu:** sửa ticket_ordinal cho vé GA, thêm payment_compensations, sửa quy tắc version theo consumer; đồng bộ API timeout, task và test tuần 3/5/6. Giữ test concurrency tuần 3–4 và state EXPIRED -> REFUND_REQUIRED.
- **Triển khai source:** 0; **SQL migration/test phần mềm:** 0; **nghiệm thu:** chưa thực hiện.
- **Kiểm tra tài liệu:** đối chiếu schema, state, event, API, lịch task và ZIP. Bằng chứng chạy thực tế chỉ có sau khi triển khai.
- **Bước tiếp theo:** T1.1, T1.4 rồi migration/outbox trước booking tuần 3.

### 2026-11-12 — Nâng cấp tài liệu v0.3

- **Hoàn thành tài liệu:** sửa 6 file, thêm README, DATA_MODEL, API_CONTRACT, EVENT_CATALOG, TEST_PLAN, DEMO_AND_RECOVERY và UPGRADE_REPORT. Sửa thứ tự outbox, payment, task thiếu; phân xử payment theo result bền.
- **Triển khai source:** 0; **kiểm thử phần mềm:** 0; **nghiệm thu hệ thống:** chưa thực hiện.
- **Kiểm tra tài liệu:** đối chiếu scope/API/event/task, liên kết và ZIP; kết quả ghi UPGRADE_REPORT.
- **Bước tiếp theo:** T1.1 rồi T1.4; schema/outbox trước booking tuần 3.

### 2026-11-11 — Cập nhật tài liệu sau phản biện Phần 9

- **Hoàn thành:** cập nhật đầy đủ sáu file nền.
- **Thay đổi chính:** thay danh tính do client khai báo bằng mock bearer token seed trong local/test; tách nhả hold của Expiry Worker khỏi lệnh tạo booking; chuyển concurrency test GA/idempotency về tuần 3 và seat/expiry về tuần 4; chuẩn hóa EXPIRED -> REFUND_REQUIRED.
- **Kiểm tra:** rà chéo thuật ngữ, state machine, điều kiện nhận lại inventory và lịch task giữa sáu tài liệu. Không chạy test phần mềm vì chưa có source code.
- **Lỗi/blocker:** không có blocker mới; version stack và schema chi tiết chờ T1.1/T1.4.
- **Bước tiếp theo:** làm T1.1 rồi T1.2; scaffold nghiệp vụ sau khi nền tảng được chốt.

### 2026-11-11 — Khởi tạo bộ tài liệu

- **Hoàn thành:** tạo sáu tài liệu nền ban đầu.
- **Kiểm tra:** thống nhất phạm vi MVP và vai trò database/broker ở mức khởi đầu.
- **Lỗi còn lại:** version stack chưa chốt; chưa có code.
- **Bước tiếp theo:** T1.1.

## Theo dõi giai đoạn

| Giai đoạn | Tài liệu | Source | Test | Nghiệm thu |
|---|---|---|---|---|
| Trước scaffold | v0.4 + ADR 0001; T1.1 Done, T1.4 Todo | Chưa | CLI/tài nguyên/tương thích đã kiểm tra; chưa test ứng dụng | T1.1 đạt; chưa nghiệm thu hệ thống |
| Tuần 2–4 | Đặc tả nền có sẵn | Chưa | Chưa | Chưa |
| Tuần 5–10 | Theo TASKS | Chưa | Chưa | Chưa |

## Mẫu cập nhật sau mỗi task

    ### YYYY-MM-DD — Tn.n: Tên task
    - Hoàn thành:
    - Files thay đổi:
    - Kiểm tra đã thực hiện và kết quả:
    - Lỗi/blocker còn lại:
    - Quyết định/tài liệu cần cập nhật:
    - Bước tiếp theo:
