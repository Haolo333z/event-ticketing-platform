# event-ticketing-platform
An event-driven ticketing platform supporting multiple ticket types, concurrency control, overselling prevention, distributed transactions, and fault recovery.

## Trạng thái

Phần5 (T4.1–T4.5) đã có chọn/giữ ghế nguyên tử và expiry worker nhả ghế/GA đúng một lần; giữ các luồng GA/idempotency/owner/history. Giao diện dùng API thật. Chưa có thanh toán, phát vé hoặc Kafka. Xem [tiến độ](docs/PROGRESS.md), [task](docs/TASKS.md), [quyết định ghế/expiry](docs/adr/0005-seats-and-expiry.md) và [lệnh local](infra/README.md).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
npm.cmd ci --prefix frontend
npm.cmd run dev --prefix frontend
```

Mở **http://127.0.0.1:5173**. Với GA, chọn số lượng1..6 rồi **Giữ vé GA**. Với sự kiện ghế chỉ định, chọn1..6ghế trống rồi **Giữ ghế đã chọn**. Kết quả: bookingId, PENDING_PAYMENT, thời hạn/countdown và tổng server; bên dưới có lịch sử khách demo. Ghế có nhãn Trống/Đang giữ/Không khả dụng; trạng thái chỉ cập nhật theo API. Frontend dùng localhost8080, cổng5173 cố định cho CORS; có thể đặt VITE_API_BASE_URL trước chạy Vite. Không nhập userId/role.

Nếu mất kết nối/503, bấm **Thử lại cùng yêu cầu**: key và nội dung được giữ trong sessionStorage cùng tab, kể cả reload. Không xóa storage hoặc tạo key mới khi chưa rõ kết quả. Sau thành công, bấm **Đặt thêm vé** để chủ động tạo booking mới. Availability chỉ tham khảo; server có thể trả409 khi hết vé. Giao diện hiển thị mã lỗi/correlationId, không tính tổng giá.

TTL mặc định600s, worker mỗi5s/batch50. Đến hạn, worker chuyển EXPIRED và nhả inventory cùng transaction; UI poll3s nên có thể chậm hơn deadline vài giây. Countdown0 chỉ báo đang chờ server, không tự cho ghế trống. Với GA, hold tăng held_count, expiry giảm held_count; sold_count không tăng vì chưa có payment. Worker tạm giữ nguyên nếu phát hiện payment result bền để chờ processor T5; không giả lập confirm hoặc refund.

## Thử đặt ghế và hết hạn nhanh trên Windows

Không cần sửa `.env`. Trong terminal backend, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1
```

`-RestartExisting` chỉ được phép thay JVM đang dùng đúng infra/.local-runtime/backend.jar của workspace này; nếu cổng8080 thuộc app khác, script từ chối. Build đạt trước khi dừng JVM cũ. Hai tham số TTL/interval chỉ áp dụng process đang chạy, không ghi `.env`. Các booking đã tồn tại vẫn giữ expiresAt cũ.

Trong UI5173 chọn sự kiện ghế mẫu, chọn A1/A2 nếu còn trống, gửi giữ ghế. Mong đợi202/PENDING_PAYMENT/tổng400000VND với giá seed; ghế đổi Đang giữ. Sau khoảng20s +worker1s +poll3s, GET/UI trả EXPIRED và ghế Trống. Bấm **Đặt thêm vé** để nhận booking mới. Hai lần bấm gửi của cùng thao tác không tạo thêm booking.

