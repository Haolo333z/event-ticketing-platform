# PROGRESS — Nhật ký tiến độ

> Cập nhật sau mỗi task hoặc quyết định ảnh hưởng phạm vi. Ngày dùng múi giờ Việt Nam.

## Trạng thái hiện tại

- **Giai đoạn:** Quan sát/vận hành — T8.4 hoàn thành local ngày2026-11-14
- **Tiến độ:** T1.1–T7.4 và T8.1–T8.4 Done; README đã chạy từ clone/database/volume sạch
- **Task đang làm:** T8.4 đã kiểm chứng từ HEAD1e157a9, working tree ban đầu sạch; chưa commit/push
- **Task tiếp theo:** T9.1 (chưa triển khai)
- **Blocker:** Không có blocker T8.4 local. Chưa kiểm chứng máy Windows mới hoàn toàn, cache lạnh, GitHub Actions/Linux hoặc visual Chrome; giới hạn đầy đủ trong nhật ký T8.4.

- **Task trong TASKS:** 37/45 Done.18bảng/V1–V9 giữ nguyên; OpenAPI18operation (16cũ +2GET recovery), không thêm dependency. Các mục cũ bên dưới giữ bằng chứng lịch sử.

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

- TTL mặc định600s, worker5s/batch50 đã hoạt động, max6vé, pool10/2/acquisition2s. BookingPolicy lock1500ms/statement5s/attempt5s/total10s, frontend abort15s. Demo hiện chạy TTL20s/worker1s bằng override process, không sửa `.env`.
- Worker trễ có thể khiến inventory hết hạn chưa đặt lại được; API trả conflict có thể thử lại để giữ invariant.
- Lock order/partial rollback/expiry/payment/cancel/admin race đã có PostgreSQL tests;Kafka lag thật vẫn thuộcT6,local lag được test bằng durable result chưa gọiadapter.
- Availability khônghold;không tự lấy lại inventory đã quáhạn. T5 thay DEFERRED_PAYMENT bằng processor chung. Mỗi transaction giữ invariant riêng:recordresult/outbox commit trước;processing inventory/state/history/tickets/outbox/processed_at cùng commit/rollback.
- Lock timeout vàdeadlock40P01 thật đã kiểm tra;CORS/body/auth tests không thay security reviewT7. Chưa fault pool exhaustion/statementcancel độc lập/mất kết nối lúcCOMMIT. Compensation mớiPENDING,chưa hoàn tiền mô phỏng/T6.

## Nhật ký

### 2026-11-13 — T7.3: form Tạo/Sửa xuất hiện trong vùng nhìn

- **Nguyên nhân:** nút Tạo đã đổi state và render form, nhưng `editor-panel` nằm sau bảng/phân trang nên bị đẩy xuống cuối danh sách dài. Form Sửa cũng dùng vùng đó; trước đây không có scroll/focus khi mở.
- **Sửa:** AdminDashboard đặt vùng editor ngay dưới nút Tạo và trước bảng; cuộn tới editor khi mở, focus `admin-title` khi form sẵn sàng. Sửa chờ inventory xong mới đưa focus vào dữ liệu đúng sự kiện; loading/lỗi vẫn có nút Đóng ở đầu panel. EventEditor có nút Hủy cuối form, cả hai thao tác đóng đều không submit. Bấm lại Tạo khi form đã mở cũng đưa người dùng về form.
- **File:** `frontend/src/app/AdminDashboard.ts`, `EventEditor.ts`, `AdminDashboard.test.ts`, `frontend/src/styles.css`; cập nhật DESIGN và evidence hiện có. Không đổi backend/API/migration hoặc các thay đổi Phần8 trước đó.
- **PASS** `node --test frontend/src/app/AdminDashboard.test.ts`: **14/14**. Ba ca mới kiểm tra danh sách20dòng/form đứng ngay sau nút và trước bảng, scroll/focus/nhập liệu, Đóng/Hủy gỡ form, Sửa với dữ liệu sự kiện thứ2 sau tải async và đóng được cả khi tải lỗi. Không phát sinh create/patch khi chỉ mở/đóng.
- **PASS** `npm.cmd test --prefix frontend`: **48/48**,0fail/skip. **PASS** `npm.cmd run build --prefix frontend`: TypeScript/Vite thành công. Log tại frontend/part8-form-tests.log và part8-form-full.log (ignored). Chưa kiểm tra trực quan lại trên Chrome; jsdom xác nhận DOM/order/focus và lời gọi scroll, không có layout thật. Các kết quả116backend/66HTTP trước đó giữ nguyên, không tuyên bố đã chạy lại trong lần sửa UI này.
- Không commit/push. Tự thử: tải lại http://127.0.0.1:5173 → Quản trị demo → Sự kiện → Tạo hoặc Sửa; form hiện trên bảng, tên sự kiện có focus; Đóng biểu mẫu/Hủy sẽ đóng form.

### 2026-11-13 — Phần8: T7.1–T7.4 Done

Tiếp tục các file đã sửa trên commit `4a89926`, không tạo dự án mới hoặc làm lại T6. TASKS chỉ có T7.1–T7.4; không tạo T7.5, không triển khai log cấu trúc/metrics/recovery UI T8. Quyết định trước code tại [ADR0008](adr/0008-demo-ui-and-security-review.md); bằng chứng tại [part8-2026-11-13.json](evidence/part8-2026-11-13.json).

| Task | Thay đổi chính | Bằng chứng đủ tiêu chí |
|---|---|---|
| T7.1 | DemoShell chọn fixture khách/admin, không lưu token; quyền vẫn từ principal server. DemoAuthConfigurationTest/PostgresDashboardSecurityTest rà auth | 401 token lạ/thiếu,403 customer gọi admin dù giả role/userId; owner sai404; local/test bật được, prod/profile trộn bị chặn. JAR thật prod+mock fail startup đúng lý do. Listener test chỉ127.0.0.1 |
| T7.2 | App/Checkout giữ GA/ghế/payment; tách loading/error/empty, history/detail loading, cảnh báo snapshot cũ, giải thích EXPIRED/REFUND_REQUIRED; đổi persona giữ pending command | 45test frontend (gồm toàn bộ26test cũ) đạt. GA/seat/payment UI HTTP thật chạy lại; countdown không tự đổi state, refund không hiển thị vé, gửi lặp không đặt lại |
| T7.3 | AdminDashboard/EventEditor + admin API client. GET admin/events và inventory mới, GET admin/bookings hoàn thiện; phân trang/filter/history/outbox, form tạo/sửa GA/ghế | Dashboard qua HTTP/PostgreSQL/Kafka thật:25sự kiện → trang20+5,0ID trùng, DOM khớp response trang2. DRAFT không public, publish xem được; GA2 giữheld2/available4; capacity1 bị409, price mới không đổi tổng cũ200000; booking/outbox/2vé confirmed và SQL đạt |
| T7.4 | Strict JSON object/key lặp/trailing, giới hạn byte/UTF-8/depth, lỗi DB ở auth filter theo API; reason replay đếm Unicode; chặn log framework lộ SQL | Negative tests cho body/quyền giả/overflow/CORS/error/correlation, inject SQL secret marker không lộ response/log; toàn backend116đạt. Gitleaks/dependency/lint theo mục dưới. Không thêm Redis/rate limit hoặc đổi event/schema/state |

**Nguyên nhân pagination và các lỗi đã sửa:**

- **Script**, không phải API: `.pagination` bị gỡ trong khoảng loading; script dùng non-null assertion rồi đọc textContent. Đã chờ node tồn tại/đúng trang, so hàng DOM với response200 page1, kiểm tra ID không trùng và quay lại trang1. Không dùng sleep cố định hoặc dữ liệu giả để làm test xanh.
- **Script:** thứ tự loại vé theo UUID không bảo đảm GA ở index0. Đã lấy GA theo ID/mode cho cả lựa chọn khách và form sửa.
- **UI:** mở lại cùng sự kiện dùng snapshot tồn kho trước khi có booking; nay mỗi lần mở tồn kho/sửa đều GET mới. Bấm lại tab đang mở trước đây xóa dữ liệu nhưng không kích hoạt effect; nay no-op. Đổi tab/lưu form không render dữ liệu của tab cũ. Có ba test hồi quy riêng cho delay phân trang, tab đang mở và snapshot mới.
- **Log:** test fault SQL đã bắt được DEBUG ExceptionHandlerExceptionResolver lộ marker trong lượt đầu; đã chốt mức INFO cho framework web/JDBC/Hikari và chạy lại negative test/full suite đạt. Không xóa log lỗi cũ để che kết quả.

**Các lệnh đã thực sự chạy và kết quả:**

- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1`: **116/116**,0failure/error/skip; toàn bộ103test Phần7 +13auth/security mới. Kafka stop/start, duplicate/retry/replay/refund, payment/expiry và GA/ghế cũ vẫn đạt. Log `backend/target/part8-backend-final.log`, XML surefire; DB/broker test UUID được dọn, giữ demo.
- **PASS** `npm.cmd test --prefix frontend`: **45/45**; **PASS** `npm.cmd run build --prefix frontend` (TypeScript/Vite). **PASS** `npm.cmd test --prefix tools/contracts`:16operation/58payload; mọi operation đã triển khai.
- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1`: **66HTTP response** đúng OpenAPI, dashboard React/jsdom dùng API/Kafka/PG thật,25bản ghi/trang20+5; SQL2tickets/sold2/payment inbox1,0oversell/0vé sai trạng thái,9migration không thay. Packaged prod+mock từ chối startup, log không có token fixture/Authorization. Proof `frontend/reports/part8-live.json`, startup log trong TEMP tiền tố `ticketing_t8ui_7a2ec640346f401aaa32d0cc0b0df34f`.
- **PASS** `infra/test-part4-ui.ps1`: GA quantity2/tổng140000/replay/conflict409/CORS204; held3/sold0/capacity3. **PASS** `infra/test-part5-ui.ps1`: ghếA1+A2/expiry/rebook/GAreturncapacity2,0orphan/oversell/duplicateexpiry, restart không migrate lại.
- **PASS** `infra/test-part6-ui.ps1 -Kafka`: GA3+seats2 phát5vé unique; replay không đổi, failure/cancel nhả, late success0vé; restart phục hồi scheduled result/refundCOMPLETED; SQL0oversell/0vé sai/0compensation trùng, V1–V9. Hợp đồng HTTP hiện có66response.
- **PASS** `infra/verify-part8.ps1 -CreatePaginationFixtures` trên demo: total21/page0=20/page1=1/0ID trùng; healthUP,401/403,validation400, snapshot inventory/booking filter/outbox, backend loopback. Chỉ thêm số DRAFT còn thiếu để đủ21 cho thử Chrome, không reset/xóa dữ liệu.
- **PASS** npm audit frontend/contracts:0vulnerability; OSV77component không có vấn đề; actionlint CI và PowerShell syntax đạt (chưa có ShellCheck local). Gitleaks quét187file bàn giao và10commit lịch sử Git, không có leak; kết quả đã lưu trong evidence. `.env` ignored, không thuộc tập bàn giao; migration V1–V9 không đổi.

**Hướng dẫn và giới hạn:** README gốc/infra README có lệnh Windows và các bước Chrome: chọn Quản trị demo → Sự kiện/trang trước-sau → tạoDRAFT/publish → khách giữGA/ghế → tồn kho/booking/outbox. Bộ chọn persona là demo, không thay xác thực production. CI đã thêm dashboard live/packaged guard và artifact, nhưng **chưa chạy GitHub Actions cho working tree này** vì chưa commit/push. Native Computer Use ở lượt trước bị dừng vì không xác minh được URL; không dùng công cụ khác để vượt chặn, chưa có visual QA Chrome/mobile. DOM/API tests không chứng minh layout, keyboard thực tế hoặc mọi browser. Chưa benchmark/cluster nhiều broker/mất đĩa/chaos dài hạn/real payment. Test topic còn trên broker local với retention7ngày; T7 không tự purge dữ liệu. Không làm T8, không commit/push.

### 2026-11-12 — Phần6 hoàn tất: T5.1–T5.5

