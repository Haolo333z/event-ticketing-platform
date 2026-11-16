# event-ticketing-platform — EventFlow Enterprise V2

Hệ thống phân phối vé sự kiện tốc độ cao và vận hành toàn diện EventFlow V2. Kiến trúc Modular Monolith hướng sự kiện (EDA) kết hợp PostgreSQL 17 ACID và Apache Kafka 4.2, cam kết 0% bán lố vé (Zero Oversell), xử lý phân bổ chỗ ngồi độc quyền, tích hợp cổng thanh toán Sandbox (VNPAY), phát hành vé điện tử E-Ticket QR Code SVG ký số HMAC-SHA256, bàn làm việc soát vé thời gian thực cho Staff và cổng quản trị hệ thống toàn sàn cho Admin. **Không thu tiền thật; cổng thanh toán Sandbox chỉ dùng local/test.**

## Kiến trúc và luồng nghiệp vụ V2

Modular monolith Java LTS (JDK 25) / Spring Boot 4.1.2: HTTP API và worker cùng ứng dụng, chia module định danh 4 vai trò, nhà tổ chức, địa điểm, đặt vé/tồn kho, cổng thanh toán Hexagonal, soát vé check-in, messaging và platform operations. React 19 / TypeScript gọi API thật. Java/Node chạy trên host Windows, PostgreSQL/Kafka chạy Docker Compose.

| Thành phần | Trách nhiệm |
|---|---|
| PostgreSQL 17 | Nguồn sự thật: 35 bảng nghiệp vụ (V1–V15), khóa hàng nguyên tử `SELECT FOR UPDATE`, quyền giữ ghế/GA, giá snapshot, vé, phiên token family, IPN journal |
| Flyway | V1–V15, 35 bảng nghiệp vụ; tự động kiểm tra tính toàn vẹn (validate) và thực thi migration sạch |
| 4-Role RBAC | Phân quyền 4 vai trò chuẩn hóa: `CUSTOMER`, `STAFF`, `ORGANIZER`, `ADMIN`; Token Family Session an toàn qua HttpOnly Cookie |
| Cổng Thanh Toán | Kiến trúc Lục giác (Ports & Adapters): Tích hợp song song Cổng thanh toán VNPAY Sandbox (HMAC-SHA512) và Simulator nội bộ; xử lý IPN Idempotent và trọng tài hoàn tiền muộn (Late Refund Arbitration) |
| Outbox → Kafka | Publisher đọc **sau commit**, gửi theo booking/version, retry hữu hạn và lưu lỗi bền |
| Consumer + inbox | Inbox và thay đổi nghiệp vụ cùng transaction PostgreSQL; khử trùng lặp tiêu thụ (At-least-once + Dedup) |
| Soát Vé Check-in | Giao dịch soát vé nguyên tử (`ticket_check_ins`), xác thực chữ ký số QR Token HMAC-SHA256, chống 100% tình trạng quét trùng vé (Anti Double Check-in) |
| Expiry/refund worker | Nhả hold đúng một lần theo đồng hồ DB, xử lý nghĩa vụ hoàn tiền mô phỏng |
| Platform Admin | Quản trị người dùng & phân quyền, duyệt hoàn tiền 2 bước, báo cáo KPI & xuất CSV chống Formula Injection, tiếp nhận leads doanh nghiệp |

GA: chọn 1–6 vé → `POST /api/bookings` có `Idempotency-Key` → booking `PENDING_PAYMENT`, giữ inventory, history và outbox cùng commit → HTTP 202. Ghế: chọn ID ghế, giữ toàn bộ hoặc rollback toàn bộ. Availability chỉ tham khảo, PostgreSQL quyết định giữ được hay không. Cùng key/nội dung trả kết quả cũ; cùng key/nội dung khác trả 409. Lỗi mạng/503: dùng **Thử lại cùng yêu cầu**, giữ key/body qua reload cùng tab.

Thanh toán mô phỏng ghi kết quả bền/outbox trước; consumer xác nhận success hợp lệ thành `CONFIRMED`, chuyển held sang sold và phát đúng số vé trong cùng transaction. 202 chỉ là đã nhận, UI tiếp tục đọc API. Failure → `PAYMENT_FAILED`; hủy → `CANCELLED`; hết hạn → `EXPIRED` sau khi worker commit nhả inventory. Countdown về 0 không tự nhả ghế. Success muộn sau khi mất hold không phát vé; luồng hết hạn chuyển `REFUND_REQUIRED` và theo dõi bù trừ mô phỏng.

