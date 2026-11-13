# event-ticketing-platform
An event-driven ticketing platform supporting multiple ticket types, concurrency control, overselling prevention, distributed transactions, and fault recovery.

## Trạng thái

Phần8 triển khai **T7.1–T7.4** theo TASKS; tài liệu không có T7.5. Dashboard dùng API thật, không sửa trực tiếp database. Quyết định: [ADR0008](docs/adr/0008-demo-ui-and-security-review.md).

```powershell
# Docker Desktop đang chạy, từ thư mục gốc:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1
# Demo: terminal1 chạy backend, terminal2 chạy frontend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 60 -ExpiryIntervalSeconds 1
npm.cmd run dev --prefix frontend
# Terminal khác: kiểm tra API/quyền, tùy chọn tạo đủ21sự kiện để thử phân trang:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part8.ps1 -CreatePaginationFixtures
```

**Tự thử trên Chrome:**

1. Mở http://127.0.0.1:5173 và bấm **Quản trị demo**. Trong **Sự kiện**, bấm Trang sau/Trang trước khi có hơn20bản ghi. Chờ tải xong; dòng hiển thị phải đổi theo trang.
2. Bấm **Tạo sự kiện mới**, điền tên/địa điểm/thời gian, cấu hình loại GA và thêm loại ghế với nhãn mỗi dòng. Lưu DRAFT: thấy ở admin, chưa có trong catalog khách. Sửa sang PUBLISHED để khách xem được.
3. Sang **Khách hàng demo**, tải lại sự kiện, chọn GA/số lượng2 rồi giữ vé. Quay admin, mở **Tồn kho [tên]**: thấy held2 và available giảm2. Sửa capacity thấp hơn held+sold phải bị từ chối; sửa giá không đổi tổng tiền booking cũ.
4. Mở **Booking**, chọn bộ lọc trạng thái, bấm Xem để đọc timeline. Mở **Outbox** để xem event/version/trạng thái gửi. Màn hình chỉ xem; replay API T6 giữ nguyên, recovery UI thuộc T8.
5. Quay khách, mô phỏng SUCCESS và chờ CONFIRMED/2vé; làm mới outbox sẽ thấy PaymentResultRecorded/BookingConfirmed. DELAY_TIMEOUT dẫn đến EXPIRED rồi REFUND_REQUIRED/không vé; đây không phải thanh toán thật.

`test-part8-ui.ps1` tạo DB/topic/group riêng, kiểm tra dashboard bằng React/jsdom với HTTP/PostgreSQL/Kafka thật và startup guard production; không thay kiểm tra hình ảnh bằng Chrome. `verify-part8.ps1 -CreatePaginationFixtures` chỉ thêm số DRAFT còn thiếu để đủ21, không reset dữ liệu. Persona UI dùng token fixture công khai, server quyết định quyền; không phải đăng nhập production.

Lệnh kiểm tra Phần7 (Docker Desktop đang chạy, từ thư mục gốc):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part7.ps1
npm.cmd test --prefix tools/contracts
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
# HTTP + checkout dùng Kafka thật, DB riêng và restart worker:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1 -Kafka
# Demo foreground, terminal khác mở frontend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -HoldSeconds 20 -ExpiryIntervalSeconds 1
# Terminal khác:
npm.cmd run dev --prefix frontend
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part7.ps1
```

Smoke mong đợi: CONFIRMED,2tickets,sold2,payment inbox1,projection version3,outbox PUBLISHED. Script test tự tạo broker/volume và hai database UUID riêng; ngắt **broker test**, giữ dữ liệu demo. Thiếu PG_IT_URL/KAFKA_IT_BOOTSTRAP thì Maven có thể skip integration, không dùng làm nghiệm thu.

Mở http://127.0.0.1:5173, giữ GA hoặc ghế rồi mô phỏng thanh toán. 202 là đã ghi nhận; UI poll API, có thể chờ vài giây trước CONFIRMED/vé. Muộn → REFUND_REQUIRED,0vé; worker hoàn tiền mô phỏng cập nhật nghĩa vụ COMPLETED, booking giữ nguyên để theo dõi. API sự kiện: http://127.0.0.1:8080/api/events.

Local bật messaging mặc định, test tắt trừ ca Kafka. Broker bind127.0.0.1:9092, có volume; outbox từ Phần6 cũng được gửi theo version. Giữ volume khi thử outage. Broker tắt lâu có thể hết3attempt → FAILED giữ record, cần replay có audit; xem [runbook T6](docs/DEMO_AND_RECOVERY.md#t6--quy-trình-đã-triển-khai).

Phần7 thêm Kafka publisher sau commit, consumer inbox idempotent, retry/DLQ/replay admin và refund mô phỏng có history. Giữ luồng GA/ghế/payment/expiry và phát vé trong PostgreSQL. Không dùng tiền thật. Xem [tiến độ](docs/PROGRESS.md), [task](docs/TASKS.md), [ADR0007](docs/adr/0007-kafka-and-recovery.md) và [lệnh local](infra/README.md).

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1
# Terminal khác:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
npm.cmd ci --prefix frontend
npm.cmd run dev --prefix frontend
```