- **Đầu vào:** HEAD ca08aaf5b67a1666063d442600bb86c4ad4d16c7, working tree sạch. Đã đọc các tài liệu bắt buộc, ADR0002/0005 và source/test GA, ghế, expiry. ADR0006 ghi quyết định trước code: ghi result bền trước khi xử lý; processor dùng chung với expiry/cancel; ưu tiên timely success khi còn pending; vé có mã tra cứu demo; compensation chỉ PENDING. Không đổi stack, thêm Kafka/inbox hoặc tự commit/push.
- **T5.1 — Simulator:** mở POST simulate-payment cho CUSTOMER owner trong local/test. Chỉ nhận scenario/requestId; server quyết định status, recorded_at, giá và danh tính. Ledger và PaymentResultRecorded cùng commit dưới booking lock. Cùng requestId/scenario replay202/intent cũ; đổi scenario409. DELAY_TIMEOUT lưu SCHEDULED, recorded_at NULL, due=expiresAt+1s. Maintenance ghi result khi đến hạn và phục hồi result chưa processed. Correlation ID được lưu để delayed result giữ trace. Service ngoài profile hợp lệ bị từ chối403 trước khi truy cập DB.
- **T5.2 — Processor:** adapter, expiry và cancel gọi cùng PaymentResultProcessor. Khóa booking trước inventory theo thứ tự UUID; kiểm tra paymentResultId thuộc booking và có terminal result bền. Callback sai liên kết, unknown hoặc SCHEDULED bị từ chối. processed_at cùng state/owner chống xử lý lặp; không bỏ result vì aggregateVersion đã tăng. Success đúng hạn còn hold thì confirm; booking đã EXPIRED hoặc mất hold không được confirm lại. Failure sau CONFIRMED chỉ ghi ledger/log đối soát, không release.
- **T5.3 — Vé/bù trừ:** V8 thêm tickets và payment_compensations, PK/FK tổng hợp/unique/check/index; thêm correlation_id cho payments. V1–V7 giữ nguyên. Confirm tạo đúng3vé GA ordinal1..3 hoặc2vé ghế ordinal1 đúng seatId/label, mã EF-UUID unique. GET tickets chỉ owner/ADMIN, trả[] nếu chưa CONFIRMED. Success muộn khi pending ghi cả EXPIRED và REFUND_REQUIRED cùng release/history/outbox/compensation. Success sau CANCELLED/PAYMENT_FAILED chỉ tạo nghĩa vụ bù trừ và giữ state. Unique(payment_id) bảo đảm mỗi payment một nghĩa vụ PENDING; chưa có refund worker T6.
- **T5.4 — UI:** Checkout có3scenario và cancel, ghi rõ không thu tiền thật. Vé, mã, GA ordinal và nhãn ghế lấy từ API.26test frontend gồm7client/storage,12App và7Checkout đều đạt; TypeScript/Vite build đạt. Network/503/500 giữ nguyên bookingId/scenario/requestId qua reload, không tự POST khi mở lại; double-click chỉ gửi1request. Countdown không tự đổi state. Poll pending đến expiresAt+90s; sau EXPIRED poll thêm90s để nhận REFUND_REQUIRED, sau đó dùng nút cập nhật thủ công.
- **T5.5 — Cancel/admin:** cancel không nhận body; repeat CANCELLED trả200, body{}400, owner sai404, role sai403, state không hợp lệ409. Nếu cancel gặp timely result bền, processor commit confirm/vé trước khi trả409; không rollback confirm hợp lệ chỉ vì cancel bị từ chối. Failure/cancel chỉ nhả một lần. Admin giảm capacity2 dưới held/sold3 trong lúc payment xử lý bị409 và rollback cả price; đổi price/active đồng thời với paid seat vẫn giữ snapshot200000/tổng400000, ghế SOLD không bị expiry nhả.
- **Chống lặp:**20HTTP cùng requestId/scenario cộng20callback nội bộ lặp chỉ có1payment,1PaymentResultRecorded,1BookingConfirmed,3vé GA, held0/sold3. Đổi scenario bằng requestId cũ409. Không thêm unsigned provider callback HTTP; callback trong test là adapter nội bộ xác minh liên kết với ledger.
- **Tranh chấp thời hạn:** PAY-01 ghi result đúng hạn nhưng trì hoãn adapter; expiry gọi processor và nhận CONFIRMED/2vé/0BookingExpired. Fixture DB tại đúng deadline được confirm; muộn1microsecond phải REFUND_REQUIRED/0vé. PAY-02 expiry commit trước rồi2ghế được người khác giữ: booking cũ REFUND_REQUIRED/1compensation/0vé, owner mới vẫn giữ2ghế. Late success khi pending ghi đủ2transition EXPIRED/REFUND_REQUIRED.8race gần deadline lần cuối có7CONFIRMED và1REFUND_REQUIRED, đều khớp stored recorded_at; không suy thành bảo đảm mọi tải.8race cancel/payment có3CONFIRMED và5CANCELLED, held0/sold3. Các ca late riêng đều đạt.
- **Rollback:** fault PostgreSQL khi ghi result outbox rollback ledger/version. Fault ticket insert rollback confirm/inventory/history/confirm-outbox/vé, nhưng result gốc vẫn bền để retry cùng requestId. Fault failure history rollback release; fault compensation rollback cả late expiry/refund; khóa ghế thứ2 gây503 và không có partial sale. Bỏ fault rồi retry chỉ phát vé/chuyển trạng thái một lần. Lỗi biên dịch tạm trong test (var compound declaration) đã sửa và chạy lại đủ suite.
- **Ngân sách chung:** rà soát cuối phát hiện record/process không được cấp lại10s cho từng transaction. Đã dùng cùng deadline nghiệp vụ10s cho simulator và mỗi maintenance candidate; hết ngân sách sau khi record trả503 và giữ result bền. Test PostgreSQL với budget rút ngắn và trigger chậm0,8s xác nhận chưa bán/phát vé khi503; retry cùng requestId xử lý result cũ, đúng2vé/1confirm. Không coi đây là benchmark latency.
- **Backend cuối:** infra/test-part6.ps1 với EXPIRY_ENABLED=true đạt BUILD SUCCESS lúc2026-11-12T15:40:53Z:88test,0failure,0error,0skip;87test PostgreSQL thật và1context H2. Chi tiết: Payment30/Admin9/Booking16/Catalog2/Outbox4/ProductionProfile1/Schema1/SeatExpiry23/Seed1/Context1. DB UUID sạch mỗi lần, V1–V8; migrate lần2=0. Test properties tắt scheduler có kiểm soát. Test guard T4 được nâng từ defer sang confirm/SOLD/ticket, không bị xóa. Artifact tại backend/target/part6-evidence.json và surefire-reports; bản lưu tại [evidence/part6-2026-11-12.json](evidence/part6-2026-11-12.json).
- **Hợp đồng:** OpenAPI3.1 có14operation,11đã hiện thực;44ca kiểm tra dương/âm và40response HTTP live đúng schema. Ticket thêm seatId/seatLabel nullable; cancel không có body. Giữ7event type/schemaVersion1, không tạo thêm callback/check-in endpoint.
- **UI tích hợp thật:** GA UI Phần4 và Seat UI Phần5 chạy lại đều đạt. Checkout React trong jsdom gọi HTTP/PostgreSQL thật với TTL4s/worker1s: GA3+seat2 CONFIRMED với5code unique, replay không đổi; failure/cancel release; delayed EXPIRED→REFUND_REQUIRED/0vé. Cuối ca GA available9/capacity12, ghế SOLD. Không mock response/DB trong ca live.
- **Restart:** tạo payment SCHEDULED rồi restart đúng JVM test trên cùng DB. Ledger được phục hồi, booking REFUND_REQUIRED/0vé, replay giữ intent cũ; healthUP và Flyway No migration necessary. SQL có0ticket sai state/ordinal/booking,0oversell,0SOLD owner sai,0duplicate compensation,8version migration. Script PowerShell ban đầu đếm sai JSON[] rỗng; đã sửa đọc raw response và chạy lại đạt, không che giấu lỗi dữ liệu vé.
- **Windows demo:** launcher đã build/restart đúng runtime workspace, backend8080/healthUP áp dụng V8 không reset DB. TTL20s/worker1s chỉ override process, .env giữ nguyên. verify-part6.ps1 -WaitSeconds45 đạt trên catalog riêng cd62daa6-5d0f-4cdb-918c-4b610d99f6dc. GA booking87cc9e20-0a95-4008-9931-23dc5ca903c4 có3vé; seated booking e445f2a6-a583-4837-b7cd-33b5d922d4fe có2vé. Failure/cancel đúng; delayed b441664a-675d-45ea-97f3-7a710a5d9e6c là REFUND_REQUIRED/0vé. Giữ dữ liệu demo để xem, không đưa Git; frontend5173 đang chạy.
- **CI local/scan:** npm audit frontend/tools0vulnerabilities. CycloneDX BUILD SUCCESS, OSV2.7.1 scan69packages không có vấn đề. actionlint1.7.12 exit0; workflow thêm checkout live trên DB riêng/cổng8082. Gitleaks8.30.1 quét156file bàn giao (tập file không ignored) và8commit lịch sử, no leaks found. .env được git check-ignore xác nhận, không có trong tập bàn giao. git diff --check exit0. Chưa có GitHub Actions run cho working tree chưa commit.
- **Chưa kiểm chứng/ngoài phạm vi:** không có công cụ native browser nên chưa visual QA/screenshot; DOM/API tests không thay kiểm tra layout. Chưa benchmark latency/p95/TPS, pool exhaustion, statement cancellation độc lập hoặc mất mạng đúng lúc COMMIT. Provider/QR production/check-in ngoài MVP/task; Kafka/inbox/refund worker thuộc T6, vẫn Todo. Compensation mới PENDING, chưa báo hoàn tiền thành công.

T5.1–T5.5 Done,24/45task; không có phần chức năng T5 còn thiếu theo acceptance đã chạy. README, ADR0006, OpenAPI, DATA_MODEL, EVENT_CATALOG, TEST_PLAN và TRACEABILITY đã đồng bộ. T6.1 chưa bắt đầu; chưa commit/push.

### 2026-11-12 — Bắt đầu Phần6, quyết định trước code

- Đọc RULES/PROGRESS/TASKS/PRD/ARCHITECTURE/DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN, ADR0002/0005, kiểm tra GA/seat/expiry/migration/auth/UI/test. HEAD ca08aaf5b67a1666063d442600bb86c4ad4d16c7, working tree sạch; Docker Server29.9.1 chạy.
- ADR0006 chốt durable-result/processor transaction riêng, shared processor với expiry/cancel, nhiều result ưu tiên timely success khi pending, delayed scheduler, ticket code/seat fields, compensation chỉPENDING, callback nội bộ không thêm provider endpoint. PRD/ARCHITECTURE/API đồng bộ trước code phụ thuộc. T5 chưa Done, chưa commit/push.

### 2026-11-12 — Phần5 hoàn tất: T4.1–T4.5

