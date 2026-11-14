# DEMO_AND_RECOVERY — Kịch bản T10.2

Trình diễn local/test, payment/refund mô phỏng, không dùng tiền thật. PostgreSQL là nguồn sự thật; Kafka truyền ít nhất một lần, inbox chống xử lý trùng. Không reset database, xóa volume/inbox hoặc chỉnh trạng thái bằng SQL. Tài liệu này thay kế hoạch cũ chưa kiểm chứng có bước reset demo.

## Chuẩn bị ngoài giờ trình diễn

Yêu cầu Java25, Node26, Docker Desktop Linux engine; phiên bản theo README. Chạy từ repository trên Windows PowerShell. Prepare tạo **bản sao working tree hiện tại**, project/volume/database mới, không lấy `.env` demo cũ. Cổng riêng: PG15433, Kafka19093, API18081, UI **127.0.0.1:5173**. Cổng bận thì dừng, không kill dịch vụ khác. T10.1 vẫn ở **localhost:5173 qua IPv6**; không dùng lẫn hai URL.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Prepare
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action NewRun
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Show
```

Prepare một lần/session; Start dùng lại sau khi dừng. Session: `reports/t10-2/session.json`; log/snapshot cùng thư mục. TTL20giây và expiry1giây chỉ override trong backend, không sửa `.env` hiện có. Mỗi NewRun tạo hai fixture qua ADMIN API: GA10vé giá100.000đ và SEATED A1/A2 giá200.000đ. Giữ hai seed gốc và mọi lượt cũ. Tìm đúng tên/ID mà script in; Chrome Ctrl+F tìm tên fixture. Customer hiện tải tối đa100sự kiện/100booking; ADMIN có phân trang. Khi tập nhiều đến giới hạn, Stop session cũ rồi Prepare với một `-Session reports/t10-2/session-moi.json` mới, dùng cùng tham số đó cho mọi Action tiếp theo; giữ nguyên volume cũ.

Persona **Khách demo**: đặt, thanh toán, xem vé/lịch sử; **Quản trị demo**: dashboard/outbox/replay với lý do và audit. Chọn persona bằng giao diện; token fixture công khai chỉ local/test, server ánh xạ role. Không X-User-Id. Hai request tranh ghế trong script dùng **cùng persona khách**, không giả vờ là hai tài khoản độc lập.

Mở Chrome `http://127.0.0.1:5173`; API `http://127.0.0.1:18081/api/events`; health `http://127.0.0.1:18081/actuator/health`. Để Chrome cạnh PowerShell. Chuỗi lệnh sử dụng API thật; kết quả không được thay bằng dữ liệu giả. Không đặt vào fixture chính bằng UI trước lượt tập vì sẽ thay baseline.

## Trình diễn chính 5–10 phút

Install/build/seed ngoài đồng hồ. Chạy từng Action, đợi kết quả ổn định rồi thuyết minh/xem Chrome. Không dừng giữa giữ chỗ và thanh toán vì TTL20giây. Có thể tập toàn bộ bằng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Rehearse -Paced
```

Paced có khoảng xem/thuyết minh định giờ, tách thời gian xử lý và xem trong JSON. Lượt HTTP/SQL nhanh không được gọi là buổi thuyết trình 5–10phút.

| Mốc dự kiến | Action | Thao tác/điểm giải thích | Kết quả phải thấy |
|---|---|---|---|
| 0:00–0:45 | Show | Chrome mở hai fixture GA/ghế, giới thiệu persona và PostgreSQL | GA10AVAILABLE; A1/A2AVAILABLE; healthUP |
| 0:45–1:35 | Ga | Đặt1GA, cùng key/body gửi hai lần, SUCCESS; tải lại lịch sử/vé |202cùng bookingId;1booking/1hold; CONFIRMED/1vé. Key gửi sau confirm trả snapshot PENDING_PAYMENT cũ, GET mới làCONFIRMED |
| 1:35–2:25 | Seats | Hai request tranh A1, thanh toán request thắng; xem vé ghi A1 | Chính xác1HTTP202 và1HTTP409 INVENTORY_UNAVAILABLE;1booking/1véA1, không có booking dở của request thua |
| 2:25–3:35 | Late | Đặt1GA, chờ20giây và workerEXPIRED, rồi SUCCESS | EXPIRED→REFUND_REQUIRED,0vé; nhảinventory;1refundCOMPLETED, không confirm lại |
| 3:35–4:40 | Outage | Dừng Kafka **session**; tạo rồi hủy1GA; xem outbox/health | Root/readiness503, liveness200; CANCELLED bền,0vé/0hold; versionđầuFAILED sau3attempt, versionsauPENDING |
| 4:40–5:30 | Recover | Bật broker; ADMIN replay versionFAILED đầu, chờ drain | HealthUP; outboxPUBLISHED, inbox/projection đủ; không đổi nghiệp vụ |
| 5:30–6:15 | Replay | Replay PaymentResultRecorded đã xử lý; cùng request replay gửi hai lần |202cảhai,1audit; consumerDUPLICATE; hashnghiệp vụ giống nhau, tổngvé2 |
| 6:15–7:00 | Verify | Đối chiếu SQL/dashboard, kết luận giới hạn |4booking/2vé/9history/12outbox; GAheld0/sold1,A1SOLD; countervi phạm0 |

Chạy riêng từng bước bằng lệnh sau, thay Ga lần lượt bằng Seats, Late, Outage, Recover, Replay, Verify:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Ga
```