Có thể tự động kiểm tra một lượt bằng terminal khác:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part5.ps1 -WaitSeconds 45
```

Script chọn hai ghế mẫu còn trống, POST/replay, chờ EXPIRED, kiểm tra một history hết hạn/ghế trống, replay key cũ rồi tạo booking mới. Booking mới đó tiếp tục hết hạn theo TTL. Nếu đang giữ ghế trên UI, hãy đợi chúng hết hạn trước; không reset DB. Với TTL mặc định600s, dùng ngân sách chờ mặc định660s thay cho45s. Chạy launcher không tham số TTL để quay lại cấu hình `.env`.

## Xem hai sự kiện mẫu

Sau khi backend chạy, xem giao diện tại5173 hoặc mở [danh sách sự kiện JSON](http://127.0.0.1:8080/api/events).

- [Sự kiện ghế chỉ định](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000001): 4 ghế A1–A4, giá 200.000 VND.
- [Availability ghế](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000001/availability).
- [Sự kiện GA](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000002): capacity 100, giá 100.000 VND.
- [Availability GA](http://127.0.0.1:8080/api/events/20000000-0000-4000-8000-000000000002/availability).

Availability chỉ là snapshot, không giữ vé. Seed idempotent chỉ local/test, không overwrite giá/capacity đã chỉnh. Không cần xóa volume demo để chạy test: script test tự tạo DB riêng và dọn đúng DB đó.

## Tự kiểm tra Phần5 trên Windows

Mở Docker Desktop trước. Từ thư mục gốc:

```powershell
# PostgreSQL thật, DB test riêng; build và toàn bộ test backend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5.ps1
npm.cmd ci --prefix frontend
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd ci --prefix tools/contracts
npm.cmd test --prefix tools/contracts
# UI/HTTP thật với DB riêng sạch, tự chạy/dừng backend test:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4-ui.ps1
# Ghế + scheduler thật (TTL4s/interval1s) và restart DB test riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5-ui.ps1
# Backend local8080 cần đang chạy cho hai lệnh tiếp:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part4.ps1
npm.cmd run test:live --prefix tools/contracts
```

Suite Phần5 có58test backend (57PostgreSQL+1H2),16test frontend,32ca hợp đồng và22response HTTP live. Tranh2ghế:50lệnh/2customer chỉ1booking thắng/49conflict/2ghếHELD. Tranh1ghế qua HTTP:1HTTP202/49HTTP409.20lệnh trùng key/đảo thứ tự chỉ1booking/1outbox/2ghếHELD.20worker cùng booking chỉ1EXPIRED/1lần release. Deadlock40P01 thật được retry; fault lặp dừng3attempt/503. [Bằng chứng](docs/evidence/part5-2026-11-12.json), report backend/target/surefire-reports và part5-evidence.json. UI live dùng jsdom +HTTP/PostgreSQL thật, không mock response; chưa có browser visual QA.

verify-part4 giữ1vé ở lần đầu, dùng lại key cố định về sau; sau expiry POST replay vẫnbodyPENDING_PAYMENT gốc, GET mới trảEXPIRED. Contract live tạo sự kiện ghế riêng mỗi lần, vì vậy nên chạy qua test-part5-ui để dùng DB riêng được dọn. Không xóa volume demo. `mvnw verify` thiếuPG_IT_URL chỉ kiểm tra context H2 và skip PostgreSQL; không dùng thay gate Phần5. CI có PostgreSQL và các bước GA UI/seat UI/scheduler, nhưng chưa chạy GitHub CI cho working tree chưa commit.

## Thử một booking GA bằng PowerShell

Backend cần đang chạy. Đoạn dưới cố định key; chạy lại cả đoạn trả booking cũ. Muốn tạo booking khác sau khi đã nhận kết quả rõ ràng, chủ động đổi key.

```powershell
$base = 'http://127.0.0.1:8080'
$headers = @{ Authorization='Bearer demo-customer-token'; 'Idempotency-Key'='readme-ga-part4-v1' }
$body = @{ eventId='20000000-0000-4000-8000-000000000002'; ticketTypeId='30000000-0000-4000-8000-000000000002'; quantity=2 } | ConvertTo-Json
$first = Invoke-WebRequest -UseBasicParsing "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$first.StatusCode # 202
$first.Headers['Location'] # /api/bookings/{UUID}
$responseText = if ($first.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($first.Content) } else { $first.Content }
$result = $responseText | ConvertFrom-Json
$result # PENDING_PAYMENT, totalAmountVnd=200000 nếu giá seed chưa sửa
$again = Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$again.bookingId -eq $result.bookingId # True; không giữ thêm 2 vé
Invoke-RestMethod "$base/api/bookings/$($result.bookingId)" -Headers $headers
Invoke-RestMethod "$base/api/me/bookings?page=0&size=20" -Headers $headers
# Cùng key nhưng quantity khác: mong đợi409 IDEMPOTENCY_KEY_REUSED
$changed = @{ eventId='20000000-0000-4000-8000-000000000002'; ticketTypeId='30000000-0000-4000-8000-000000000002'; quantity=3 } | ConvertTo-Json
try { Invoke-RestMethod "$base/api/bookings" -Method Post -Headers $headers -ContentType 'application/json' -Body $changed -ErrorAction Stop }
catch {
  $failure = $_.ErrorDetails.Message
  if (!$failure -and $_.Exception.Response) {
    $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $failure = $reader.ReadToEnd(); $reader.Dispose()
  }
  $failure # JSON code=IDEMPOTENCY_KEY_REUSED (hỗ trợ cả Windows PowerShell5)
}
```

POST mới có thể trả409 INVENTORY_UNAVAILABLE nếu demo đã hết vé; không tự xóa dữ liệu để khôi phục seed. Error503 BOOKING_BUSY kèm Retry-After:1 cần thử lại **cùng key và body**. Token lạ401, admin POST booking403, customer khác GET booking404.

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

PATCH chỉ nhận field đã chốt; không cho đổi mode, sửa/xóa ghế hoặc thêm type. Giảm GA capacity dưới held+sold trả409 và rollback cả transaction. Đổi giá không sửa snapshot booking_items. Chưa có route DELETE. Public catalog chỉ hiện PUBLISHED.

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

Trên Linux/macOS, dùng `sh ./mvnw` và `npm`; integration test cần PostgreSQL riêng qua PG_IT_URL, POSTGRES_USER, POSTGRES_PASSWORD. Test frontend kiểm tra HTTP adapter, storage và tương tác DOM. Chạy giao diện bằng `npm.cmd run dev` trong frontend rồi mở http://127.0.0.1:5173.

## Cấu hình và CI

Script local tự tạo `.env` với mật khẩu ngẫu nhiên nếu chưa có và nạp các biến cho Compose/backend. Nếu sao chép `.env.example` thủ công, phải đặt POSTGRES_PASSWORD trước khi chạy. `.env` bị Git bỏ qua. Không đưa secret vào biến `VITE_*` vì các biến đó có thể xuất hiện trong bundle trình duyệt.

CI chạy khi push, pull request hoặc gọi thủ công: backend verify và CycloneDX SBOM; frontend `npm ci`, test, build và npm audit (fail từ mức high); OSV scan dependency backend từ SBOM; Gitleaks quét cả lịch sử Git và file hiện tại. Findings hoặc lỗi scanner làm job fail. Dependabot kiểm tra Maven/npm/Actions mỗi tuần. Các scanner cần kết nối dịch vụ advisory; báo cáo local chưa thay cho GitHub Actions run.

Sau khi có Gitleaks 8.30.1 và OSV Scanner 2.7.1 trên PATH, từ thư mục gốc chạy:

```powershell
gitleaks git . --redact --no-banner
osv-scanner scan source --sbom backend/target/bom.json
```

Không quét trực tiếp mọi file local bằng `gitleaks dir .` khi có `.env`/dependency/build ignored; để kiểm tra tập bàn giao, chép tập `git ls-files --cached --others --exclude-standard` sang thư mục scan tạm rồi quét thư mục đó. GitHub checkout sạch không có `.env` nên CI vẫn quét working tree đầy đủ.

Không commit `.env`, build output, dữ liệu DB hoặc credential. CI và kiểm tra local không deploy hay gửi thông báo ra ngoài. Bước kế tiếp là T5.1, chưa thực hiện. Chưa tự commit/push Phần5.