- **Phạm vi/quyết định:** từ HEAD cf6852568cc9a4da427ea7874fb9921c284f0d52, working tree sạch, giữ stack/repo/GA. ADR0005 và PRD/ARCHITECTURE/API đã ghi trước code: giữ hash GA, canonical seat hash; schema/event chỉ mở shape seat đã chốt và thêm V7 owner FK; result payment bền defer cho T5.2, không confirm/fail/refund. Không payment/Kafka, không commit/push.
- **T4.1:** seats/availability hiện có đủ model/trạng thái, label unique; V7 thêm FK tổng hợp owner/event/type. Không sửa V1–V6. Map UI theo label/status từ public availability, không lộ owner/không tạo tọa độ venue giả. PostgreSQL test constraint duplicate label, HELD thiếu owner và owner khác type bị chặn; input/response schema đồng bộ OpenAPI.
- **T4.2:** BookingRequest oneOf GA/SEATED, SeatBookingRequest1..6UUID khác nhau/sorted lowercase; đảo thứ tự replay cùng key. SeatInventory khóa từng immutable UUID tăng dần, chỉ nhận AVAILABLE; HELD quá hạn trả HOLD_EXPIRY_PROCESSING cho đến release commit. Booking/item/owner/deadline/history/BookingHeld/key/response cùng transaction. Giá tổng server;202/Location sau commit. GaBookingRequest hash cũ không đổi. BookingTransactions tách runner chung với budget/retry hiện có.
- **T4.3:** ExpiryService lock booking SKIP LOCKED/recheck state/due bằng DB clock, kiểm tra result bền, InventoryRelease khóa ghế cùng thứ tự hoặc giảm GA có điều kiện, EXPIRED/expired_at/version/history/BookingExpired cùng commit. Scheduler mặc định5s/batch50, test profile tắt để test gọi có kiểm soát.20worker →1transition; repeat no-op, GA release/rebook đúng một lần; batch51→50/1/0; một lỗi không rollback booking khỏe. SUCCESS fixture recorded trước deadline →DEFERRED_PAYMENT/giữ hold, SCHEDULED chưa recorded vẫn expire. Processor T5 chưa triển khai.
- **T4.4:**50lệnh use case/2customer tranh2ghế/đảo thứ tự →1success/49conflict/1booking/2HELD/1owner.50HTTP tranh1ghế →1HTTP202/49HTTP409/1HELD/1booking.20duplicate đảo thứ tự →1bookingId/1body/1outbox/2HELD; cùng key đổi ghế409. Advisory gate chặn worker sau release/outbox nhưng trước commit: observer còn thấy PENDING/HELD, command mới chưa hoàn tất; mở gate thì worker commit, command mới nhận ghế; worker cũ lặp không nhả owner mới. Replay key cũ sau expiry/rebook không lấy lại hold.
- Ca overlap riêng2customer chọnA1+A2 vàA2+A3 đồng thời:1success/1conflict qua use case,2HELD/1owner/1booking/1outbox,2ghế cònAVAILABLE; không giữ dở ghế riêng của bên thua. Đây là test bổ sung sau các gate50request, không thay số đo HTTP50.
- **T4.5:** ghế thứ2 bị lock thật →503BOOKING_BUSY/Retry-After1/rollback ghế trước/key/booking; expiry gặp lock cũng rollback state/owner/outbox/history, retry được. Fault sau booking/expiry outbox rollback toàn bộ. Deadlock40P01 thật giữa2transaction →quan sát1deadlock,2commit qua3attempt tổng,version mỗi ghế=2/không hold giả. Fault SQLSTATE40P01 lặp tại bước response →đúng3attempt/503 và0hold/booking/outbox. Không coi test này là bảo đảm không có deadlock ở mọi tải.
- **Backend cuối:** `infra/test-part5.ps1` BUILD SUCCESS lúc2026-11-12T14:30:13Z,58tests/0failure/0error/0skip: Admin9/Booking16/Catalog2/Outbox4/ProductionProfile1/Schema1/SeatExpiry23/Seed1/Context1.57PostgreSQL thật+1H2. DB test UUID sạch mỗi lần, V1–V7, migrate lần2=0. GA35test cũ vẫn đạt; không reset demo. Lần cuối cố tình truyền EXPIRY_ENABLED=true: test properties chốt false và assert không có scheduler bean để tránh env local chen vào fixture; GA UI CLI false cũng chạy lại đạt dưới env true, seat live CLI true. Artifact tái tạo backend/target/part5-evidence.json; [bằng chứng lưu](evidence/part5-2026-11-12.json).
- **Frontend/contract:** App/client/styles có chọn type/ghế, trạng thái Trống/Đang giữ/Không khả dụng, stable key/payload qua503/network/reload; poll booking/availability3s, countdown chỉ hiển thị, không tự đổi EXPIRED/AVAILABLE.16test frontend (5client/storage+11SSR/DOM) đạt; TypeScript/Vite build đạt. OpenAPI3.1/14operation/8implemented,32ca dương/âm và22response HTTP live GA/seat đúng schema. Không đổi event schemaVersion/payload hoặc thêm endpoint expiry công khai.
- **Live UI/DB/restart:** `test-part4-ui.ps1` GA UI cũ đạt. `test-part5-ui.ps1` DB/cổng/JAR riêng, TTL4s/worker1s bật thật: chọnA1+A2/202/tổng400000/replay/clash409, schedulerEXPIRED, ghếAVAILABLE, rebookIDmới,expire lần2; GA2vé cũng hết hạn và available trở lại2. SQL0orphanhold/0ownerEXPIRED/0oversell/0duplicateBookingExpired, migrations7. Restart JVM test cùngDB →healthUP/No migration necessary; script dọn đúng process/JAR/DB của mình. Ca live không mock HTTP/DB.
- **Windows/demo:** launcher hỗ trợ -RestartExisting (chỉ đúng runtime workspace, build đạt trước stop), -HoldSeconds20/-ExpiryIntervalSeconds1 chỉ override process. Backend mới8080/healthUP đã áp dụng V7 lên demo không reset, nhả4hold cũ đến hạn. `verify-part5.ps1 -WaitSeconds45` thực tế: booking b949fc10-e49c-4a81-a58d-8be99c299700 giữA1/A2/400000/replay; schedulerEXPIRED một lần/ghếtrống; replay keycũ vẫnbodygốc; rebook f4a4b816-7109-4e34-a69f-4244cf1854af, sau đó scheduler cũng expire. Frontend5173 tiếp tục chạy. `.env` giữ nguyên; tham số mặc định vẫn600s/5s.
- **Lỗi phát hiện và sửa:** điều kiện PG_IT_URL trên lớp cha không được kế thừa khiến AdminTest chạy khi launcher không có DB test; thêm annotation trực tiếp, bộ58test vẫn chạy đủ. Launcher cũ stop trước build, nay build trước stop để build lỗi không làm mất runtime. Đọc restart log đang được process ghi bằng ReadAllText bị Windows khóa file; đổi Get-Content và chạy lại toàn kịch bản restart đạt. Lần smoke đầu do launcher lỗi không có server chưa được tính pass; các lệnh đã chạy lại đạt như trên.
- **CI local/scan:** npm audit frontend/tools0vulnerabilities; CycloneDX SBOM BUILD SUCCESS, OSV2.7.1 scan69packages không có vấn đề, actionlint1.7.12 exit0. CI thêm bước seat UI/scheduler trên DB riêng sau GA live; chưa có GitHub Actions run cho working tree này. Gitleaks/diff check cuối ghi dưới đây.
- **Kiểm tra bàn giao:** Gitleaks8.30.1 scan137file tracked/untracked không ignored (~648KB) và7commit lịch sử: no leaks found. `.env` được git check-ignore xác nhận; không đưa file này vào tập scan/bàn giao. git diff --check exit0, HEAD vẫncf68525/chưa commit/push. Frontend16test/build và contract32ca được chạy lại cuối vẫn đạt. Backend8080/healthUP, Vite5173 HTTP200; địa chỉ chỉ bind127.0.0.1. Không có cảnh báo scanner còn tồn đọng.
- **Giới hạn:** không có công cụ/kết nối native browser để visual QA/screenshot, DOM/HTTP tests không chứng minh bố cục Chrome. Chưa benchmark latency/p95/TPS hoặc fault pool exhaustion/statement cancellation độc lập/mất mạng đúng lúc COMMIT. Payment processor/tickets/compensation/Kafka thuộc phần sau, không đánh dấu Done. Result payment bền được giữ an toàn chờ T5.2. Không có blocker chức năng T4; T5.1 vẫn Todo.

T4.1–T4.5 Done theo các acceptance đã chạy,19/45task. README/infraREADME có lệnh Windows và cách xem ghế tự hết hạn; chưa tự commit/push.

### 2026-11-12 — Bắt đầu Phần5, quyết định trước code

- HEAD cf6852568cc9a4da427ea7874fb9921c284f0d52, working tree sạch; Docker Server29.9.1 đang chạy. Đọc RULES/PROGRESS/TASKS/PRD/ARCHITECTURE/DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN, DESIGN và code/migration/test Phần4.
- ADR0005 chốt mở lại request SEATED đã thiết kế, giữ hash GA tương thích, thêm composite owner FK bằng migration mới, khóa từng ghế theo UUID string; worker mỗi booking một transaction, scheduler5s/batch50. Mâu thuẫn phạm vi payment T4.3/T5.2 giải quyết bằng defer result bền (giữ inventory), không implement payment trước; đồng bộ PRD/ARCHITECTURE/API trước code phụ thuộc. Chưa đánh dấu task T4 Done.

### 2026-11-12 — Phần4 hoàn tất: T3.1–T3.5

- **Đầu vào/phạm vi:** đọc RULES/PROGRESS/TASKS/PRD/ARCHITECTURE/DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN, hợp đồng và source sau Phần3; HEAD d476b78086b8d4daeacf4ed0060eba7d398684f2, working tree sạch lúc bắt đầu. Giữ repo/stack, không Redis, không chọn ghế/payment/Kafka; không commit/push.
- **Mâu thuẫn đã giải quyết trước code:** DATA_MODEL muốn row idempotency hoàn chỉnh nhưng ARCHITECTURE reserve key trước booking. ADR0004 chốt NULL tạm trong cùng transaction và deferred trigger kiểm tra tại COMMIT, không có STARTED bền. POST chỉ nhận GaSelection trong phạm vi T3; SeatSelection/CreateBooking giữ thiết kế T4. Làm rõ event không PUBLISHED/CLOSED404, inactive/ngoài giờ bán409; replay response gốc và GET trạng thái hiện tại. PRD/state/envelope không đổi.
- **T3.1:** BookingController có POST202/Location sau commit, GET owner/ADMIN và GET mine CUSTOMER phân trang. BookingQueryService dùng snapshot; history ban đầu NULL→PENDING_PAYMENT. Validation UUID/key/field/quantity1..6, token lạ401/role sai403/owner khác404; không lấy userId/role/giá client. Tổng giá và expiresAt do server/DB tính. OpenAPI đánh dấu đúng8operation hiện thực, còn6operation tương lai chưa chạy.
- **T3.2:** V5 thêm idempotency_keys và booking_status_history; PK(account,operation,key), SHA256 canonical, composite FK account/booking, response202 và deferred completion guard. BookingCommandService INSERT ON CONFLICT rồi SELECT statement mới READ COMMITTED; duplicate chờ commit/replay cùng body/Location, hash khác409 IDEMPOTENCY_KEY_REUSED. Replay sau EXPIRED dùng fixture transition/outbox đúng, vẫn bookingId/body gốc; chưa triển khai expiry. Test key scope khác account và UUID hoa/thường/field order/2.0 đạt.
- **T3.3:** CatalogBookingAccess khóa SHARE event rồi type cùng thứ tự admin; GaInventory conditional UPDATE held+sold+quantity<=capacity. Booking/item/snapshot/GA/history/BookingHeld/version1/key/response cùng transaction, sold không tăng. Capacity conflict hoặc lỗi PostgreSQL sau outbox rollback tất cả; incomplete key commit bị chặn. V6 bổ sung check initial history vì SQL CHECK có thể nhận UNKNOWN khi from_status NULL; không sửa V1–V5 đã áp dụng. BookingPolicy SET LOCAL timeout;55P03/57014 trả503/Retry-After1 sau rollback,40P01/40001 retry tối đa3attempt trong budget. Fault injection40001 lần đầu thành công sau retry và lỗi liên tục dừng3attempt đạt; chưa tạo deadlock40P01 thật.
- **T3.4:** PostgresBookingTest16test mới, bao gồm success/sold-out/invalid/owner/history/idempotency/expired/rollback/row lock/key lock/retry/concurrency.50request use case từ5customer capacity10:10success/40conflict, held10/sold0/bookings10/outbox10.50HTTP đồng thời riêng:10HTTP202/40HTTP409, held10/bookings10/outbox10.20request cùng key quantity2:1bookingId/1body/1outbox/held2. Cùng key khác payload đồng thời:202/409,1booking. Query sum(item.quantity)=10 trong ca5customer. Không suy thành benchmark hoặc bảo đảm mọi tải; [bằng chứng lưu](evidence/part4-2026-11-12.json), output tạo lại tại backend/target/part4-evidence.json.
- **T3.5:** frontend App/client/styles dùng fetch thật catalog/detail/availability/POST/poll GET/history. Chọn GA1..6; chỉ hiển thị đơn giá catalog/tổng server, mã lỗi/correlationId. Double-click guard; key/payload cố định lưu sessionStorage trước POST,503/network/reload giữ để retry, không tự tạo key mới. Sau thành công cần chủ động đặt thêm.11test frontend (4client/storage +7SSR/DOMinteraction) đạt; TypeScript/Vite production build đạt.
- **Build/DB thực tế:** `infra/test-part4.ps1` → BUILD SUCCESS,35tests/0fail/0error/0skip (34PostgreSQL +1context H2); report surefire: Admin9/Booking16/Catalog2/Outbox4/ProductionProfile1/Schema1/Seed1/Context1. DB test UUID mới mỗi lần, Flyway V1–V6 từ sạch; gọi migrate lần2 migrationsExecuted=0. Không reset demo. Docker Desktop đã được mở lại khi engine dừng; PostgreSQL17.11 thật được dùng cho lần chạy cuối. JDK25.0.5, i5-11400H6core/12logical, RAM~15,78GiB, pool10/2/acquisition2s.
- **Live UI:** `infra/test-part4-ui.ps1` chạy JAR build cuối trên DB/cổng riêng, React App trong jsdom dùng HTTP thật. Tạo catalog capacity3/giá70000 bằng API admin; UI đặt2vé nhận202/tổng140000; replay cùng key/body không thêm vé; client khác giữ vé cuối khiến snapshot cũ, UI nhận409 rồi refresh hết vé; lịch sử2booking, preflight204. SQL held3/sold0/capacity3. Script dừng/dọn chỉ process/DB test do chính nó tạo. Chạy lại sau build cuối đạt; không mock response trong ca này.
- **Hợp đồng/demo:** tools/contracts `npm test` PASS OpenAPI3.1/14operations/30ca; `test:live` PASS15response HTTP schema. `infra/verify-part4.ps1` PASS202/Location/replay/owner/history và V1–V6 mỗi version đúng1lần. Backend demo8080 và Vite5173 đang phục vụ khi kiểm tra HTTP. Smoke và live-contract mỗi script giữ1vé ở lần đầu bằng key riêng cố định; seed không overwrite/reset inventory. README có lệnh và ví dụ PowerShell.
- **CI local/scan:** frontend và tools/contracts npm audit0vulnerabilities; CycloneDX SBOM BUILD SUCCESS, OSV2.7.1 scan69packages không có vấn đề; actionlint1.7.12 exit0. Workflow thêm job-step live UI/HTTP contract trên DB CI riêng sau backend verify. GitHub Actions chưa chạy vì chưa commit/push; không dùng CI commit cũ để nghiệm thu thay đổi mới. Kết quả Gitleaks/diff được ghi trong mục kiểm tra cuối bên dưới.
- **Kiểm tra cuối:** Gitleaks8.30.1 scan tập file Git tracked/untracked không ignored (~545KB) và6commit lịch sử, no leaks found; `.env` vẫn bị Git ignore. git diff --check exit0 (chỉ có nhắc LF/CRLF của Git). Ví dụ README đã thử thực:202/tổng200000, replay bookingId cũ, đổi quantity409 IDEMPOTENCY_KEY_REUSED, GET owner/mine đạt; PowerShell5 cần đọc response stream nếu ErrorDetails trống, README đã sửa. Vite5173 HTTP200. Demo GA còn96vé: smoke1 +contract1 +README2 đang được giữ,3booking của customer; không reset. Số này là snapshot tại lần kiểm tra, có thể thay đổi khi người dùng thử UI.
- **Giới hạn còn lại:** công cụ computer-use không kết nối browser/nativehost, nên chưa có Chrome visual QA/screenshot; DOMinteraction và UI với API thật đã đạt nhưng không chứng minh layout. Automatic approval review từ chối lệnh restart JVM demo, lý do duy nhất trả về “blocked by policy”; chưa kiểm chứng restart process demo lần này, backend cũ vẫn chạy. Flyway migrate lần2 đã được kiểm tra riêng trên PostgreSQL thật. Chưa fault-inject pool exhaustion/statement cancel độc lập/mất mạng lúc COMMIT/deadlock40P01 thật. Chưa làm T4.1 trở đi: ghế, worker, thanh toán/vé/Kafka; booking qua TTL vẫn pending/held cho đến phần worker, không tự expiry trong GET/UI.