Mở **http://127.0.0.1:5173**. Với GA, chọn số lượng1..6 rồi **Giữ vé GA**. Với sự kiện ghế chỉ định, chọn1..6ghế trống rồi **Giữ ghế đã chọn**. Kết quả: bookingId, PENDING_PAYMENT, thời hạn/countdown và tổng server; bên dưới có lịch sử khách demo. Ghế có nhãn Trống/Đang giữ/Không khả dụng; trạng thái chỉ cập nhật theo API. Frontend dùng localhost8080, cổng5173 cố định cho CORS; có thể đặt VITE_API_BASE_URL trước chạy Vite. Không nhập userId/role.

Nếu mất kết nối/503, bấm **Thử lại cùng yêu cầu**: key và nội dung được giữ trong sessionStorage cùng tab, kể cả reload. Không xóa storage hoặc tạo key mới khi chưa rõ kết quả. Sau thành công, bấm **Đặt thêm vé** để chủ động tạo booking mới. Availability chỉ tham khảo; server có thể trả409 khi hết vé. Giao diện hiển thị mã lỗi/correlationId, không tính tổng giá.

TTL mặc định600s, worker5s/batch50. Success có recorded_at từ DB trước/đúng deadline và còn hold →CONFIRMED,held chuyển sold và phát vé cùng transaction. Failure/cancel/expiry nhả đúng một lần. Success sau khi đã mất hold →REFUND_REQUIRED hoặc nghĩa vụ bù trừ trên state terminal, không nhận lại ghế/phát vé. UI poll3s, countdown0 không tự đổi state. Worker và adapter gọi cùng PaymentResultProcessor, không còn nhánh defer của T4.

## Thử thanh toán mô phỏng và xem vé trên Windows

Để thử nhanh, chạy backend với TTL20s/worker1s; không sửa `.env`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1
```

Frontend ở http://127.0.0.1:5173 (`npm.cmd run dev --prefix frontend` nếu chưa chạy). Giữ GA hoặc ghế, rồi chọn trong **Thanh toán mô phỏng — không thu tiền thật**:

- **Mô phỏng thành công**: GET booking →CONFIRMED; vé hiển thị mã EF-UUID, GA ordinal hoặc nhãn ghế do server trả.3vé GA là3ticket riêng;2ghế có2ticket ordinal1, đúng seatId/seatLabel.
- **Mô phỏng thất bại**: PAYMENT_FAILED,không vé,inventory trả lại.
- **Mô phỏng kết quả đến muộn**: ghi SCHEDULED ngay; sau expiresAt+1s scheduler ghi SUCCESS. UI tiếp tục poll sau EXPIRED để thấy REFUND_REQUIRED; không có vé. Worker T6 hoàn tiền mô phỏng trên cùng nghĩa vụ, booking giữ REFUND_REQUIRED để theo dõi.
- **Hủy giữ chỗ**: CANCELLED nếu còn pending/chưa có success hợp lệ; repeat cancel không release lần hai. Nếu payment đã thắng, cancel409 và UI cập nhật CONFIRMED/vé thật.

Nếu payment bị network/503/500, dùng **Thử lại thanh toán cùng yêu cầu**; requestId/scenario/bookingId lưu sessionStorage qua reload, không tự tạo attempt khác. Không xóa storage khi chưa rõ kết quả.202 chỉ xác nhận đã nhận mô phỏng; booking/vé luôn lấy từ GET API.

Tự động thử một lượt trên Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part6.ps1 -WaitSeconds 45
```