Phục hồi: Kafka tạm ngừng thì outbox/lỗi còn ở PostgreSQL. Bật lại broker để retry; hết số lần tự thử thì ADMIN điều tra và replay event gốc. Inbox/state guard ngăn ghi nghiệp vụ lặp. Đây là **at-least-once kèm deduplication**, không cam kết exactly-once hay chịu mất ổ đĩa. Xem [kiến trúc](docs/ARCHITECTURE.md), [API](docs/API_CONTRACT.md), [event](docs/EVENT_CATALOG.md), [runbook](docs/DEMO_AND_RECOVERY.md).

## Yêu cầu môi trường

Baseline theo [ADR0001](docs/adr/0001-stack-and-local-environment.md); không tự nâng version khi chạy hướng dẫn:

| Công cụ | Phiên bản đã chốt |
|---|---|
| Java và javac | Eclipse Temurin **25.0.5+8**, target 25 |
| Node / npm | **26.13.0 / 11.20.0** |
| Maven | Wrapper **3.9.16** trong backend, không cần Maven global |
| Docker Desktop | Linux containers; Engine **29.9.1**, Compose **5.6.0** đã kiểm chứng |
| PostgreSQL / Kafka | Image pin **17.11 / 4.2.2**, Compose tự tải |
| Ứng dụng | Boot **4.1.2**, React **19.3.1**, TypeScript **7.0.2**, Vite **8.3.8**; BOM/lockfile giữ dependency |

Cần Git, Windows PowerShell 5.1+, Internet lần đầu để tải Git/Maven/npm/image và đủ đĩa. Compose giới hạn PostgreSQL 1 GiB, Kafka 2 GiB; full test còn broker riêng và JVM/Node. Máy kiểm chứng RAM 16 GiB, Docker khoảng 8 GiB; đây là cấu hình đã thử, chưa đo minimum.

```powershell
git --version
java -version
javac -version
node --version
npm.cmd --version
docker version
docker compose version
```

Docker phải có **Client và Server**. `JAVA_HOME`/PATH trỏ cùng JDK 25. Dùng `npm.cmd` để tránh policy chặn npm.ps1.

## Chuẩn bị từ clone mới

```powershell
git clone https://github.com/Haolo333z/event-ticketing-platform.git
Set-Location event-ticketing-platform
powershell -NoProfile -ExecutionPolicy Bypass -File infra/local-env.ps1
npm.cmd ci --prefix frontend
npm.cmd ci --prefix tools/contracts
```

Các lệnh sau chạy từ **gốc repository**. `local-env.ps1` tạo `.env` từ `.env.example` khi chưa có, sinh mật khẩu ngẫu nhiên, không in password và không ghi đè file đã có. Nó đọc KEY=value như dữ liệu; không thực thi nội dung. Bypass chỉ áp dụng process được gọi, không đổi policy hệ thống. Không commit `.env`.

Mặc định không cần sửa `.env`. Nếu chỉnh bằng editor, dùng KEY=value đơn giản, không dấu nháy/comment cuối dòng:

| Biến | Mặc định / ý nghĩa |
|---|---|
| SPRING_PROFILES_ACTIVE | local; mock auth/seed chỉ local/test |
| POSTGRES_DB / POSTGRES_USER | ticketing_demo / ticketing |
| POSTGRES_PASSWORD | Đã sinh; không rỗng, không đổi tùy tiện khi volume đã khởi tạo |
| DB_HOST / DB_PORT | 127.0.0.1 / 5432 |
| SERVER_PORT | 8080, cổng backend Windows |
| KAFKA_PORT / KAFKA_BOOTSTRAP_SERVERS | 9092 / 127.0.0.1:9092; đổi đồng thời |
| VITE_API_BASE_URL | http://127.0.0.1:8080; đổi theo backend, chạy lại frontend sau khi đổi |
| BOOKING_HOLD_TTL_SECONDS | 600; worker mỗi 5 giây (EXPIRY_INTERVAL_SECONDS) |
| COMPOSE_PROJECT_NAME | Tùy chọn project riêng; tách container/volume, cần đổi cả cổng khi chạy song song |

`.env.example` cố ý để password rỗng. Nếu tự copy thành `.env`, phải điền password trước khi chạy. Không ghi đè `.env` đang dùng.

## Khởi động và dừng trên Windows

