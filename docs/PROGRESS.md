# PROGRESS — Nhật ký tiến độ

> Cập nhật sau mỗi task hoặc quyết định ảnh hưởng phạm vi. Ngày dùng múi giờ Việt Nam.

## Trạng thái hiện tại

- **Giai đoạn:** Phần7 — Kafka và phục hồi đã hoàn thành, đối chiếu ngày 2026-11-13
- **Tiến độ:** T1.1–T6.5 Done; Kafka/PostgreSQL thật, GA/ghế/payment/expiry, inbox/replay và refund mô phỏng đã kiểm chứng
- **Task đang làm:** Chưa có
- **Task tiếp theo:** T7.1 (chưa bắt đầu, ngoài phạm vi yêu cầu này)
- **Blocker:** Không còn blocker T6. Chưa remote CI cho thay đổi này, browser visual QA, cluster nhiều broker/mất đĩa hoặc chaos dài hạn. Không commit/push.

- **Task trong TASKS:** 29/45 Done.18bảng/V1–V9,13API đã triển khai; Kafka publisher/2consumer, recovery/DLQ/replay và refund worker. Các mục Phần3–6 dưới đây giữ bằng chứng lịch sử.

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