Script tạo catalog riêng để không bán hết ghế seed, kiểm tra GA3vé/ghế2vé, replay, failure/cancel và delayed refund0vé; in bookingId/mã vé và giữ dữ liệu để bạn xem lịch sử. Không reset DB. Với TTL mặc định600s, bỏ tham số45 để dùng ngân sách660s. Muốn thử API bằng tay sau khi có bookingId:

```powershell
$auth = @{ Authorization='Bearer demo-customer-token' }
$bookingId = 'UUID-booking-cua-ban'
$requestId = [Guid]::NewGuid().ToString() # Giữ lại ID này khi retry cùng thao tác
$payment = @{ scenario='SUCCESS'; requestId=$requestId } | ConvertTo-Json
Invoke-RestMethod "http://127.0.0.1:8080/api/bookings/$bookingId/simulate-payment" -Method Post -Headers $auth -ContentType 'application/json' -Body $payment
Invoke-RestMethod "http://127.0.0.1:8080/api/bookings/$bookingId" -Headers $auth
Invoke-RestMethod "http://127.0.0.1:8080/api/bookings/$bookingId/tickets" -Headers $auth
# Cancel không gửi body:
# Invoke-RestMethod "http://127.0.0.1:8080/api/bookings/$bookingId/cancel" -Method Post -Headers $auth
```

Cùng requestId nhưng đổi scenario trả409 IDEMPOTENCY_KEY_REUSED. Simulator chỉ nhận scenario/requestId và bearer owner local/test; không có provider callback HTTP, nhập status/recordedAt/amount/userId sẽ bị từ chối. Ticket code là mã tra cứu demo, không phải QR/chữ ký offline hay check-in production.

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

## Tự kiểm tra Phần6 trên Windows

Mở Docker Desktop trước. Từ thư mục gốc:

```powershell
# PostgreSQL thật, DB test riêng; build và toàn bộ test backend:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6.ps1
npm.cmd ci --prefix frontend
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd ci --prefix tools/contracts
npm.cmd test --prefix tools/contracts
# UI/HTTP thật với DB riêng sạch, tự chạy/dừng backend test:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part4-ui.ps1
# Ghế + scheduler thật (TTL4s/interval1s) và restart DB test riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part5-ui.ps1
# Checkout/payment/tickets + delayed recovery sau restart, DB riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1
# Backend local8080 cần đang chạy cho hai lệnh tiếp:
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-part4.ps1
npm.cmd run test:live --prefix tools/contracts
```

Suite Phần6 có88test backend (87PostgreSQL+1H2),26test frontend,44ca hợp đồng/40response HTTP live.20request payment cùng requestId +20callback nội bộ lặp chỉ1result/1confirm/đúng3vé. Có tests recorded_at đúng deadline/+1microsecond, lag, expiry trước success/rebook,8vòng race deadline,8vòng cancel/payment,admin capacity/giá,rollback recording/ticket/failure/compensation. Toàn bộ GA/ghế/expiry và3kịch bản UI live chạy lại. [Bằng chứng](docs/evidence/part6-2026-11-12.json), report backend/target/surefire-reports và part6-evidence.json. UI live là jsdom +HTTP/PostgreSQL thật; chưa có browser visual QA.

verify-part4 giữ1vé lần đầu rồi replay key cố định; sau expiry POST vẫnbodyPENDING_PAYMENT gốc, GET mới trảEXPIRED. Contract live tạo catalog/payment/vé riêng mỗi lần; nên chạy qua test-part6-ui để dùng DB riêng được dọn. Không xóa volume demo. `mvnw verify` thiếuPG_IT_URL chỉ kiểm tra context H2 và skip PostgreSQL; không dùng thay gate Phần6. CI có PostgreSQL,GA UI/seat UI/checkout thật; chưa có GitHub CI cho working tree chưa commit.

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

Không commit `.env`, build output, dữ liệu DB hoặc credential. CI và kiểm tra local không deploy hay gửi thông báo ra ngoài. Bước kế tiếp T6.1/inbox, chưa thực hiện; chưa tự commit/push Phần6.