**Terminal 1 — giữ mở cho backend:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
```

Script đọc `.env`, đợi PostgreSQL/Kafka healthy, build Maven Wrapper, chạy Flyway/seed local rồi giữ JVM foreground/UTC. Seed chỉ thêm bản ghi thiếu, không reset dữ liệu cũ. Build trong launcher có thể **skip test tích hợp** khi thiếu biến IT; dùng gate đầy đủ bên dưới.

**Terminal 2 — giữ mở cho frontend:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-frontend.ps1
```

Script đọc VITE_API_BASE_URL từ `.env` gốc, dùng Vite đã cài, bind 127.0.0.1:5173 strict. Không tự chuyển cổng: CORS chỉ cho http://127.0.0.1:5173 và http://localhost:5173.

**Terminal 3 — kiểm tra, đóng được sau khi xong:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
docker compose --env-file .env -f infra/docker-compose.yml ps
```

Mong đợi `PASS: HTTP 200 / UP; V1 applied exactly once; ticketing schema exists.` và hai container healthy.

| Địa chỉ | Nội dung |
|---|---|
| http://127.0.0.1:5173 | Giao diện khách / Quản trị demo |
| http://127.0.0.1:8080/api/events | Catalog public |
| http://127.0.0.1:8080/actuator/health | Tổng hợp dependency cần thiết, 200/UP khi sẵn sàng |
| http://127.0.0.1:8080/actuator/health/readiness | DB + Kafka khi messaging bật |
| http://127.0.0.1:8080/actuator/health/liveness | Tiến trình sống; có thể UP khi dependency DOWN |

OpenAPI máy đọc tại [docs/contracts/openapi.json](docs/contracts/openapi.json); không có Swagger UI `/swagger-ui`. Metrics chỉ ADMIN local/test: `/actuator/metrics` và `/actuator/metrics/{name}`, xem [tên/đơn vị](docs/API_CONTRACT.md). Log JSON trên terminal backend truyền correlation ID, booking ID/message event ID khi có ngữ cảnh; không ghi token/payload thanh toán.

Thử expiry nhanh: Ctrl+C backend rồi chạy (hoặc dùng -RestartExisting để thay đúng runtime workspace):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1
```

Override chỉ trong process, không sửa `.env`. Dừng bình thường bằng Ctrl+C hai terminal. Hoặc từ terminal khác dừng ứng dụng và Docker project, **giữ volume**:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/stop-local.ps1 -StopServices
```

Script xác minh đường dẫn JVM/Vite thuộc workspace, từ chối tiến trình lạ; compose stop giữ dữ liệu. Không cần reset DB hoặc down --volumes để chạy lại.

## Tài khoản đăng nhập và 4 vai trò EventFlow V2

Hệ thống hỗ trợ cả 2 phương thức xác thực: Đăng nhập thực tế (Email/Password & Google Sign-In OIDC) và Bearer token demo cố định (chỉ dành cho môi trường local/test):

| Vai Trò | Email Đăng Nhập Mặc Định | Mật Khẩu | Bearer Token Test | Cổng Giao Diện Chính |
|---|---|---|---|---|
| **CUSTOMER** | `customer@eventflow.vn` | `Customer@123456` | `demo-customer-token` | `/` (Trang chủ), `/dat-ve` (Đặt vé), `/ve-cua-toi` (Ví vé QR SVG) |
| **STAFF** | `staff@eventflow.vn` | `Staff@123456` | `demo-staff-token` | `/staff` (Bàn làm việc soát vé QR Scanner & Check-in Terminal) |
| **ORGANIZER** | `organizer@eventflow.vn` | `Organizer@123456` | `demo-organizer-token` | `/organizer` (Hồ sơ đối tác, tạo sự kiện, phân bổ nhân sự) |
| **ADMIN** | `admin@eventflow.vn` | `Admin@123456` | `demo-admin-token` | `/admin` (5 tab quản trị người dùng, duyệt hoàn tiền, báo cáo, leads) |

### Trải nghiệm các phân hệ chính trên trình duyệt:

1. **Khách Hàng (Customer Commerce):** Truy cập `http://127.0.0.1:5173/dat-ve`, chọn sự kiện, chọn vé GA hoặc ghế chỉ định trên sơ đồ Stage 3D trực quan. Giữ vé 10 phút bảo vệ bằng cơ chế `SELECT FOR UPDATE`. Thanh toán qua cổng VNPAY Sandbox hoặc 3 kịch bản mô phỏng. Sau khi thành công, vào `/ve-cua-toi` để xem thẻ Boarding Pass E-Ticket và mã QR Code SVG ký số HMAC-SHA256, hỗ trợ in vé điện tử A4.
2. **Nhân Viên Soát Vé (Staff Check-in):** Truy cập `/staff`, chọn sự kiện được phân công, quét mã QR Token hoặc nhập mã vé. Giao dịch kiểm soát nguyên tử tại `ticketing.ticket_check_ins` cam kết chống 100% tình trạng quét trùng vé.
3. **Nhà Tổ Chức Sự Kiện (Organizer Portal):** Truy cập `/organizer`, quản lý hồ sơ đối tác, tạo sự kiện DRAFT, chọn địa điểm chuẩn (`venues`), cấu hình vé GA/ghế, xuất bản PUBLISHED và phân công nhân sự soát vé.
4. **Quản Trị Viên Toàn Hệ Thống (Admin Platform):** Truy cập `/admin`, quản lý 5 tab nghiệp vụ: Quản trị tài khoản & cấp quyền (chống leo quyền), Duyệt hoàn tiền 2 bước, Báo cáo doanh thu & xuất CSV chuẩn RFC 4180 chống CSV Formula Injection, Tiếp nhận leads tư vấn doanh nghiệp, và Vận hành Outbox/Kafka Recovery.

