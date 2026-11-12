# event-ticketing-platform
An event-driven ticketing platform supporting multiple ticket types, concurrency control, overselling prevention, distributed transactions, and fault recovery.

## Trạng thái

T1.2 đã đạt CI; T1.3 có PostgreSQL, backend health endpoint và Flyway migration nền. Chưa có nghiệp vụ booking hoặc Kafka. Xem [tiến độ](docs/PROGRESS.md), [task](docs/TASKS.md), [ADR stack](docs/adr/0001-stack-and-local-environment.md) và [lệnh chạy local](infra/README.md).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
```

## Yêu cầu

- Temurin JDK 25.0.5+8, JAVA_HOME trỏ tới JDK và `java`/`javac` trên PATH cùng phiên bản.
- Node 26.13.0, npm 11.20.0; Maven Wrapper tự tải Maven 3.9.16, không cần cài Maven global.
- Internet ở lần build đầu để tải dependency. Docker dành cho môi trường ở T1.3 và các task sau.

## Cấu trúc

```text
backend/   Spring Boot, Maven Wrapper, module packages, test context
frontend/  React + TypeScript + Vite, test render bằng Node test runner
infra/     Compose PostgreSQL và script chạy/kiểm tra local
docs/      thiết kế, ADR, task, tiến độ hiện có
.github/   CI build/test, secret/dependency scan, Dependabot
```

## Kiểm tra local (PowerShell)

```powershell
java -version
javac -version
cd backend
.\mvnw.cmd -B -ntp verify cyclonedx:makeAggregateBom
cd ../frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd audit --audit-level=high
```

Trên Linux/macOS, dùng `sh ./mvnw` và `npm`. Test backend hiện kiểm tra Spring context không cần external services; test frontend kiểm tra render trang khởi đầu. Đây chưa phải test nghiệp vụ. Chạy giao diện mẫu bằng `npm.cmd run dev` trong frontend rồi mở http://127.0.0.1:5173.

## Cấu hình và CI

Script local tự tạo `.env` với mật khẩu ngẫu nhiên nếu chưa có và nạp các biến cho Compose/backend. Nếu sao chép `.env.example` thủ công, phải đặt POSTGRES_PASSWORD trước khi chạy. `.env` bị Git bỏ qua. Không đưa secret vào biến `VITE_*` vì các biến đó có thể xuất hiện trong bundle trình duyệt.

CI chạy khi push, pull request hoặc gọi thủ công: backend verify và CycloneDX SBOM; frontend `npm ci`, test, build và npm audit (fail từ mức high); OSV scan dependency backend từ SBOM; Gitleaks quét cả lịch sử Git và file hiện tại. Findings hoặc lỗi scanner làm job fail. Dependabot kiểm tra Maven/npm/Actions mỗi tuần. Các scanner cần kết nối dịch vụ advisory; báo cáo local chưa thay cho GitHub Actions run.

Sau khi có Gitleaks 8.30.1 và OSV Scanner 2.7.1 trên PATH, từ thư mục gốc chạy:

```powershell
gitleaks git . --redact --no-banner
gitleaks dir . --redact --no-banner
osv-scanner scan source --sbom backend/target/bom.json
```

Không commit `.env`, build output, dữ liệu DB hoặc credential. CI và kiểm tra local không deploy hay gửi thông báo ra ngoài. Bước kế tiếp theo TASKS là T1.3 sau khi T1.2 được nghiệm thu.
