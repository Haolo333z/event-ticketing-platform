# event-ticketing-platform
An event-driven ticketing platform supporting multiple ticket types, concurrency control, overselling prevention, distributed transactions, and fault recovery.

## Trạng thái

Phần 3 (T2.1–T2.5) có catalog SEATED/GA, dữ liệu demo local/test, API đọc/quản trị, mock bearer và outbox nền. Chưa có API giữ/đặt vé hoặc Kafka publisher. Xem [tiến độ](docs/PROGRESS.md), [task](docs/TASKS.md), [quyết định triển khai](docs/adr/0003-catalog-implementation.md) và [lệnh chạy local](infra/README.md).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
```

## Xem hai sự kiện mẫu

Sau khi backend chạy, mở [danh sách sự kiện](http://127.0.0.1:8080/api/events) trong trình duyệt (hiện là JSON, chưa có giao diện catalog).

- [Sự kiện ghế chỉ định](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000001): 4 ghế A1–A4, giá 200.000 VND.
- [Availability ghế](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000001/availability).
- [Sự kiện GA](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000002): capacity 100, giá 100.000 VND.
- [Availability GA](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000002/availability).

Availability chỉ là snapshot, không giữ vé. Seed idempotent chỉ local/test, không overwrite giá/capacity đã chỉnh. Không cần xóa volume demo để chạy test: script test tự tạo DB riêng và dọn đúng DB đó.

## Tự kiểm tra Phần 3 trên Windows

Mở Docker Desktop trước. Từ thư mục gốc:

```powershell
# PostgreSQL thật, DB test riêng; build và toàn bộ test backend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part3.ps1
npm.cmd ci --prefix tools/contracts
npm.cmd test --prefix tools/contracts
# Backend local cần đang chạy cho hai lệnh tiếp:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part3.ps1
npm.cmd run test:live --prefix tools/contracts
```

Nếu demo đã chỉnh inventory thì verify-part3 có thể báo khác seed ban đầu; integration test vẫn dùng DB sạch riêng. `mvnw verify` không có PG_IT_URL chỉ chạy test context H2 và skip test PostgreSQL; không dùng kết quả đó thay gate Phần 3. CI backend có PostgreSQL17.11 và PG_IT_URL để chạy test thật.

## Thử API quản trị

Mock fixture công khai: `demo-customer-token` và `demo-admin-token`, chỉ local/test. Thiếu/token lạ trả401; customer gọi admin trả403. Server lấy account/role từ DB principal, bỏ qua header tự khai danh tính. Mock bật ngoài local/test làm startup thất bại; ngoài các profile này không có seed runner hay token demo mặc định.

```powershell
$admin = @{ Authorization = 'Bearer demo-admin-token' }
$body = @{
  title='Demo draft'; description='Local example'; venue='Demo venue'
  saleStartAt='2026-01-01T00:00:00Z'; saleEndAt='2099-01-01T00:00:00Z'
  startsAt='2099-01-02T00:00:00Z'; status='DRAFT'
  ticketTypes=@(@{ name='GA'; mode='GA'; priceVnd=120000; capacity=50 })
} | ConvertTo-Json -Depth 6
$event = Invoke-RestMethod http://127.0.0.1:8080/api/admin/events -Method Post -Headers $admin -ContentType 'application/json' -Body $body
$patch = @{ status='PUBLISHED'; ticketTypes=@(@{id=$event.ticketTypes[0].id; priceVnd=150000; capacity=60}) } | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://127.0.0.1:8080/api/admin/events/$($event.id)" -Method Patch -Headers $admin -ContentType 'application/json' -Body $patch
```

PATCH chỉ nhận field đã chốt; không cho đổi mode, sửa/xóa ghế hoặc thêm type. Giảm GA capacity dưới held+sold trả409 và rollback cả transaction. Đổi giá không sửa snapshot booking_items. Chưa có route DELETE hoặc booking. Public catalog chỉ hiện PUBLISHED.

## Công cụ cần có

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

Trên Linux/macOS, dùng `sh ./mvnw` và `npm`; integration test cần PostgreSQL riêng qua PG_IT_URL, POSTGRES_USER, POSTGRES_PASSWORD. Test frontend kiểm tra render trang khởi đầu. Chạy giao diện mẫu bằng `npm.cmd run dev` trong frontend rồi mở http://127.0.0.1:5173; chưa có luồng đặt vé.

## Cấu hình và CI

Script local tự tạo `.env` với mật khẩu ngẫu nhiên nếu chưa có và nạp các biến cho Compose/backend. Nếu sao chép `.env.example` thủ công, phải đặt POSTGRES_PASSWORD trước khi chạy. `.env` bị Git bỏ qua. Không đưa secret vào biến `VITE_*` vì các biến đó có thể xuất hiện trong bundle trình duyệt.

CI chạy khi push, pull request hoặc gọi thủ công: backend verify và CycloneDX SBOM; frontend `npm ci`, test, build và npm audit (fail từ mức high); OSV scan dependency backend từ SBOM; Gitleaks quét cả lịch sử Git và file hiện tại. Findings hoặc lỗi scanner làm job fail. Dependabot kiểm tra Maven/npm/Actions mỗi tuần. Các scanner cần kết nối dịch vụ advisory; báo cáo local chưa thay cho GitHub Actions run.

Sau khi có Gitleaks 8.30.1 và OSV Scanner 2.7.1 trên PATH, từ thư mục gốc chạy:

```powershell
gitleaks git . --redact --no-banner
gitleaks dir . --redact --no-banner
osv-scanner scan source --sbom backend/target/bom.json
```

Không commit `.env`, build output, dữ liệu DB hoặc credential. CI và kiểm tra local không deploy hay gửi thông báo ra ngoài. Bước kế tiếp theo TASKS là T3.1, chưa thực hiện trong Phần 3.