Lệnh tự kiểm tra: README gốc và infra/README; T3.1–T3.5 Done theo acceptance đã chạy,14/45task. Không có task chức năng còn thiếu trong Phần4; các giới hạn kiểm chứng nêu trên vẫn được giữ rõ.

### 2026-11-12 — Phần3 hoàn tất: T2.1 → T2.5

- Thực hiện tuần tự theo các gate bên dưới, không sang task sau trước khi gate trước đạt. Không sửa V1, không tạo repo mới, không thêm booking API/T3.1 hoặc UI đặt vé.
- Migrations: V2 tạo users/events/ticket_types/seats/inventory_ga và bookings/booking_items nền; V3 tạo payments/outbox_events; V4 chặn JSON null bookingId trong outbox. PK/FK tổng hợp/unique/check/index, Flyway history public.5bảng mục tiêu còn lại không thuộc Phần3. ADR0003 giải thích trước code các ranh giới và điểm contract về ghế/mode.
- Seed:2persona,2event published (ghế4 A1–A4 giá200000;GA100 giá100000), INSERT ON CONFLICT DO NOTHING chỉ local/test. Không overwrite dữ liệu admin đã sửa. Token public fixture chỉ có trong profile local/test, không lưu DB/log; prod không có seed runner/token mặc định, bật mock trên profile ngoài local/test gây startup failure.
- API đã chạy: GET /api/events, GET /api/events/{id}, GET /api/events/{id}/availability; POST /api/admin/events, PATCH /api/admin/events/{id}. Controller chỉ delegate, transaction/lock/validation ở service. Public chỉ published; lỗi có code/message/correlationId; capacity update khóa/recheck counts; giá item snapshot bất biến. Không có DELETE/mode/seat update trong v0.
- HTTP policy:body128KiB catalog,8KiB command; UTF-8/identity JSON, depth10, field lạ/null/sai kiểu bị từ chối; reject chunked quá limit; CORS allowlist5173, preflight204; principal từ token/DB, header X-Role/X-User-Id không đổi danh tính. SQLSTATE55P03/57014/40P01/40001/08 sau rollback trả503+Retry-After1; không suy timeout là idempotency conflict.
- **Build/test cuối:** powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part3.ps1 → BUILD SUCCESS,19tests/0fail/0error/0skip. DB test mới UUID, migrate V1–V4 từ sạch, Flyway migrate lại0; không reset DB/volume demo. Test report backend/target/surefire-reports: Admin9, Catalog2, Outbox4, ProductionProfile1, Schema1, Seed1, Context1. Context H2 chỉ là1test;18test còn lại chạy với PostgreSQL17.11 thật.
- **Nội dung tests:** FK/PK/unique/check/index, ghế HELD thiếu owner bị chặn, FK event/type lệch bị chặn; seed idempotent;2mode/detail/availability và400/404;401/403/giảrole; startup fail + HTTP demo bearer bị từ chối ở prod; admin create/patch/invalid timestamp/UUID, bảo vệ held+sold/giá snapshot/ghếHELD; row-lock race; timeout thật rollback trước503; CORS/body/chunked/media/depth; ledger/envelope/commit/rollback/duplicateversion/transaction mandatory.
- **Live demo và restart:** verify-part3.ps1 PASS; HTTP200/UP,2sự kiện,4ghế/GA100,401/403,V1–V4 đúng một lần. Restart backend cùng DB: “Schema public is up to date. No migration necessary”; verify tiếp tục PASS. Backend đang chạy PID8276 lúc bàn giao, http://127.0.0.1:8080/api/events; hai port8080/5432 chỉ bind127.0.0.1.
- **Contract/CI local:** npm ci/test tools/contracts PASS28ca, OpenAPI14operation/7event/6state; npm run test:live PASS9response HTTP đúng schema; x-implemented đánh dấu đúng5operation catalog. Frontend npm ci/test/build/audit đạt1test/0vulnerability (không thêm UI). SBOM CycloneDX tạo thành công; OSV2.7.1 kiểm tra69runtime packages, No issues found. actionlint1.7.12 exit0; npm audit tools0; Gitleaks quét5commits và tập file tracked/untracked không bị ignore, no leaks found; git diff --check đạt. CI thêm PostgreSQL service để không skip integration tests; chưa có CI GitHub cho thay đổi lần này.
- **Lỗi đã sửa:** repository proxy không cho đọc field trực tiếp; chuyển JdbcTemplate vào service. Full-suite seed query được scope theo fixture ID; context H2 tắt seed rõ ràng. SQLSTATE55P03 được Spring bọc UncategorizedSQLException nên thêm mapping cause chain. Windows khóa JAR target khi app đang chạy; launcher nay chạy bản sao Git-ignore infra/.local-runtime/backend.jar, giữ artifact build tự do. Test/build được chạy lại sau sửa và đạt như trên.
- **Còn lại:** Không có publisher/consumer Kafka, payment processing, expiry, đặt/giữ vé, API customer booking hay giao diện đặt vé. Outbox chỉ là writer transaction và ledger nền; test dùng booking fixture, không phát event giả khi admin tạo catalog. Chưa commit/push. T3.1 vẫn Todo.

Lệnh tự kiểm tra và ví dụ API quản trị tại README/infra/README. Mở /api/events bằng trình duyệt để xem JSON2sự kiện; frontend5173 hiện vẫn là scaffold.

### 2026-11-12 — Phần 3, gate T2.1 và T2.2

- Đọc tài liệu/hợp đồng và source; HEAD ban đầu 46470c14b0de0610379d0e6535a0f96f17a969e5, working tree sạch. Docker ban đầu chưa chạy; đã mở Docker Desktop và xác minh Server29.9.1. Giữ nguyên repo và V1.
- ADR0003 ghi rõ trước code: bookings/items chỉ là storage nền cho FK, chưa có booking API; v0 không có xóa/sửa mode/seat qua PATCH; catalog không phát event BookingHeld giả.
- T2.1: V2 catalog + bookings/items nền. `infra/test-part3.ps1 -Tests PostgresSchemaTest` BUILD SUCCESS,1test/0fail trên DB PostgreSQL17.11 mới tách biệt; Flyway chạy lần2 migrationsExecuted=0; constraint role/FK/giá/capacity bị vi phạm được từ chối; index tồn tại. Script chỉ xóa DB test UUID do chính nó tạo, không reset DB demo. T2.1 đạt trước khi viết seed.
- T2.2: seed local/test có2persona/2event/2type/4ghế/GA100, INSERT ON CONFLICT không overwrite. `-Tests PostgresSeedTest` BUILD SUCCESS,1test/0fail; seed gọi thêm2lần vẫn đúng số hàng; profile prod/mixed không được bật mock auth. T2.2 đạt trước khi viết catalog API. Token nằm trong profile fixture, không trong DB/log.
- T2.3: `-Tests PostgresCatalogTest` BUILD SUCCESS,2test/0fail; HTTP thật random port/PostgreSQL thật kiểm tra2mode,4ghế/GA100,list/detail/availability, UUID/phân trang400 và404. Read service dùng snapshot REPEATABLE_READ, chưa hold inventory. Gate đạt trước khi thêm admin/auth.
- T2.4: `-Tests PostgresAdminTest` BUILD SUCCESS,7test/0fail sau sửa lỗi truy cập field trực tiếp trên repository proxy (đã inject JdbcTemplate vào service). Kiểm tra401/403 và X-Role/X-User-Id không đổi principal, startup fail khi mock bật trên prod, catalog create/patch, validation, rollback cả title khi capacity conflict, giữ giá snapshot, ghế HELD không sửa/xóa qua v0, CORS204/403, body quá128KiB cả chunked413, encoding415, GET body400. Test inventory row bị khóa rồi held_count tăng: admin đợi lock rồi từ chối capacity không đủ. Gate đạt trước khi tạo V3/outbox.
- T2.5: `-Tests PostgresOutboxTest` BUILD SUCCESS,4test/0fail trên PostgreSQL thật: commit cùng booking fixture/outbox; lỗi sau append rollback cả2; duplicate aggregateVersion rollback cả2; transaction MANDATORY; payload sai từ chối; ledger terminal thiếu recorded_at bị chặn, row hợp lệ được nhận. Không có Kafka publisher hoặc booking API. V3 tạo payments/outbox; V4 bổ sung check JSON null bookingId bằng migration mới (không sửa V3 đã áp dụng).

### 2026-11-12 — T1.4: Chốt API/domain v0

- **Đầu vào:** đọc RULES/PROGRESS/PRD/ARCHITECTURE/DATA_MODEL/API_CONTRACT/EVENT_CATALOG, TASKS và TEST_PLAN. Working tree sạch lúc bắt đầu; HEAD 63db4cc2736c20e1aec939df64bbb6099694ef3a. Người dùng xác nhận đã commit/push T1.3 và CI xanh; đây là xác nhận từ người dùng, không phải lần audit CI mới trong T1.4.
- **Đã chốt:** DATA_MODEL có ER đủ14 bảng, kiểu/nullability/PK/FK tổng hợp, unique/check/index, delete policy và invariant cần domain. Giữ V1, không tạo SQL V2. paymentIntentId nằm trên booking; paymentResultId=payments.id; requestId client=payments.request_id. Replay audit giới hạn3 lần/event trên outbox; không xóa inbox.
- **Artifact:** docs/contracts/openapi.json (OpenAPI3.1,14 operations, request/response/error/header/role/profile), events.schema.json (JSON Schema2020-12,7 type), booking-transitions.json (6state/5cạnh), examples.json (fixture). ADR0002 chốt HTTP/domain; TRACEABILITY nối PRD/API/event/bảng với task/test. Đồng bộ PRD/ARCHITECTURE/DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN/README tài liệu.
- **Giới hạn:** body8192 byte booking/payment/replay,131072 byte catalog; UTF-8, identity encoding, reject field lạ, quantity1..6, page0/size20/max100; CORS chỉ hai origin local5173, credentials=false. lock_timeout1500ms/statement5s; connection acquisition2s, attempt5s/tổng10s/request15s là ngân sách ban đầu cần enforce và test khi triển khai. Reserved IDEMPOTENCY_IN_PROGRESS/RATE_LIMITED chưa phát trong baseline. 55P03/57014 rollback→503BOOKING_BUSY+Retry-After1, không quy mọi timeout thành idempotency conflict.
- **Kiểm tra đã chạy:** npm ci --prefix tools/contracts; npm test --prefix tools/contracts PASS: Swagger Parser13.1.0 xác nhận OpenAPI/$ref,14 operations; AJV8.20.0 + formats3.0.1 kiểm tra28 ca dương/âm,7 event fixture, enum state và5cạnh đối chiếu PRD; kiểm tra role/profile,Location/Retry-After,CORS. Không giả lập auth bằng việc chỉ xem metadata.
- **Đo request thực:** JSON.stringify UTF-8: GA6=117byte,6ghế=350byte, catalog20type/tổng200ghế/Unicode ở giới hạn field=51839byte. Chọn8192/131072 có headroom; byte limit vẫn ưu tiên nếu client gửi whitespace/escape làm body lớn hơn. Đây là byte của fixture, không phải benchmark HTTP.
- **Đo DB thực:** tools/contracts/measure-lock.ps1 trên PostgreSQL17.11 demo, hai kết nối advisory lock; SET LOCAL lock_timeout1500ms/statement_timeout5s, trả55P03, ROLLBACK; elapsed1635ms gồm Docker/psql overhead. Script không tạo/sửa bảng nghiệp vụ, không reset dữ liệu. Chưa có số đo khóa ghế/GA hoặc idempotency table vì T2.1 chưa làm.
- **Tự động hóa:** tools/contracts có package-lock, script verify và đo lock; thêm job contracts vào CI và Dependabot cho tool. npm audit tool=0 vulnerabilities; actionlint1.7.12 exit0; Gitleaks quét tập file tracked/untracked không bị Git ignore, no leaks found; git diff --check đạt. GitHub CI cho thay đổi T1.4 chưa chạy; backend/frontend runtime không thay đổi trong task này.
- **Còn thiếu ngoài T1.4:** SQL migration/business controllers/auth seed; enforce body/CORS/deadline/error handler; test boundary byte/chunked/preflight/owner và contention thực theo TRACEABILITY. T1.4 đạt acceptance thiết kế và kiểm tra; T2.1 vẫn Todo, chưa thực hiện. Chưa commit/push lần này.

Lệnh kiểm tra lại từ thư mục gốc:

```powershell
npm.cmd ci --prefix tools/contracts
npm.cmd test --prefix tools/contracts
# PostgreSQL demo cần đang chạy cho phép đo sau:
powershell -NoProfile -ExecutionPolicy Bypass -File tools/contracts/measure-lock.ps1
```

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
## 2026-11-13 — Phần7: T6.1–T6.5 Done