Gợi ý lời nói theo bước: (1) availability tham khảo, DB quyết định giữ chỗ; (2) key/kết quả/inventory cùng transaction; (3) request tranh ghế thua rollback; (4) payment dùng DBclock, mất hold không nhận lại; (5) outbox bền khi broker hỏng; (6) giữ nguyên eventID/inbox và budget3replay; (7) confirm phát vé, BookingConfirmed chỉ thông báo; (8) demo không chứng minh chịu mất ổ đĩa hoặc nhiều broker.

**Nói chính xác:** outage tạo rồi hủy để kết quả ổn định, không thanh toán trong lúc broker tắt. PaymentSUCCESS đã ghi bền đúng hạn trong PostgreSQL vẫn có thể được expiryworker confirm khi Kafka dừng; không nói mọi booking phải chờ broker mới xử lý. REFUND_REQUIRED giữ sau refundCOMPLETED để bảo toàn lịch sử.

## API/SQL và bằng chứng

Show in URL/ID/tên fixture. Verify chạy SQL chỉ đọc `infra/demo-audit.sql`, scope hai event của lượt, lưu snapshot `reports/t10-2/runs/<runId>/`. Không in token/mãvé/payloadpayment. Chạy lại SQL:

```powershell
$s=Get-Content reports/t10-2/session.json -Raw | ConvertFrom-Json
$r=Get-Content $s.runFile -Raw | ConvertFrom-Json
Get-Content infra/demo-audit.sql -Raw | docker exec -i "$($s.project)-postgres-1" psql -U ticketing -d ticketing_rehearsal -qAt -v ON_ERROR_STOP=1 -v "ga_event=$($r.ga.id)" -v "seat_event=$($r.seated.id)"
Invoke-RestMethod 'http://127.0.0.1:18081/actuator/health'
```

Chrome: chọn **Khách hàng demo**, bấm **Tải lại sự kiện**, **Xem sự kiện** tại hai fixture; xem sơ đồ A1/A2 và GA. Sau mỗi Action, tại **Lịch sử của tôi** bấm **Tải lại lịch sử**, chọn đúng bookingId; **Cập nhật booking** và xem **Vé của booking**. Chọn **Quản trị demo**, tab Booking/Outbox/Khôi phục, dùng Trang sau khi cần; Outbox→Chi tiết event để xem audit. API detail cầncustomer, recovery cầnADMIN.202chỉ chấp nhận/requeue, phải chờPUBLISHED/inbox. Không replay vô hạn hoặc xóa inbox. Bằng chứng public/giới hạn trong PROGRESS.

## Dự phòng nếu còn thời gian

`-Action Backup`: cùngkey đổiquantity→409; fixtureghế **khác** hết hạn→đặt lại→paymentFAILURE→nhảghế,0vé. Không đổi số liệu fixturechính. Tải50request, consumerpoison/retry/DLQ và JVMrestart đã có gateT9.1–T9.3, nằm ngoài đồng hồ chính; không gây lỗi bằng SQL.

## Gián đoạn, tập lại, dừng giữ volume

- ResponseHTTP mơ hồ: script lưu key/body/requestId trước gửi; chạy lại **đúng Action**, giữ nguyên chúng, GET kiểm tra. Nếu hold đã hết giữa Ga/Seats và payment, giữ evidenceFAIL rồi NewRun; không backdate/đổi deadline.
- Mất terminal: Start rồi Show. Outage cố ý để broker dừng; Recover bật lại và replay đúng event. Start cũng bật broker. Backend/frontend chạyẩn, log riêng, không cần giữ terminallauncher.
- CóFAIL thì dừng, đọc snapshot/log; không tuyên bốPASS. Replay version thấp nhất sau khôi phục, cùngrequestId/reason khi mấtresponse. ConsumerDLQ phải điều tra trước replay, không bỏ budget3/inbox.
- Tập lại: Recover nếu cần, NewRun rồi Rehearse. Giữ mọi fixturecũ, không resetseed.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Stop
# Chỉ dừng đúng Node/JVM/projectsession; giữ volume/.env/fixture.
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Start
```

Giới hạn: brokerRF1, không chứng minh mấtvolume/clusterHA/productionauth. Lượt tập HTTP/SQL và khoảng dừng thuyết minh được đo riêng; không gọi là đã thao tácChrome nếu chưa làm. Xác nhậnChromeT10.1 của chủ dự án không thay bằng chứngT10.2.