## Chạy test

Sau npm ci, Docker Desktop hoạt động:

```powershell
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live
```

Gate cuối chạy toàn backend PostgreSQL/Kafka thật rồi HTTP contract và React/jsdom admin/recovery với API thật. Harness tạo DB UUID riêng; backend suite còn broker/volume UUID riêng, cuối cùng xóa **đúng tài nguyên test tự tạo**. Live UI dùng topic/group UUID trên broker cấu hình, không ngắt broker demo; topic giữ tới retention. Không thay QA hình ảnh Chrome. Kết quả: backend/target/surefire-reports, frontend/reports, log TEMP script thông báo.

`verify-part4`…`verify-part8` là smoke lịch sử **ghi fixture/booking**, một số cố định 8080. Không dùng làm kiểm tra chỉ đọc trên demo cần giữ. `verify-local.ps1` chỉ đọc. Xem [infra/README](infra/README.md).

Rà soát T9.4 trên **toàn bộ thay đổi chưa commit**, tách biệt demo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-invariants.ps1 -WorkingTree
```

Gate tạo snapshot tracked/untracked không ignore và kiểm SHA256; không copy `.env`, dependency hay dữ liệu demo. Chạy full backend/frontend/contract/live, migration sạch rồi restart, secret/dependency scan và lint CI. Tải scanner pin version và kiểm checksum; cần Internet. Giữ volume test để kiểm tra; kết quả ở `reports/t9-4/<runId>/summary.json`. Đây là kiểm tra local, không thay kết luận GitHub Actions trên commit mới. Không sửa file nguồn trong khi gate chạy; xem giới hạn tại PROGRESS.

## Kiểm chứng môi trường sạch, song song demo

Clone vào thư mục mới, không copy `.env`, target, node_modules hoặc dữ liệu demo. Sau khối chuẩn bị ở trên, cấu hình **chỉ .env của clone** như sau; các cổng 15432/19092/18080 và IPv6 ::1:5173 phải trống:

```powershell
$checkProject = 'ticketing-readme-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$checkEnv = [IO.File]::ReadAllText((Join-Path $PWD '.env'))
$checkEnv = $checkEnv -replace '(?m)^POSTGRES_DB=.*$', 'POSTGRES_DB=ticketing_readme'
$checkEnv = $checkEnv -replace '(?m)^DB_PORT=.*$', 'DB_PORT=15432'
$checkEnv = $checkEnv -replace '(?m)^SERVER_PORT=.*$', 'SERVER_PORT=18080'
$checkEnv = $checkEnv -replace '(?m)^KAFKA_PORT=.*$', 'KAFKA_PORT=19092'
$checkEnv = $checkEnv -replace '(?m)^KAFKA_BOOTSTRAP_SERVERS=.*$', 'KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:19092'
$checkEnv = $checkEnv -replace '(?m)^VITE_API_BASE_URL=.*$', 'VITE_API_BASE_URL=http://127.0.0.1:18080'
[IO.File]::WriteAllText((Join-Path $PWD '.env'), $checkEnv + "`nCOMPOSE_PROJECT_NAME=$checkProject`n", [Text.UTF8Encoding]::new($false))
```

Không chạy khối này trên `.env` demo. Project riêng tách cả container/volume; đổi cổng thôi chưa đủ. Chạy các gate test ở trên trong clone; sau đó:

```powershell
# Terminal backend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -Port 18080
# Terminal frontend, Chrome mở http://localhost:5173 (qua IPv6, CORS cho phép):
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-frontend.ps1 -BindAddress ::1
# Terminal kiểm tra:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1 -Port 18080
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:18080/api/events
# Dừng đúng clone, giữ volume:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/stop-local.ps1 -Port 18080 -FrontendAddress ::1 -StopServices
```

127.0.0.1:5173 vẫn là demo cũ; bản thử dùng **localhost qua IPv6**. Nếu không có IPv6 loopback, dùng máy/VM có cổng 5173 trống, không đổi cổng frontend rồi bỏ qua CORS. Bằng chứng, bước đã chạy và giới hạn ở [PROGRESS](docs/PROGRESS.md).

## Xử lý lỗi thường gặp

| Lỗi | Kiểm tra / cách xử lý |
|---|---|
| Cổng bận | Get-NetTCPConnection bên dưới, nhận diện PID bằng Get-Process. Dừng đúng terminal của mình; -RestartExisting chỉ thay runtime cùng workspace. Chạy song song dùng project/cổng riêng như trên |
| Docker chỉ Client / cannot connect | Mở Docker Desktop, đợi Linux engine; docker version, docker context show, docker info. Không reset volume |
| PostgreSQL/Kafka chưa healthy | compose ps/logs/up bên dưới; kiểm tra RAM/đĩa/cổng và KAFKA_PORT/bootstrap. Sửa dependency trước khi chạy backend |
| DB healthy nhưng sai password/database | Volume giữ user/password khởi tạo lần đầu, sửa .env không đổi chúng. Khôi phục cấu hình đúng hoặc dùng project mới; không xóa volume demo |
| Lỗi Java/dependency | Kiểm tra java/javac/JAVA_HOME, dùng Maven Wrapper; npm ci đúng lockfile. Xem lỗi mạng/TLS/proxy khi tải registry; không bỏ test/đổi dependency ngẫu nhiên |
| Frontend 200 nhưng API/CORS lỗi | Backend readiness UP, kiểm tra VITE_API_BASE_URL trong .env rồi restart frontend; mở đúng HTTP origin localhost/127.0.0.1:5173 |
| 401/403 / metrics bị chặn | API admin/metrics/recovery cần ADMIN local/test; không gửi userId/role để vượt quyền |
| 409/503 đặt vé | 409 có thể hết vé/tranh ghế/key đổi nội dung; tải snapshot mới. 503 BOOKING_BUSY: giữ key/body, thử lại theo lỗi, không tạo key mới khi kết quả chưa rõ |
| Kafka lên nhưng chưa có vé | Kiểm tra outbox/inbox/audit; retry hữu hạn, lỗi cuối cần điều tra/replay gốc. Không xóa inbox, sửa status SQL hoặc reset counter |

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,5173,5432,9092
docker compose --env-file .env -f infra/docker-compose.yml ps
docker compose --env-file .env -f infra/docker-compose.yml logs --tail 80 postgres kafka
docker compose --env-file .env -f infra/docker-compose.yml up -d --wait postgres kafka
```