Tiếp tục từ HEAD5942ed0 và các thay đổi hiện có, không làm lại Phần6. Đã đọc RULES/TASKS/PROGRESS/ARCHITECTURE/DATA_MODEL/EVENT_CATALOG/API_CONTRACT/TEST_PLAN, ADR payment/expiry/replay và kiểm tra source/migration/outbox. Quyết định trước code tại [ADR0007](adr/0007-kafka-and-recovery.md). Suite chạy đêm12/11, đối chiếu hoàn tất13/11: [bằng chứng Phần7](evidence/part7-2026-11-13.json).

| Task | Kết quả và file chính | Kiểm chứng thực tế |
|---|---|---|
| T6.1 | V9: inbox PK(consumer,event_id), hash envelope, audit/recovery/refund history; EventCodec giữ schema v1 | Schema/thuộc tính lạ/key sai bị chặn; unique và ID khác nội dung bị chặn; migration sạch/rerun đạt |
| T6.2 | OutboxPublisher gửi sau commit, ACK version thấp nhất; session leader ngoài transaction nghiệp vụ;3attempt/backoff1/2s rồi FAILED giữ record | Broker stop/start thật hai ca; version1→2→3; version sau không vượt lỗi; ACK rồi DBmarkfail gửi lại cùngID; publisher thứ2 bị chặn bởi leader |
| T6.3 | EventConsumer payment dùng PaymentResultProcessor chung; audit nhận đủ7type/version riêng; inbox+business cùngTX | Paymentversion2;10duplicate/restartconsumer không tăng vé/history; gap3trước1/2 park rồi phục hồi; replay BookingConfirmed không phát vé; duplicate failure/expiry không nhả hold khác |
| T6.4 | FailureStore lưu raw/metadata trước ACK; DLQ gửi sau commit; OperationsController/ReplayService chỉ admin local/test, audit và tối đa3request/event | Ticketfault:3delivery đo bằng PGsequence, inbox/ticket/state/outbox rollback; sửa lỗi+replay phục hồi. Poisonrecord giữDB/đọcKafkaDLQ thật. 401/403/profile/validation/audit/dedup/limit3 đạt |
| T6.5 | RefundRecoveryService khóa cùng nghĩa vụ unique(payment_id); attempt/history/status cùngTX, tối đa3attempt; không tiền thật | 12caller chỉ1completion; fail/retry có2history; rollback không lưu nửa chừng; hết3attempt giữFAILED. Success sauCANCELLED vẫn phục hồi; REFUND_REQUIRED không phát vé/nhận lại inventory |

### Lệnh đã chạy và kết quả

- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part7.ps1`: **103/103**,0failure/error/skip (15Kafka +87PostgreSQL cũ +1H2). Hai DB và broker/volume UUID được dọn, giữ demo. Log: backend/target/part7-final.log. Launcher local sau đó chạy contextH2 và ghi lại surefire; JSON đã giữ số liệu full suite trước bước này, không lấy số skip launcher làm nghiệm thu.
- **PASS** `npm.cmd test --prefix frontend`: **26/26**; `npm.cmd run build --prefix frontend`: TypeScript/Vite đạt.
- **PASS** `npm.cmd test --prefix tools/contracts`: OpenAPI3.1,14operation,**49payload**;13operation đã triển khai, admin bookings T7 chưa làm.
- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part6-ui.ps1 -Kafka`: **50HTTP response** đúng OpenAPI; DOM/API/Kafka/PostgreSQL thật: GA3+ghế2 phát5vé unique; duplicate giữ kết quả, failure/cancel nhả, late success0vé; restart JVM phục hồi scheduledpayment/refundCOMPLETED; SQL0oversell/0vé sai trạng thái/0compensation trùng; V1–V9 mỗi bản một lần, restart không migrate lại.
- **PASS** `infra/test-part4-ui.ps1`: GA quantity2/tổng140000/replay/conflict409/history/CORS204; held3/sold0/capacity3. **PASS** `infra/test-part5-ui.ps1`: A1+A2/replay/clash/expiry/rebook/GAreturncapacity2; SQL0orphan/oversell/duplicateexpiry và restart không migrate lại.
- **PASS** `infra/start-local.ps1 -RestartExisting -HoldSeconds 20 -ExpiryIntervalSeconds 1`: chỉ thay JVM runtime đúng workspace, giữ DB/.env/volume; DBdemo V8→V9 đúng một lần, health200/UP. **PASS** `infra/verify-part7.ps1`: booking415c2884-00c7-4198-896a-e8c4874dd1fa,CONFIRMED,2vé,sold2,paymentinbox1,projection3,audit1 cho2requestreplay giống nhau,0outbox chưa publish.
- Snapshot DBdemo sau smoke: **71PUBLISHED**, auditinbox71/paymentinbox15, **5compensationCOMPLETED**,0GAoversell,0vé trên booking chưaCONFIRMED; V1..V9 đều count1. Đây là snapshot phiên kiểm tra, không là số bất biến cho lần sau.
- **PASS** actionlint CI; npm audit frontend/contracts0vulnerability; Gitleaks candidate tree không lộ secret. **FAIL ban đầu** OSV6advisory ở2codec Kafka; đã pin patch lz4-java1.11.4/snappy-java1.1.10.9 theoADR0007 và chạy lại full103/Kafka/live. **PASS cuối** OSV77component không báo vấn đề. Chưa scan image broker/OS riêng.

### Lỗi đã sửa và giới hạn

Đã sửa healthcheck Kafka khi cổng host khác9092 (dùng listener nội bộ29092); điền next_attempt_at cho outbox cũ; query test dùng held_count/sold_count và fixture deadline hợp check; script Windows bỏ bọc mảng lồng Invoke-RestMethod (DB đã phát đúng2vé). Không xóa migration/test để làm xanh. V1–V8 không đổi; V9 là migration duy nhất mới.

CI thêm broker/database riêng, evidence và checkout Kafka; **chưa chạy GitHub Actions của thay đổi chưa commit/push**. Browser dùng jsdom/HTTP, chưa visual QA thật. RF1/singlepublisher là MVP; chưa chứng minh mất volume, cluster nhiều node, network partition dài hạn hoặc crash đúng mọi cửa sổ COMMIT. Retry/DLQ/refund hết ngân sách giữ record để điều tra, không tự reset hoặc ép side effect; chưa có API force refund. T6 không tự purge inbox/outbox/recovery. T7 trở đi chưa làm.

README/infra README/runbook có lệnh Windows và recovery theo version. Không commit/push; người dùng xem diff và tự quyết định.

## 2026-11-13 — Sửa CI#31/de527c0: cô lập fixture và hồi quy pagination admin

Đã đọc RULES và kiểm tra trạng thái Git, workflow, fixture/migration, cleanup, truy vấn admin và log thật của [run#31](https://github.com/Haolo333z/event-ticketing-platform/actions/runs/43349485060), backend job131636787039. HEAD ban đầu là de527c070eaa7971f530d81511f6422ef9a1d55f, working tree sạch. [Bằng chứng tổng hợp](evidence/ci31-pagination-fix-2026-11-13.json) giữ thứ tự class, kết quả và SHA256 log local.

### Nguyên nhân và phạm vi sửa

Đây là lỗi cô lập dữ liệu và giả định của test, chưa thấy lỗi API trong ca tái hiện. `PostgresOutboxTest.businessAndOutboxCommitTogether` commit một booking/outbox rồi không dọn. CI chạy class này trước `PostgresDashboardSecurityTest` trên cùng `PG_IT_URL`; test dashboard tạo thêm hai booking nhưng khẳng định tổng toàn database phải bằng2. API trả3 là đúng. Tái hiện bản cũ trên DB sạch với outbox chạy trước: **14test,1failure**, đúng expected2/actual3; sau cleanup dashboard còn đúng **1booking/1outbox** của test outbox. Không phải dữ liệu demo có sẵn trên runner.

`BookingQueryService.admin` đếm tất cả booking khớp status, dùng cùng transaction `REPEATABLE_READ` và `ORDER BY created_at DESC,id DESC LIMIT/OFFSET`. Không sửa production query, API, migration hoặc cố định thứ tự test để che lỗi. Thứ tự class khác nhau giữa local và CI đã làm lần kiểm tra local trước đó bỏ sót lỗi.

- `PostgresOutboxTest.java`: ghi UUID fixture và dọn đúng booking/payment/outbox do từng test tạo trong `@AfterEach`. Giữ nguyên assertion xác nhận dữ liệu đã commit trước khi dọn và các assertion rollback.
- `PostgresDashboardSecurityTest.java`: chủ động có booking nền thuộc sự kiện khác; xác nhận chính xác hai ID của fixture mục tiêu. Lấy snapshot DB không sắp xếp rồi tính thứ tự mong đợi độc lập trong Java. Hai booking mục tiêu trùng `created_at` để kiểm tra UUID DESC; booking nền cũ hơn để kiểm tra thời gian DESC. Kiểm tra toàn bộ trang size1, total theo snapshot, status filter, đúng ID/event/status, không trùng ID và trang cuối rỗng. Cleanup chỉ xóa fixture của mình; không truncate hoặc sửa booking của test khác.
- `TEST_PLAN.md`: ghi ca hồi quy dữ liệu nền/thứ tự class. `PROGRESS.md` và JSON evidence ghi kết quả thật. Không thêm task mới vào TASKS.

### Kiểm tra đã chạy

| Kiểm tra | Kết quả thực tế |
|---|---|
| Tái hiện trước sửa: `-Dtest=PostgresOutboxTest,PostgresDashboardSecurityTest -Dsurefire.runOrder=reversealphabetical -Duser.timezone=UTC verify` trên DB UUID sạch | **FAIL có chủ đích:14test/1failure**, đúng lỗi CI; còn1booking/1outbox sau cleanup |
| Chạy lại đúng cặp class trên bản sửa cuối cùng | **PASS14/14**,0failure/error/skip; sau cleanup còn0booking/0outbox |
| Toàn backend qua `infra/test-part8.ps1`, `MAVEN_ARGS=-Dsurefire.runOrder=reversealphabetical -Dsurefire.runOrder.random.seed=31` | **PASS116/116**,0failure/error/skip, Maven `verify` BUILD SUCCESS; outbox chạy trước dashboard |
| Toàn backend qua cùng harness, `MAVEN_ARGS=-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=31` | **PASS116/116**,0failure/error/skip, BUILD SUCCESS; outbox cũng chạy trước dashboard |
| DB riêng có sẵn hai booking ngoài fixture: chạy riêng method pagination | **PASS1/1** với5booking lúc kiểm tra; count/MD5 của hai hàng ngoài fixture trước/sau giữ nguyên (`2\|190c6c091968da11d5726b04759ad442`). Setup outbox **4/4**; DB được dọn sau xác minh |
| `npm.cmd test --prefix tools/contracts` | **PASS58case**,16operation OpenAPI |
| `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1` | **PASS66HTTP response** đúng schema; dashboard DOM/API thật có25booking, hai trang20/5, overlap0, DOM khớp API; GA/inventory/history/outbox/Kafka phát2vé, SQL invariants, production auth guard và log check đạt |
| `mvnw.cmd -B -ntp cyclonedx:makeAggregateBom`, OSV trên `backend/target/bom.json` | **PASS** SBOM;77package, không phát hiện vấn đề |
| `git diff --check` | **PASS** |

Full suite dùng cùng phiên bản stack với CI: Temurin25.0.5+8, PostgreSQL17.11, Kafka4.2.2, UTC. Harness tạo hai DB UUID riêng cho PostgreSQL/Kafka test và broker/project/volume Kafka riêng, rồi dọn đúng tài nguyên đó. Không ghi/xóa dữ liệu demo, không sửa `.env`, không commit/push. Log ở `backend/target/ci31-before.log`, `ci31-focused-final.log`, `ci31-full-reverse-final.log`, `ci31-full-random.log`, `ci31-unrelated-fixtures.log`, `ci31-admin-live.log`, `ci31-contracts.log`, `ci31-sbom.log`, `ci31-osv.log`; các file target không đưa vào Git. JSON evidence giữ tóm tắt suite trước khi Surefire có thể bị lần chạy tiếp ghi đè.

### Tự kiểm tra trên Windows

Từ thư mục gốc, Docker Desktop đang chạy và `.env` local đã cấu hình:

```powershell
$env:MAVEN_ARGS='-Dsurefire.runOrder=reversealphabetical'
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1
$env:MAVEN_ARGS='-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=31'
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1
Remove-Item Env:MAVEN_ARGS
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1
```

Mỗi lượt full suite mong đợi116test,0failure/error/skip và BUILD SUCCESS; script UI mong đợi pagination20/5, overlap0 và PASS cuối. Có thể chạy nhanh cặp hồi quy bằng `infra/test-part8.ps1 -Tests 'PostgresOutboxTest,PostgresDashboardSecurityTest'` với `MAVEN_ARGS` thứ tự đảo như trên.

### Giới hạn và bước tiếp theo

GitHub Actions của bản sửa chưa commit/push chưa được chạy; run#31 trên commit cũ vẫn đỏ. Đã kiểm chứng tương đương suite/database/broker/UTC của CI trên Windows, chưa chạy lại runner Linux cho bản sửa này. UI kiểm tra bằng jsdom/HTTP thật; chưa visual QA Chrome/mobile và không chạy lại frontend unit/build vì không sửa frontend. Pagination được kiểm tra trên snapshot dữ liệu ổn định; không khẳng định OFFSET giữ nguyên tập trang khi có giao dịch khác ghi giữa hai request HTTP. Người dùng xem diff, tự commit/push và theo dõi CI mới. Chưa bắt đầu T8.1.

## 2026-11-13 — T8.1 Done: log cấu trúc và health

Tiếp tục từ HEAD4931d2f (bản sửa fixture CI đã commit), working tree ban đầu sạch. Đã đọc RULES/TASKS/PROGRESS/PRD/DESIGN/ARCHITECTURE, hợp đồng liên quan, ADR0007 và kiểm tra HTTP correlation, outbox/consumer, security, cấu hình Actuator và test hiện có. Correlation đã nằm trong HTTP/envelope và DB health đã có, nên chỉ bổ sung phần còn thiếu; không làm lại nghiệp vụ. Quyết định log/probe được ghi ở ARCHITECTURE trước code. [Bằng chứng T8.1](evidence/t8-1-observability-2026-11-13.json) có số liệu suite, ví dụ JSON an toàn và hash log.

### Đối chiếu acceptance criteria

| Tiêu chí T8.1 | Triển khai và bằng chứng |
|---|---|
| Correlation xuyên HTTP/outbox/consumer | Test HTTP thật đối chiếu correlationId/bookingId/message eventId trong response header, PostgreSQL envelope và log JSON ở HTTP, outbox_committed, outbox_published, audit consumer. Payment HTTP dùng correlation mới; log outbox/payment-v1 có cùng ID, booking CONFIRMED qua Kafka. HTTP replay giữ nguyên body/event và correlation bền của event gốc |
| Log có cấu trúc, không lẫn context | JSON Logstash + SLF4J key-value/MDC; UUID validate, scope/finally khôi phục thread. Test scope lồng/exception/thread dùng lại và request tiếp theo không có booking/event ID cũ. Consumer đọc ID từ envelope đã validate, không lấy raw key/payload để ghi log |
| Giữ đúng ranh giới giao dịch, log đúng kết quả | Outbox log sau COMMIT bằng TransactionSynchronization; rollback không có outbox_committed. Publisher ACK và đánh dấu DB xong mới log kết quả; consumer commit inbox/nghiệp vụ xong mới consumer_processed, duplicate có outcome riêng. Fault SQL thật:3delivery/3consumer_failed cùng ID, không có payment consumer_processed trước recovery, không lộ marker SQL |
| Health phản ánh dependency cần theo dõi | Healthy PG/Kafka: root/readiness/liveness200 UP. Dừng broker test thật: root/readiness503 DOWN, liveness200 UP; khôi phục broker thì trở lại200 UP. DB acquisition fault ở DataSource test: root/readiness503, liveness200, sau phục hồi200. Validation false/SQLException đều DOWN không có chi tiết nhạy cảm. Messaging tắt không yêu cầu broker |
| Không lộ token/payload | Token/body/header/query không đưa vào log HTTP; poison record chứa token/payment marker được retry/park nhưng marker không có trong log. Giữ assertion chống lộ demo token cả startup. Logger dump YAML bị tắt riêng; log nghiệp vụ/lỗi vẫn hoạt động. Health không có components/details hoặc exception text |

`eventId` ở log là UUID message trong outbox/envelope, khác `eventId` của sự kiện bán vé. Request chưa có booking/message không sinh ID giả. HTTP tạo booking có cả ba ID; endpoint booking khác ghi booking UUID hợp lệ từ route, correlation nối sang event ID trong outbox. Với request replay, correlation HTTP mới không ghi đè correlation event đã bền. Các stage có thể xen kẽ giữa thread; không suy thứ tự toàn hệ thống từ vị trí dòng log.

### File chính

- `operations/TraceContext.java`, `TraceLog.java`: scope metadata và log có các trường cố định; không nhận body/header/payload vào logger.
- `shared/CorrelationFilter.java`: context HTTP, log route mẫu/method/status và ID hợp lệ, luôn dọn sau request hoặc exception.
- `bookings/BookingCommandService.java`, `BookingController.java`: result nội bộ bổ sung message ID để log HTTP tạo/replay; body/status API không đổi.
- `messaging/OutboxWriter.java`, `OutboxPublisher.java`, `EventConsumer.java`, `FailureStore.java`, `KafkaConfiguration.java`: log commit/ACK/consumer/retry/park; giữ nguyên transaction, inbox, replay, event schema. Framework retry không dump record/exception ở mức mặc định.
- `operations/DatabaseHealthIndicator.java`, `MessagingHealthIndicator.java`: DB connection validation và Kafka metadata probe timeout; không trả/log exception nhạy cảm. DB probe thay default theo tên bean để giữ contributor `db`, không tạo probe DB trùng.
- `application.yml`, `logback.xml`, `logback-spring.xml`, `identity/SecurityConfiguration.java`: JSON, mức log an toàn từ bootstrap, health groups và quyền GET probe. Bootstrap trước Spring Environment dùng text/INFO; runtime chuyển JSON.
- `TraceContextTest.java`, `HealthEndpointTest.java`, `KafkaRecoveryTest.java`, `PostgresOutboxTest.java`: các assertion ID/health/privacy/rollback thực thi cùng hồi quy cũ.
- `README.md`, `ARCHITECTURE.md`, `API_CONTRACT.md`, `TEST_PLAN.md`, `TASKS.md`, `PROGRESS.md`, JSON evidence: cách dùng, semantics health ngoài16operation OpenAPI và bằng chứng. Không thay migration/schema/payload event/dependency/frontend.

### Lệnh đã chạy và kết quả thật

- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1`: **121/121**,0failure/error/skip, Maven verify/packaged JAR BUILD SUCCESS. Gồm116test nền +2TraceContext +2HealthEndpoint +1Kafka trace mới; các test cũ được bổ sung assertion vẫn giữ số lượng. PostgreSQL17.11/Kafka4.2.2 thật trong hai DB/project UUID riêng, UTC, Temurin25.0.5+8/Boot4.1.2. Log cuối `backend/target/t81-backend-verified.log`; surefire summary lưu trong JSON trước khi bị lần chạy khác ghi đè.
- **PASS** ca riêng `infra/test-part8.ps1 -Tests 'KafkaRecoveryTest#httpOutboxAndRealKafkaCarrySameIdsAndSafeJson'`: **1/1**; sau đó chạy full121 với toàn bộ assertion cuối. Log `backend/target/t81-trace.log`.
- **PASS** `npm.cmd test --prefix tools/contracts`: **58case/16operation**. Không đổi JSON response hoặc OpenAPI nghiệp vụ; health được mô tả ở API_CONTRACT. Log `backend/target/t81-contracts.log`.
- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1`: **66HTTP response** đúng contract; admin/customer DOM dùng API/PG/Kafka thật, pagination25→20/5, overlap0; tạo/sửa catalog, GA2, capacity conflict/price snapshot/history, outbox/2vé, SQL0oversell/0vé sai trạng thái và9migration không đổi. Packaged prod+mock từ chối startup; scan application log không có demo token. Log `backend/target/t81-live-admin.log`.
- **PASS** `git diff --check`; JSON evidence parse/đối chiếu summary đạt. Không chạy lại frontend unit/build vì không sửa frontend; DOM/API integration đã chạy trên JAR mới.

Lượt đầu chưa chạy được tích hợp vì Docker engine đang tắt; đã khởi động Docker Desktop và chạy lại. Lượt full đầu phát hiện fixture test mới gửi thừa `mode`, API trả400 đúng contract; sửa fixture, không nới validation. Lượt sau phát hiện logger YAML DEBUG trước cấu hình Boot làm lộ token demo trong test startup; đã chặn đúng logger dump cấu hình và đặt bootstrap INFO, giữ nguyên negative assertion, chạy lại full121 và packaged live. JSON encoder của Boot cần Spring Environment nên chỉ khởi tạo sau bootstrap. Các lượt lỗi không được tính PASS; artifact cuối dùng log `t81-backend-verified.log`.

### Tự kiểm tra Windows và giới hạn

Từ thư mục gốc, Docker Desktop đã chạy và `.env` local có sẵn:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8.ps1
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1

# Khi backend local đã chạy
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
```

Mong đợi full121/121, health200/UP khi dependency khỏe. Muốn thử lỗi broker, dùng test tự động ở trên: test chỉ stop/start broker UUID riêng, không dừng broker demo. Lọc JSON backend theo correlationId/bookingId/eventId và stage; sample an toàn ở evidence. Không gửi token cho health.

Chưa chạy GitHub Actions/Linux của thay đổi chưa commit/push; chưa visual QA Chrome (live UI dùng jsdom). DB fault được inject ở DataSource dùng H2 thật, chưa dừng PostgreSQL thật hoặc mô phỏng network partition PG ở T8.1. Kafka health chỉ chứng minh metadata broker truy cập được, không kiểm tra quyền ghi từng partition, publisher/consumer tiến triển, backlog/lag; các metric thuộc T8.2 chưa làm. Chưa thử cluster nhiều broker, benchmark hoặc mọi crash window. Các dòng rất sớm trước Boot có thể là text, log vận hành runtime là JSON. Không thêm Redis, dịch vụ log ngoài, payment thật hoặc metric. Không sửa dữ liệu demo/.env, không commit/push. T8.2 trở đi chưa bắt đầu.

## 2026-11-13 — T8.2 Done: metrics độc lập worker

HEAD ban đầu59e4621 (`feat: add structured tracing and health checks`), working tree sạch. Đã đọc RULES/TASKS/PROGRESS và T8.1, PRD/DESIGN/ARCHITECTURE, DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN liên quan, code expiry/publisher/consumer/recovery/security và migrationV1–V9. Micrometer có sẵn qua Actuator; HTTP timer đã được auto-instrument, nên không tạo timer trùng hoặc thêm dịch vụ. Hợp đồng tên, đơn vị, label, retry và cache ghi ở [ARCHITECTURE — T8.2](ARCHITECTURE.md#t82--hợp-đồng-metrics) trước code. [Bằng chứng T8.2](evidence/t8-2-metrics-2026-11-13.json) giữ số liệu thật, danh mục metrics và hash log.

### File thay đổi và cách đo

- `operations/OperationsMetrics.java` mới: một SQL read-only trên connection riêng, đồng hồ/snapshot PostgreSQL; gauge6state, overdue count/oldest age, outbox PENDING/PUBLISHED/FAILED và oldest unpublished, stored attempts, recovery/DLQ, snapshot availability/age. Không phụ thuộc `ExpiryScheduler`, publisher hay consumer có đang chạy. Gauge count dùng bookings/messages/attempts; mọi age dùng **seconds**, rỗng=0. Snapshot cache mặc định1s; query lỗi trảNaN/available0, không xuất số0 giả hoặc dữ liệu cũ như mới.
- `operations/MessagingMetrics.java` mới: counter retry application delivery (bỏ lần đầu) và DLQ send ACK/FAILED; process-local. Chỉ stage outbox/consumer/dlq, consumer none/payment-v1/booking-audit-v1, outcome ACKNOWLEDGED/FAILED. Không dùng ID, topic/record key, error text, user hoặc token làm label.
- `messaging/OutboxPublisher.java`: đo attempt trước send theo attempts bền, DLQ outcome theo ACK; không thay leader/transaction/retry budget. `messaging/KafkaConfiguration.java`: bật delivery-attempt header do container tạo, đếm consumer retry ở đầu listener; không đổi envelope/payload hoặc inbox.
- `application.yml`, `application-local.yml`, `application-test.yml`, `identity/SecurityConfiguration.java`: metrics Actuator read-only chỉ ADMIN local/test; ngoài local/test access mặc định NONE, dù backing bean có thể vẫn tồn tại. Chỉ thêm exposure metrics, không mở endpoint Actuator khác. HTTP latency dùng `http.server.requests` sẵn có: COUNT/TOTAL_TIME/MAX, baseUnit seconds, uri template; không thêm meter gắn URI thực.
- `PostgresMetricsTest.java` mới: scheduler lifecycle thật, fixture GA/ghế,6state qua service, snapshot commit/rollback, outbox/recovery fixtures, HTTP latency/security/tag privacy. `operations/OperationsMetricsTest.java` mới: cache TTL/failure/recovery. `KafkaRecoveryTest.java`: thêm assertion counter/gauge và ca DLQ broker outage/retry thật. `TicketingApplicationTest.java`: chỉ định prod rõ ràng, xác nhận effective metrics access NONE.
- `infra/test-metrics.ps1` mới: tái sử dụng harness DB/broker UUID hiện có; default gate24test hoặc `-FullSuite`128test. `README.md`, `ARCHITECTURE.md`, `API_CONTRACT.md`, `TEST_PLAN.md`, `TASKS.md`, `PROGRESS.md`, evidence cập nhật phạm vi/cách chạy/đơn vị. Không thay schema/migration/dependency/frontend hay API nghiệp vụ.

### Đối chiếu acceptance và số liệu thực tế

| Kiểm tra | Kết quả |
|---|---|
| Dừng Expiry Worker, deadline tự đi qua | Pause `ThreadPoolTaskScheduler`, drain task đang chạy; tạo GA+ghế thật TTL2s, không sửa deadline trong ca này. Đo overdue **0→2**, tuổi oldest **0,076971s→0,715379s** khi scheduler vẫn dừng và cả2booking vẫn PENDING_PAYMENT |
| Chạy worker lại | Resume scheduler, để tick thật xử lý; cả2booking EXPIRED, GA held_count0/ghế AVAILABLE. Metric overdue **2→0**, oldest age **0s**, khớp SQL mới. Không gọi expiry inline trong ca acceptance; không dừng worker demo |
| Booking theo6state | Tạo đủ6state qua booking/cancel/payment/expiry, mỗi state tăng1 so baseline SQL riêng. Snapshot gọi bên trong transaction có thay đổi chưa commit vẫn chỉ thấy dữ liệu đã commit; rollback không lộ state tạm; cancel commit cập nhật gauge |
| Tuổi outbox và trạng thái recovery | Fixture test riêng đặt FAILED/PUBLISHED: oldest unpublished **20,009612s→0s**, so SQL; FAILED không bị bỏ quên. Stored attempts và recovery/DLQ PENDING/EXHAUSTED/PUBLISHED/DEAD/RESOLVED khớp DB. Việc đặt trạng thái outbox/DLQ bằng SQL chỉ là fixture kiểm tra gauge, không tuyên bố đó là ACK Kafka thật |
| Retry/Kafka/DLQ thật | Send đầu lỗi không tăng retry;3consumer delivery lỗi tạo2retry; replay offset mới không tự tính retry. Poison record kiểm tra2consumer và đọc DLQ Kafka thật. Broker riêng stop/start: **2DLQ FAILED →2retry →2ACK**, durable dlq_attempts1→2 và gauge PUBLISHED khớp DB |
| API latency/security/labels | Hai GET API thật làm COUNT tăng đúng2, TOTAL_TIME **0,015161s**, MAX **0,0091159s** ở series test. Anonymous/lạ token401, customer403, admin200; prod effective access NONE. Kiểm tra tất cả custom/HTTP meter tag không có UUID/token/private query/account; URI template giữ nguyên khi ID thay đổi |
| Cache và lỗi collector | TTL1000ms bằng clock kiểm soát: không query lặp trong cửa sổ; source lỗi →NaN/available0 và snapshot.age tiếp tục tăng; phục hồi →giá trị mới/available1. Integration đặt cache0 để đối chiếu SQL tức thời; cấu hình runtime mặc định vẫn1000ms |

Các số trên là snapshot của lượt chạy cuối, không là assertion cố định cho database demo. Assertions dùng fixture mới, delta/baseline và SQL hiện trạng. `eventId/bookingId/correlationId` tiếp tục nằm trong log T8.1 khi cần truy vết, không chuyển thành label metrics.

### Lệnh đã chạy

- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-metrics.ps1 -FullSuite`: **128/128**,0failure/error/skip, Maven verify/package BUILD SUCCESS. Gồm121test nền +5PostgresMetrics +1cache +1Kafka DLQ mới; regression GA/ghế/payment/expiry/Kafka/log/health giữ xanh. PostgreSQL17.11/Kafka4.2.2 thật, Temurin25.0.5+8/Boot4.1.2, UTC; DB/project UUID được dọn đúng tài nguyên test. Log cuối `backend/target/t82-backend-pass.log`.
- **PASS** `mvnw.cmd -B -ntp '-Dtest=TicketingApplicationTest,OperationsMetricsTest' test`: **2/2** trước full suite cuối; prod access NONE và cache. Log `backend/target/t82-profile-cache.log`. Lệnh default gate24 của script được cung cấp để chạy lại; lần nghiệm thu cuối chạy `-FullSuite`, không báo đã chạy riêng default gate.
- **PASS** `npm.cmd test --prefix tools/contracts`: **58case/16operation**; không đổi response/schema nghiệp vụ. Log `backend/target/t82-contracts.log`.
- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1`: **66HTTP response** đúng OpenAPI; real DOM/API/PG/Kafka, dashboard25booking→20/5/overlap0, GA2/capacity/price/history/outbox/phát2vé; SQL0oversell/0vé sai state và9migration không đổi; packaged production mock guard và log scan đạt. Log `backend/target/t82-live-admin.log`.
- **PASS** `git diff --check`; evidence parse và tổng JUnit128 được đối chiếu. Raw report `backend/target/part82-metrics-evidence.json`/`part7-kafka-evidence.json` được tổng hợp vào evidence trước khi Surefire có thể bị ghi đè; target/log không đưa vào Git.

Lỗi đã phát hiện và sửa: test mới trùng import Timer và hiểu nhầm registry AutoCloseable; collector JdbcTemplate tham gia transaction thread gọi, đã thay bằng direct read-only connection và giữ assertion rollback; kiểm tra bean backing không tương đương quyền truy cập Actuator; harness nạp profile local nên ca nonlocal phải chọn prod rõ ràng. Các lượt FAIL không tính nghiệm thu; kết quả cuối128/128 kể trên.

### Cách tự thử và giới hạn

Từ thư mục gốc, Docker Desktop đang chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-metrics.ps1 -FullSuite
npm.cmd test --prefix tools/contracts
# Sau khi backend local chạy; admin token là fixture công khai đã chốt
$adminHeaders = @{ Authorization = 'Bearer demo-admin-token' }
Invoke-RestMethod http://127.0.0.1:8080/actuator/metrics/ticketing.holds.overdue -Headers $adminHeaders
Invoke-RestMethod http://127.0.0.1:8080/actuator/metrics/ticketing.holds.overdue.oldest.age -Headers $adminHeaders
Invoke-RestMethod http://127.0.0.1:8080/actuator/metrics/ticketing.outbox.unpublished.oldest.age -Headers $adminHeaders
Invoke-RestMethod http://127.0.0.1:8080/actuator/metrics/http.server.requests -Headers $adminHeaders
```

Metric count/age phản ánh dữ liệu hiện tại, không hứa giá trị2/0 trên demo; thử pause/resume dùng script để không ảnh hưởng demo. Retry ứng dụng không bao gồm network retry nội bộ Kafka producer. Counter/timer reset khi JVM restart, không là số event unique bền; cache tối đa1s mặc định và mỗi request metrics có thể ở snapshot khác nhau. DB auth lỗi có thể chặn metrics HTTP, dùng health để theo dõi dependency; gauge NaN/availability không thay health. Chưa benchmark aggregate SQL ở dữ liệu lớn, pool saturation, nhiều replica hoặc cluster Kafka; chưa có Prometheus/Grafana/alerting/consumer lag. Chưa GitHub Actions/Linux/visual Chrome; không chạy lại frontend unit/build vì không sửa frontend, nhưng live jsdom/API đã chạy. Không sửa `.env`, dữ liệu demo, schema hay event; chưa commit/push. T8.3 trở đi chưa thực hiện.

## 2026-11-13 — T8.3 Done: recovery view, audit và replay no-op

Tiếp tục từ HEAD480cd4c, working tree ban đầu sạch. Đọc RULES/TASKS/PROGRESS/PRD/ARCHITECTURE/DESIGN, DATA_MODEL/API_CONTRACT/EVENT_CATALOG/TEST_PLAN và ADR0007; kiểm tra ReplayService, FailureStore/DLQ/inbox, audit replay_requests, publisher và dashboard có sẵn. Quyết định bổ sung read-view trước code tại ARCHITECTURE/API_CONTRACT, không viết lại cơ chế T6.4. [Bằng chứng T8.3](evidence/t8-3-recovery-2026-11-13.json) có hash snapshot nghiệp vụ, số liệu backend/UI/SQL và hash log.

### File và phạm vi thay đổi

- `operations/RecoveryQuery.java` mới: list recovery OPEN/RESOLVED/ALL phân trang và detail event trong REPEATABLE_READ; publisher status, consumer receipt, lỗi/DLQ an toàn, audit/remainingReplays. Không trả raw_event/key/topic/payload hoặc arbitrary exception text. Detail tối đa100lỗi gần nhất; audit cũ tối đa3được trả đủ.
- `operations/OperationsController.java`: thêm GET `/api/admin/operations/recovery` và GET `/api/admin/operations/events/{id}`. `ReplayService.java` chỉ đổi visibility helper authorize để query tái sử dụng cùng guard; phương thức POST replay/transaction/budget/idempotency giữ nguyên. Không có mutation endpoint mới, không sửa consumer/inbox, không tạo force/reset/purge.
- `frontend/src/app/RecoveryView.ts`, `replayAttempt.ts`: list/filter/pagination/detail gần đầu/focus/đóng, lý do Unicode, pending request giữ trong session, không tự POST khi mount/refresh và chặn double click. Audit và từng consumer/DLQ có trạng thái riêng;202 không bị diễn giải thành xử lý xong. Receipt audit không đồng nghĩa payment hoàn tất; consumer chưa ghi nhận vẫn có thể xử lý lần đầu.
- `frontend/src/app/AdminDashboard.ts`, `frontend/src/api/admin.ts`, `styles.css`: tab Khôi phục, nút Chi tiết event trong từng hàng Outbox, adapter gọi đúng2GET và POST replay cũ; không gửi user/role/force. Khi budget0 khóa request mới, nhưng giữ khả năng gửi lại đúng pending request vì server dedup trước kiểm tra budget.
- `RecoveryView.test.ts`, `admin.test.ts`, `AdminDashboard.test.ts`, `KafkaRecoveryTest.java`: unit/integration/RBAC/privacy/pagination/replay no-op. `frontend/tests/live-recovery.ts`, `frontend/package.json`, `infra/test-recovery.ps1`, `infra/test-part8-ui.ps1`: live fault mất phản hồi thật, đối chiếu SQL trên DB test; CI thêm chạy live recovery cho lần push sau.
- `docs/contracts/openapi.json`, `tools/contracts/verify.mjs`, `verify-live.mjs`:18operation, strict schemas recovery/audit/receipt và validation/privacy cases. `RULES`, migrationV1–V9, schema/envelope event, seed và dependency không đổi. README/DESIGN/ARCHITECTURE/API_CONTRACT/TEST_PLAN/TASKS/PROGRESS/evidence cập nhật phạm vi và cách thử; chưa làm T8.4.

### Acceptance và bằng chứng thực

| Trường hợp | Kết quả |
|---|---|
| ADMIN hợp lệ / không có quyền | Hai GET mới: admin200, thiếu/lạ token401, customer403. Guard service prod403, POST replay vẫn theo T6.4. Không nhận identity/role từ client |
| Event không tồn tại / validation | GET và POST404; query sai400, status rỗng400, payload force400; requestId cũ khác reason409 |
| Event đã xử lý / replay trùng | PaymentResultRecorded và BookingConfirmed được republish qua Kafka thật; đợi log DUPLICATE từ consumer. Hash snapshot nghiệp vụ trước/sau đều **b59f9e6bece60463fe8bda16a211a2a4**. Bao gồm booking/items/tickets/history/payments/inventory/business-event fields/inbox/projection event rows; vé2, history2, businessEvents3 không đổi |
| Audit và budget | Cùng request/reason trả202 không append audit hoặc reset attempts/status sau PUBLISHED.3request khác nhau/event tạo3audit; request thứ4 bị409, request cũ vẫn202 khi budget0. Actor/time/reason/requestId từ audit bền hiển thị đúng; không xóa inbox |
| Danh sách lỗi | Fixture23row giữa tổng39row của full Kafka suite; size1 lấy đủ dữ liệu,0trùng ID và ID DESC khi timestamp trùng. Không giả định DB chỉ có fixture của mình. Private marker trong raw/error/key/topic không có trong response; record không có outbox không được replay |
| UI/mất phản hồi | Fetch wrapper làm mất phản hồi **sau khi server thật trả202**. Người dùng gửi lại cùng request từ UI:2HTTP POST nhưng1requestId/1audit; vé2→2, history2→2, receipt timestamps không đổi, remaining2/3. SQL cuối sold2/businessEvents3/paymentInbox1. Không dùng dữ liệu giả cho kết quả nghiệp vụ |
| Frontend limits | Form focus/đóng, audit text escape, stale response không đè selection; whitespace/501Unicode bị chặn,500Unicode gửi được. Double click chỉ1POST; pending qua remount không tự gửi, gửi lại được đúng request khi server budget0; hiển thị403/404/409/correlation rõ |

Thông báo202 chỉ chứng minh yêu cầu được chấp nhận (có thể là kết quả của request cũ), không chứng minh lần gửi/consumer mới đã xong. Receipt chỉ ghi lần xử lý đầu của từng consumer; chứng minh no-op của lần phát lại lấy từ test Kafka đợi log DUPLICATE và snapshot SQL, không suy từ receipt cũ hoặc PUBLISHED đơn thuần.

### Lệnh đã chạy và kết quả

- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live`: **130/130 backend**,0failure/error/skip, verify/package BUILD SUCCESS; sau đó chạy gate HTTP/UI/SQL thật. Gồm128test nền +2Kafka recovery mới; giữ hồi quy GA/ghế/payment/expiry/metrics/log/health. Log `backend/target/t83-full-live.log`.
- **PASS** ca riêng2test recovery qua `infra/test-part8.ps1 -Tests 'KafkaRecoveryTest#recoveryHttpReplaysProcessedEventsWithoutAnySecondBusinessEffect+recoveryReadViewIsAuthorizedPaginatedAndNeverLeaksRawFailureData'` sau sửa validation: **2/2**. Log `backend/target/t83-focused-fixed.log`.
- **PASS** `npm.cmd test --prefix frontend`: **54/54**,0fail/cancel/skip; `npm.cmd run build --prefix frontend`: TypeScript/Vite đạt. Log cuối `backend/target/t83-frontend-tests-final.log`, `t83-frontend-build-final.log`.
- **PASS** `npm.cmd test --prefix tools/contracts`: **69positive/negative case**, **18operation**. Log `backend/target/t83-contracts.log`.
- **PASS** `powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-part8-ui.ps1` lần cuối sau làm rõ wording consumer receipt: **74HTTP response** đúng schema, admin/customer DOM/API/Kafka thật và live recovery lost-response/same-request. SQL recovery `2|2|2|3|1|1` = tickets/sold/history/businessEvents/audit/paymentInbox; production auth guard/localhost/log scan và9migration không đổi đạt. Log `backend/target/t83-live-final.log`; JAR backend cùng bản đã full130, frontend DOM dùng source mới nhất.
- **PASS** `git diff --check`; evidence JSON parse/tổng JUnit130/hash log đối chiếu đúng. CI workflow thêm bước live recovery nhưng **chưa chạy GitHub Actions của thay đổi chưa commit**.

Lượt focused đầu phát hiện `@RequestParam(defaultValue="OPEN")` coi `status=` rỗng là mặc định, trái contract. Đã tách omitted/null khỏi explicit empty, giữ assertion400 rồi chạy lại focused/full/live; không đổi expected để che lỗi. Rà UI cũng tách ý nghĩa từng consumer receipt để không diễn giải audit receipt là payment xong.

### Tự thử Windows/Chrome và giới hạn

```powershell
# Từ thư mục gốc, Docker Desktop đang chạy
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
# Muốn mở bản hiện tại với DB demo giữ nguyên
powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -RestartExisting
npm.cmd run dev --prefix frontend
```

Chrome http://127.0.0.1:5173 → Quản trị demo → Khôi phục. Filter/trang, Xem event, nhập reason và Gửi yêu cầu; xem requestId/audit/ngân sách rồi Làm mới. Với event đã xử lý, Outbox → Chi tiết event. Khi không có lỗi, empty-state là đúng; không cần tạo/sửa lỗi trong demo chỉ để có dữ liệu. Sau lỗi mạng dùng Gửi lại cùng yêu cầu, không tạo key mới. Không đưa token/payment payload vào reason. Record không có outbox chỉ điều tra, không có hành động force.

Backend gate sở hữu DB và broker UUID riêng. Live gate dùng DB/topic/group UUID riêng trên broker local, không dừng broker demo hoặc ghi DB demo; tài nguyên DB/process test được dọn. Chưa visual QA Chrome/mobile; UI được kiểm bằng jsdom với HTTP thật. Chưa GitHub Actions/Linux, tải lớn/multi-broker hoặc mọi crash window. Lý do kỹ thuật cố ý rút gọn theo class/trạng thái T6.4, không phân tích chính xác root SQL exception; detail chỉ100failure gần nhất, audit budget3đầy đủ. Pending persistence dựa sessionStorage; khi không dùng được storage, giữ request trong bộ nhớ khi view còn sống. Không thêm dependency/migration/envelope, không sửa `.env`, không commit/push. T8.4 trở đi chưa làm.

### 2026-11-14 — T8.4: README tiếng Việt, chạy thật từ clone sạch

**Phạm vi:** chỉ T8.4, không triển khai T9.1. Bắt đầu từ HEAD `1e157a9354f9efcb7158503e3923467720a1c535`, Git sạch; đã đọc RULES/TASKS/PROGRESS, README gốc, infra/README và các launcher/test/Compose, đối chiếu kiến trúc/ADR0001 và code hiện hành. Không đổi schema, migration, API, event, luồng GA/ghế/payment/expiry/Kafka/replay hoặc dependency. Không commit/push.

**Lý do sửa:** README cũ tích lũy nhiều hướng dẫn giai đoạn trước, có mô tả recovery UI chưa làm, số operation/schema cũ và smoke ghi fixture lẫn với kiểm tra chỉ đọc. Launcher/verify cố định backend8080; chạy npm dev trực tiếp không nạp VITE_API_BASE_URL từ .env gốc. Các điểm này gây nhầm khi chạy sạch hoặc chạy song song demo. README mới lấy trạng thái T8.3 làm chuẩn; giữ số liệu cũ trong nhật ký lịch sử, không dùng làm kết quả mới.

**File:** README.md viết lại tiếng Việt: mục đích, kiến trúc mô tả/bảng, GA/ghế/payment/expiry/phục hồi, stack/yêu cầu, .env, terminal start/stop, URL/health/metrics/OpenAPI, demo persona/token fixture, thử Chrome, test và troubleshooting. infra/README.md là danh mục script hiện hành và cảnh báo tác động dữ liệu. `.env.example` thêm SERVER_PORT=8080; start-local.ps1/verify-local.ps1 nhận -Port/SERVER_PORT, mặc định giữ8080. start-frontend.ps1 mới nạp .env gốc, chạy Vite đã cài, strict5173. stop-local.ps1 mới chỉ dừng listener/command line đúng workspace, kiểm tra toàn bộ trước khi dừng; -StopServices dùng compose stop giữ volume. Cập nhật TASKS/PROGRESS và [bằng chứng máy đọc](evidence/t8-4-readme-2026-11-14.json).

**Môi trường sạch đã dùng:** git clone thật từ GitHub vào `C:\Users\tinht\AppData\Local\Temp\ticketing-readme-ca27534dd334`, remote HEAD khớp commit hiện tại. Trước chuẩn bị không có .env, backend/target hoặc node_modules frontend/contracts, không có volume của project kiểm tra. Chỉ overlay7file README/infra/config T8.4 chưa commit; không copy build artifact, dependency, .env hay dữ liệu demo. Project `ticketing-readme-ca27534dd334`, database `ticketing_readme`, PostgreSQL15432, Kafka19092, backend18080, ViteIPv6::1:5173 (Chrome origin localhost:5173, CORS hiện có cho phép). Database có0bảng ticketing trước startup; container/volume hoàn toàn tách project demo. Các khối tạo .env, đổi project/cổng và lệnh chạy đúng trong README đã được thực thi ở clone này.

Phiên bản thực tế: Temurin java/javac25.0.5+8, Node26.13.0/npm11.20.0, Docker Client+Server29.9.1, Compose5.6.0, Git2.55.0.windows.3. Wrapper3.9.16, Boot4.1.2, imagePG17.11/Kafka4.2.2 theo baseline đã chốt.

| Lệnh / bước chạy tại clone sạch | Kết quả thực tế |
|---|---|
| git clone; powershell -NoProfile -ExecutionPolicy Bypass -File infra/local-env.ps1 | PASS clone đúng HEAD; tạo .env/password ngẫu nhiên không in password; chạy local-env lần2 giữ nguyên hash file |
| npm.cmd ci --prefix frontend; npm.cmd ci --prefix tools/contracts | PASS từ node_modules chưa tồn tại; lần lượt55/16package, audit0vulnerability |
| npm.cmd test --prefix frontend | PASS54/54,0fail/skip |
| npm.cmd run build --prefix frontend | PASS TypeScript/Vite |
| npm.cmd test --prefix tools/contracts | PASS OpenAPI3.1/18operation/69case positive-negative |
| powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live | PASS toàn backend130/130,0fail/error/skip; Maven BUILD SUCCESS;74response HTTP hợp đồng; admin và recovery UI thật/SQL/prod auth guard. Lần gate cuối capture stdout/stderr riêng, exit0 |
| powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-local.ps1 -Port 18080 | PASS startup từ database trống;9migration khác version/đều success,18bảng,2event,4ghế,GA capacity100,0booking |
| powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1 -Port 18080; chạy lại không -Port | PASS HTTP200/UP,V1 đúng1lần,schema ticketing; xác nhận cả fallback SERVER_PORT từ .env |
| GET /api/events, detail và availability; health/root/readiness/liveness | PASS đúng2event GA/SEATED; cả health200/UP |
| powershell -NoProfile -ExecutionPolicy Bypass -File infra/start-frontend.ps1 -BindAddress ::1 | PASS localhost:5173 và IPv6 trả200; main.ts đã transform chứa API18080; CORS localhost:5173→204 |
| compose ps/logs; dừng rồi bật riêng kafka, sau đó postgres của project kiểm tra | PASS mỗi outage root/readiness503,liveness200; up -d --wait postgres kafka phục hồi readiness200, giữ volume |
| start-local.ps1 -Port 18080 -RestartExisting | PASS thay đúng JVM; checksum toàn bộ9row Flyway history không đổi, seed2event/4ghế/GA100/0booking giữ nguyên |
| stop-local.ps1 -Port 18080 -FrontendAddress ::1 -StopServices | PASS dừng đúng JVM/Vite/2container kiểm tra, port đóng, giữ cả2volume; đã chạy lại stop sau gate cuối |
| Start backend/frontend và stop từ clone hướng cổng demo8080/5173 | PASS từ chối tiến trình khác, exit1 đúng mong đợi; không dừng demo |
| docker version với endpoint named-pipe không tồn tại | PASS trả lỗi kết nối/exit1 như khi daemon unavailable; daemon Docker Desktop thật không bị tắt, docker version sau đó vẫn có Server29.9.1 |
| Parser tất cả infra/*.ps1; liên kết tương đối README/infra; git diff --check; quét log gate/start/restart | PASS không lỗi cú pháp/link/whitespace; không có password đã sinh hoặc bearer fixture trong log được capture |

**Không nhầm launcher với full suite:** Maven trong start-local phát hiện130test nhưng chạy9/skip121 do không có biến IT. Đây chỉ là kiểm tra build khởi động. Bằng chứng130/130,0skip nằm ở gate test-recovery riêng chạy PostgreSQL/Kafka thật (đã chạy2lượt trong clone; không cộng thành260test khác nhau). Gate HTTP/admin/recovery cũng chạy lại đạt; replay cùng request2HTTP →1audit, vé2→2, history2→2, payment inbox1, business outbox3. Dashboard25event →20+5,0IDtrùng, DOM khớp API. Số liệu này ở DB test UUID, không phải assertion dựa trên trạng thái demo.

**Bảo toàn demo:** fingerprint chuẩn hóa nội dung cả18bảng trước/sau cùng SHA256 `fa37aae42c59a7fbfada8991ceed3c7321a57f9ffdf99a86f53282079c7913be`; hash .env gốc không đổi. Backend PID29744 vẫn HTTP200/UP ở127.0.0.1:8080, frontend PID3628 vẫn200 ở127.0.0.1:5173; hai container demo còn healthy. Không reset DB, không xóa volume demo. Clone kiểm tra và hai volume riêng được giữ để xem lại nhưng ứng dụng/container kiểm tra đã dừng. Harness regression chỉ xóa DB/broker/volume UUID do chính nó tạo; live topic UUID ở broker kiểm tra giữ tới retention.

Log thô/runner vận hành trong reports/t84-* (ignored), log live JVM trong TEMP, JUnit trong backend/target của clone. JSON evidence lưu command, kết quả, hash file candidate/log và giới hạn, không chứa .env/password/payload thanh toán. Lần capture gộp stderr ban đầu khiến PowerShell wrapper báo NativeCommandError cho dòng progress git clone; gate gốc đã chạy lại capture hai stream riêng và xác nhận exit0. Một lệnh audit dot-source ban đầu gặp ExecutionPolicy hiện tại; đã chạy lại bằng process-scoped Bypass đúng README, không đổi policy toàn máy.

**Nghiệm thu:** T8.4 Done — từ checkout không có cấu hình/dependency/artifact và DB/volume mới, thực thi hướng dẫn cấu hình, cài đặt, build/test, start/verify/restart/stop và phục hồi dependency thành công; không cần dữ liệu demo cũ.37/45task Done. T9.1 trở đi chưa thực hiện.

**Giới hạn:** đây là môi trường ứng dụng/dữ liệu sạch trên máy Windows hiện có, không phải máy mới hoàn toàn; cache Maven/npm/Docker image được giữ, chưa kiểm chứng cache lạnh/offline hay khác tài khoản Windows. Chưa visual QA Chrome hoặc GitHub Actions/Linux cho thay đổi chưa commit. HTTP/Vite/CORS và React/jsdom/API thật đã chạy. Chưa tắt Docker Desktop thật, chỉ mô phỏng endpoint không kết nối để không ảnh hưởng demo. Không khẳng định multi-broker/HA/mất ổ đĩa, thanh toán hoặc refund thật. CORS frontend vẫn chỉlocalhost/127.0.0.1:5173; máy không có IPv6 cần máy/VM khác trống5173 để thử song song.

Tự chạy lại từ gốc Windows (demo đang chạy không cần restart):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/verify-local.ps1
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-recovery.ps1 -Live
```

Muốn lặp nghiệm thu môi trường sạch, dùng nguyên mục “Kiểm chứng môi trường sạch, song song demo” trong README ở clone mới với project/port riêng; không áp dụng khối sửa .env lên workspace demo. Chrome demo hiện có tại http://127.0.0.1:5173; hướng dẫn Khách hàng/Quản trị/Khôi phục trong README. Không chạy smoke verify-part4…8 lên dữ liệu cần giữ chỉ để kiểm tra README.