Root/readiness trả 503 khi dependency cần thiết DOWN; liveness vẫn có thể 200. Health không lộ chi tiết public. Log JSON trên terminal backend giúp tra correlation ID; không chia sẻ .env hoặc payload thanh toán.

## Tập trình diễn 5–10 phút (T10.2)

Theo [runbook người thuyết trình](docs/DEMO_AND_RECOVERY.md): `infra/demo-rehearsal.ps1 -Action Prepare`, `NewRun`, rồi `Rehearse -Paced`, chạy bằng `powershell -NoProfile -ExecutionPolicy Bypass -File`. Script tạo bản sao working tree/project/volume riêng, API18081, PG15433, Kafka19093, UI **127.0.0.1:5173**; không reset demo hay sửa `.env` hiện có. Nếu IPv4:5173 bận thì dừng, không tự thay dịch vụ cũ. `Stop` giữ volume; `Start` khởi động lại; `NewRun` tạo fixture mới để tập lại.

Tài liệu chuẩn: [TASKS](docs/TASKS.md), [PROGRESS](docs/PROGRESS.md), [PRD](docs/PRD.md), [TEST_PLAN](docs/TEST_PLAN.md), [ADR](docs/adr). Trạng thái thực và giới hạn kiểm chứng xem PROGRESS.
