# TEST_PLAN — Kiểm thử và bằng chứng đồ án

## Gate T9.1 — Chạy lại bất biến từ HEAD hiện tại

`powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-invariants.ps1` tạo clone HEAD và `.env`/project/volume/cổng riêng, không nạp cấu hình `.env` workspace hoặc khởi động demo (chỉ đọc hash file để kiểm tra bảo toàn). Từ chối nếu code ứng dụng/test chưa khớp HEAD để không báo nhầm kết quả của code cũ. Chạy lại ca sẵn có; chỉ thêm test nghiệp vụ khi xác định khoảng trống. Runner mới chỉ điều phối và thu bằng chứng, không thêm scenario hoặc sửa bất biến.

| Bất biến | Ca hiện có cần chạy và đối chiếu SQL |
|---|---|
| GA không vượt capacity | PostgresBookingTest:50usecase/50HTTP capacity10; SELECT held_count, số booking/outbox và SUM(quantity); số thành công phải khớp allocation |
| Ghế không hai owner / không partial hold | PostgresSeatExpiryTest:50command tập ghế đảo thứ tự,50HTTP một ghế,2tập giao nhau một phần; COUNT(DISTINCT held_by_booking_id), booking_items, AVAILABLE/HELD; ghế của bên thua không bị giữ dở |
| Idempotency |20request cùng key GA và20danh sách ghế đảo thứ tự →1ID/1body/1outbox; đổi payload409; inventory/unique-key timeout và rollback không để booking/key/hold dở |
| Expiry nhả đúng một lần |20worker →1EXPIRED transition,1BookingExpired,0HELD; worker lặp/replay sau rebook không nhả owner mới. Ca PostgresMetricsTest dừng scheduler thật rồi resume, đối chiếu SQL overdue/GA/ghế |
| Kafka gửi/nhận lặp |KafkaRecoveryTest: ACK rồi DB mark thất bại gửi lại cùng ID,10payment duplicate/consumer restart/5BookingConfirmed replay; inbox/payment history/ticket không tăng; snapshot nghiệp vụ trước/sau HTTP replay giống nhau |
| Rollback |Lỗi thật qua PostgreSQL trigger ở GA/ghế/expiry, ledger/ticket/history/compensation và inbox consumer; no partial writes. Consumer3delivery lỗi giữ0ticket/0inbox, sau replay có đúng vé/1inbox |
| Restart |Kafka broker/consumer restart từ suite; test-part5-ui restart JVM cùngDB và Flyway không chạy lại; test-part6-ui -Kafka restart kết quả payment SCHEDULED, ledger/intent giữ nguyên, late success không phát vé và compensation không trùng |

Gate chạy full backend bằng `test-recovery.ps1 -Live`, sau đó `test-part4-ui.ps1`, `test-part5-ui.ps1`, `test-part6-ui.ps1 -Kafka`; frontend/build và contract cũng chạy lại. Ghi JUnit từng ca và các artifact part4/5/6/7, log SQL/live trước khi harness dọn DB UUID. Chỉ PASS khi không fail/error/skip, các subprocess exit0 và tài nguyên demo được giữ nguyên. Kết quả tại `reports/t9-1/<runId>/summary.json`; số thực/bằng chứng công bố ở PROGRESS, không lấy số lịch sử dưới đây làm kết quả mới.

Các burst50request là hồi quy đã có, không thay nghiệm thu máy/latency/p95/TPS của T9.2. Fault/restart trong gate cũng là chạy lại TEST_PLAN, không tự đánh dấu T9.3. Không khẳng định đã kiểm chứng mất mạng chính xác tại COMMIT, crash mọi cửa sổ, cluster nhiều broker hoặc tải dài hạn.

## Gate T8.3 — Recovery view và replay có kiểm soát

- Tái sử dụng POST replay T6.4, không xóa inbox hoặc tạo force/reset. `KafkaRecoveryTest` phát lại PaymentResultRecorded/BookingConfirmed đã xử lý qua HTTP/Kafka thật. Đợi log DUPLICATE từ consumer, so hash toàn snapshot nghiệp vụ trước/sau: booking, items, tickets, history, payments, inventory, event business và inbox/projection rows không đổi. Cùng requestId/reason không append audit/reset attempts;3request mới hết budget, request thứ4 bị409, request cũ vẫn202, payload có force bị400.
- Hai GET recovery: anonymous/lạ token401, customer403, admin200; service prod403; UUID không tồn tại404, query sai400. Fixture riêng gồm23row trong database còn dữ liệu test khác; phân trang size1 thu đủ ID, không trùng, timestamp trùng vẫn ID DESC, không giả định tổng cố định. Raw record/key/topic/payload/error marker không xuất hiện trong response; record không có outbox không replay được.
- Frontend: form mở gần đầu/focus và đóng; list/filter/pagination/loading/error; audit reason hiển thị text an toàn;500Unicode point/whitespace, lỗi quyền/404/budget; double click chỉ gửi1POST. Mất phản hồi giữ requestId/reason qua remount, không tự POST khi mount/refresh; retry đúng request ngay cả khi ngân sách mới bằng0. Consumer receipt được hiển thị theo từng consumer, không suy receipt audit là payment xong.
- Live UI với API/PG/Kafka thật: làm mất phản hồi sau202, gửi lại cùng request từ giao diện, audit vẫn1, vé2 và history2 không đổi, receipt timestamps giữ nguyên. SQL cuối kiểm tra sold2/businessEvents3/paymentInbox1. Regression admin/customer/health/log/metrics vẫn chạy.
- Contract OpenAPI18operation, thêm RecoveryPage/RecoveryEvent/Audit/Failure/Receipt strict và negative payload privacy/budget. Không sửa schema database/event hoặc data seed; ghi bằng chứng và giới hạn chẩn đoán/100failure gần nhất vào PROGRESS.

## Gate T8.2 — Metrics

- `PostgresMetricsTest`: pause lifecycle TaskScheduler thật, drain task đang chạy, tạo booking GA/ghế qua service thật, để deadline đi qua theo đồng hồ PG; metric overdue và oldest age phải tăng khi scheduler vẫn dừng. Resume scheduler, chờ EXPIRED/release thật và đối chiếu metric với SQL hiện trạng; không gọi expiry inline trong ca acceptance này.
- Gauge state: tạo đủ6state bằng service booking/cancel/payment/expiry và so delta với baseline SQL; snapshot phải dùng connection riêng, không đọc thay đổi đang nằm trong transaction/rollback của thread gọi. Tuổi outbox bao gồm FAILED/PENDING và thay đổi sau PUBLISHED; tuổi rỗng=0. Gauge recovery/DLQ đi qua PENDING/EXHAUSTED/PUBLISHED/RESOLVED.
- `KafkaRecoveryTest`: delivery lần đầu không tăng retry;3delivery lỗi payment tạo2retry, replay offset mới không tự tăng retry. Poison record được retry ở cả2consumer, recovery DB và Kafka DLQ thật được đối chiếu. Dừng broker riêng: DLQ send thất bại, restart/send lại làm retry/ACK counter tăng và gauge chuyển trạng thái.
- Timer `http.server.requests`: gửi request API thật, kiểm tra COUNT tăng đúng số request tạo, TOTAL_TIME/MAX>0 và đơn vị seconds; uri là route mẫu, không có UUID/token/query/account trong tag. Metrics chỉ ADMIN local/test; anonymous401/customer403/admin200, mặc định ngoài local/test access=NONE dù bean backing có thể vẫn tồn tại.
- `OperationsMetricsTest`: TTL cache không query liên tiếp trong cửa sổ; source lỗi làm gauge NaN/available0, age vẫn phản ánh snapshot thành công trước đó; phục hồi cập nhật số mới/available1. Không dùng zero giả khi query lỗi.
- Chạy `infra/test-metrics.ps1` hoặc `-FullSuite`, contract và live HTTP gate liên quan; ghi số liệu đo thật, không cố định database demo. Giới hạn process-local counters, cache và SQL scan được ghi trong ARCHITECTURE/PROGRESS.

## Gate T8.1 — Log cấu trúc và health

- `TraceContextTest`: thread dùng lại sau exception, scope lồng khôi phục MDC; HTTP correlation hợp lệ/không hợp lệ, route mẫu, không ghi token/body/query; request tiếp theo không có booking/event ID cũ.
- `PostgresOutboxTest`: rollback sau append không được log `outbox_committed`; assertion commit/rollback DB hiện có giữ nguyên.
- `KafkaRecoveryTest`: HTTP tạo booking, đối chiếu correlationId/bookingId/eventId giữa log JSON HTTP, outbox sau commit, publisher và audit consumer trên PostgreSQL/Kafka thật. Kiểm tra payment HTTP → outbox → payment-v1, replay request giữ event/correlation bền ban đầu, duplicate có outcome riêng. Fault SQL sau append: ba log consumer_failed có đúng ID, chưa có payment consumer_processed khi rollback; không lộ marker SQL. Gửi poison record có marker token/payment và kiểm tra sau retry/park marker không xuất hiện trong log, kể cả token demo từ startup YAML.
- Kafka stop/start trên broker test riêng: health tổng hợp và readiness đổi200 UP →503 DOWN →200 UP; liveness vẫn200 UP. Payload health không có components/details.
- `HealthEndpointTest`: lỗi acquisition/validation DB trả DOWN không kèm exception nhạy cảm; HTTP readiness/tổng hợp503, liveness200, hồi phục200; messaging tắt không cần broker. Fault acquisition được inject ở DataSource test với H2 thật; không dừng PostgreSQL demo.
- Chạy suite PostgreSQL/Kafka đầy đủ để giữ hồi quy GA/ghế/payment/expiry/replay và kiểm tra hợp đồng API. Không nghiệm thu metric/backlog hoặc độ ổn định nhiều node ở T8.1.

## Gate T7 — Auth, UI và HTTP/log negative tests

- DemoAuthConfigurationTest: mock bật được trong local/test; profile rỗng/prod/trộn bị từ chối, prod mặc định tắt. PostgresDashboardSecurityTest kiểm tra token lạ401/customer→admin403, giả userId/role không cấp quyền, owner sai404, admin service cũng chặn customer.
- API private: DRAFT/CLOSED/inactive chỉ admin xem, inventory không có owner, booking filter6state/pagination/validation; capacity conflict không ghi giá hoặc thay snapshot booking.
- Hồi quy CI#31: test pagination admin luôn có booking nền thuộc sự kiện khác. Xác nhận đúng hai ID của fixture, status filter, toàn bộ trang size1, không trùng ID và trang cuối rỗng. Thứ tự mong đợi tính trong Java từ snapshot DB không ORDER BY: created_at DESC rồi UUID DESC; hai booking mục tiêu trùng timestamp, booking nền cũ hơn. Không giả định toàn database chỉ có2booking. PostgresOutboxTest xóa đúng UUID fixture sau khi đã kiểm tra commit, không để dữ liệu ảnh hưởng class sau. Chạy kiểm tra với thứ tự đảo và random seed31; không cố định thứ tự trong CI để che lỗi.
- HTTP policy: giới hạn8192/131072byte, UTF-8 sai/multibyte/depth/key lặp/trailing JSON/content-type/encoding; field giả/overflow bị chặn, CORS chỉ5173; lỗi chuẩn/correlationId, kể cả lỗi auth database. Inject lỗi SQL chứa marker và kiểm tra marker không xuất hiện ở response hoặc log. Replay reason500Unicode code point.
- Customer DOM: loading/error/empty/history/detail, snapshot cũ, EXPIRED/REFUND_REQUIRED, retry cùng command và không hiển thị vé cho booking mất hold. Admin DOM: pagination có delay, bấm lại tab, đổi tab, mở lại tồn kho, create/edit/unknown outcome/duplicate click/XSS/persona không mất key.
- `infra/test-part8-ui.ps1`: React/jsdom + HTTP/PostgreSQL/Kafka thật, so hàng trang2 với response thật và không trùng ID; DRAFT→PUBLISHED, GA/ghế, held/capacity conflict/giá snapshot, booking history/filter/outbox, confirm2vé và SQL. Packaged prod+mock phải fail startup, server chỉ bind127.0.0.1. Không coi đây là Chrome visual QA.
- `infra/test-part8.ps1` chạy lại toàn backend cũ và mới với tài nguyên test riêng; `verify-part8.ps1` kiểm tra demo Windows qua API, tùy chọn thêm DRAFT đủ21 để thử phân trang trong Chrome. Chưa làm T8 hoặc tự tạo T7.5.

## Gate T6 — Kafka/PostgreSQL thật

`infra/test-part7.ps1` chạy15ca KafkaRecoveryTest cộng88ca backend cũ trong DB riêng: migration sạch/rerun, schema/inbox unique/hash, async paymentversion2, duplicate/restart consumer, stop/startbroker, version1→2→3, ACK thành công nhưngDBmarkfail, inbox/businessrollback+đo3delivery bằng sequence ngoài rollback, gap3trước1/2, poisonrecord+đọcKafkaDLQ thật, replay401/403/profile/audit/limit3, paymentđúnghạn khiKafkachưađến, late success/refund12caller concurrent/historyrollback/bounded3failure, duplicate failure/expiry không nhả holdkhác, sessionleader chống publisherthứ2.

`infra/test-part6-ui.ps1 -Kafka`: 50HTTP responseOpenAPI, checkout DOM API thật GA3+seats2/5véunique, failure/cancel/late success/expiry, JVMrestart scheduledresult, compensationCOMPLETED và SQL0oversell/0vésaiowner/0nghĩavụtrùng/V1–V9mộtlần. Frontend26ca; staticcontract49payload. Không coi jsdom là browser visual QA; YAMLlint không chứng minh remote CI xanh. Các mục Phần4–6 dưới đây giữ bằng chứng lịch sử.

> Kế hoạch toàn dự án và kết quả đến Phần6. Payment/ghế/expiry/vé đã có bằng chứng dưới đây;Kafka/inbox/refund worker vẫn là kế hoạch. Kết quả thực tại PROGRESS/evidence/part6-2026-11-12.json. Các gate Phần4/5 giữ số liệu lịch sử của chúng.

## Nguyên tắc

Phần6:88test backend (87PostgreSQL+1H2),30test mới PostgresPaymentTest;26frontend (7client/storage+12App+7Checkout),buildbackend/frontend,44ca contract/40response HTTP live. Không mockDB hoặc HTTPresponse trong ca live. Scheduler kiểm soát bằng test properties/CLI; đầy đủ suite chạy với envEXPIRY_ENABLED=true mà không chen vào fixture. Các artifacttarget được tạo lại,không commit dữ liệu test.

Phần5: infra/test-part5.ps1 chạy58test/0fail/0error/0skip (57PostgreSQL+1H2); PostgresSeatExpiryTest có23test mới. Frontend16test (5client/storage +11SSR/DOM), buildTypeScript/Vite; contract32ca/22response HTTP live. Test-part4-ui kiểm tra GA cũ, test-part5-ui kiểm tra ghế/scheduler/GA expiry/restart trên DB riêng sạch, không dùng mock DB hoặc mock HTTP response trong live tests. Test properties/CLI chốt scheduler tắt hoặc bật theo từng ca. Không nhận test skipped là pass.

Đến hết Phần4 có35test backend (34 PostgreSQL integration +1 context H2) chạy qua infra/test-part4.ps1; DB riêng mới mỗi lần, không reset demo. PostgresBookingTest có16test mới. verify-part4.ps1 kiểm tra booking/replay/owner/history trên demo; tools/contracts verify-live.mjs kiểm tra15response HTTP theo schema. verify-part3.ps1 giữ tiêu chí seed GA100 ban đầu nên có thể không còn đạt sau khi đã đặt vé demo.

Frontend có11test Node/DOM (4client/storage +7interaction) và build TypeScript/Vite. infra/test-part4-ui.ps1 tạo DB riêng sạch, chạy JAR thật và App trong jsdom với fetch HTTP thật: booking2vé, tổng server140000, replay, stale inventory409, hết vé/history/CORS. jsdom không thay thế kiểm tra layout/browser; kết nối công cụ trình duyệt không khả dụng nên chưa có ảnh hoặc visual QA.

T1.4 có kiểm tra hợp đồng qua tools/contracts (OpenAPI3.1, JSON Schema event, fixture dương/âm), fixture byte UTF-8 và tools/contracts/measure-lock.ps1 trên PostgreSQL thật. Đây là kiểm tra thiết kế và cơ chế timeout, chưa phải concurrency booking. Gate HTTP/domain cần triển khai theo [TRACEABILITY](TRACEABILITY.md) và [ADR0002](adr/0002-api-domain-contract.md).

Test nghiệp vụ dùng PostgreSQL thật trong môi trường cô lập; Kafka test khi triển khai consumer/outbox. Không thay DB bằng mock cho concurrency. Test fixture reset giữa các ca. Kiểm tra bất biến bằng truy vấn DB sau các luồng, không chỉ HTTP status. Mock bearer token chỉ dùng test auth/API.

| ID | Tuần | Kịch bản | Bất biến/đầu ra |
|---|---|---|---|
| GA-01 | 3 | 50 request cạnh tranh capacity nhỏ | held+sold <= capacity, booking thành công không vượt số vé |
| IDEM-01 | 3 | 2 request cùng account/key/payload đồng thời | một booking, cùng bookingId/response sau commit |
| IDEM-02 | 3 | key cũ, payload khác | 409; booking gốc không đổi |
| IDEM-03 | 3 | hai request cùng key chờ quá giới hạn khóa; lock ghế/GA cũng quá hạn | rollback không có hold/booking dở; cùng key retry an toàn; không gán timeout inventory thành IDEMPOTENCY_IN_PROGRESS |
| SEAT-01 | 4 | 2 tập ghế giao nhau, gửi đảo thứ tự | một owner/ghế, không partial hold |
| SEAT-02 | 4 | ghế HELD hết hạn, đặt mới trước worker commit | conflict; sau commit mới có thể nhận |
| EXP-01 | 4 | hai worker quét cùng booking, retry | release đúng một lần, một EXPIRED transition |
| PAY-01 | 5 | payment result recorded trước hạn, consumer chậm, worker quét | worker/reconciler thấy result bền, xác nhận hoặc xử lý theo policy; không nhả nhầm |
| PAY-02 | 5 | worker EXPIRED trước payment success | EXPIRED -> REFUND_REQUIRED, không vé |
| PAY-03 | 5 | failure sau EXPIRED, success sau CANCELLED/PAYMENT_FAILED, duplicate success | no-op hoặc compensation theo bảng state; không confirm/nhả lần hai |
| ADMIN-01 | 5 | giảm capacity, xóa ghế HELD, đổi giá | từ chối thay đổi phá inventory; đơn cũ giữ giá snapshot |
| MSG-01 | 6 | broker dừng/gửi lặp/restart | outbox pending được xử lý, inbox dedup, không mất booking |
| TICKET-01 | 5 | confirm booking GA quantity N > 1 và gọi lại processor với cùng payment result | đúng N vé theo (booking_item_id,ticket_ordinal), ordinal 1..N; ghế có một vé; replay không tạo thêm vé |
| TICKET-02 | 6 | replay BookingConfirmed event cho consumer audit | không phát thêm ticket; event không phải lệnh issue ticket |
| COMP-01 | 6 | late success lặp, refund simulator fail rồi retry | một payment chỉ có một compensation; chỉ một lần hoàn tất; booking giữ REFUND_REQUIRED |
| MSG-02 | 6 | payment consumer chỉ nhận PaymentResultRecorded có aggregateVersion > 1, DB booking.version đã tăng; projection gặp gap thật | payment được phân xử từ row bền, không bị bỏ vì version; projection dừng event vượt gap và đưa recovery |
| SEC-01 | 7 | token lạ, CUSTOMER gọi admin, profile khác bật mock auth | 401/403/fail startup |
| LOAD-01 | 9 | >=50 request đồng thời ghế/GA trên máy demo | báo cáo success/conflict/latency, DB invariant, không bịa TPS |

## Cách chạy và chứng cứ

- Testcontainers hoặc Docker Compose test environment; T1.1 chốt cách chạy phù hợp máy. Không cần thêm Redis nếu không có use case.
- Ghi seed, request count, mức đồng thời, pool size, worker interval, CPU/RAM, version PostgreSQL/Kafka và commit hash.
- Sau test: kiểm tra GA counts, seat owner, booking_items, tickets unique, history, outbox/inbox, overdue hold count.
- Test deadlock bắt 40P01 và bounded retry; test 50 request không tự chứng minh không thể deadlock ở mọi tải.
- CI build/test và secret scan trước khi merge; không ghi số liệu benchmark vào tài liệu cho đến khi chạy thực tế.

## Ngưỡng nghiệm thu

Không oversell, không partial hold, không vé trùng, không release lặp, không stuck pending khi worker hoạt động. Latency/TPS chỉ được chốt sau baseline máy ở T1.1; nếu không đạt thì ghi số thực, điều tra, không đổi tiêu chí sau khi chạy để báo cáo đẹp.

## Gate Phần4 đã chạy ngày 2026-11-12

- T3.1:202/Location, GET owner/ADMIN, customer khác404, mine riêng/phân trang, validation400/401/403 và mã lỗi; giá từ server, TTL600s/history ban đầu.
- T3.2: IDEM-01 burst20request cùng key/payload →1booking/1body/1outbox, held2; IDEM-02 khác payload409 cả tuần tự và đồng thời; canonical UUID/field order/2.0; key scope theo account; replay sau EXPIRED dùng fixture DB có transition/outbox đúng, không triển khai worker.
- IDEM-03: một connection thật giữ inventory row hoặc unique key, HTTP503/Retry-After1 sau lock_timeout; query xác nhận không booking/item/key/outbox/hold dở, retry cùng key sau khi mở khóa thành công.
- T3.3: conditional UPDATE không đủ vé409; lỗi PostgreSQL sau outbox làm rollback toàn bộ. Deferred trigger chặn commit key chưa hoàn chỉnh. PostgreSQL trigger phát SQLSTATE40001 lần đầu rồi thành công; phát liên tục dừng ở3attempt/503 và không tăng hold lần hai. Đây là fault injection trên PostgreSQL thật, chưa phải tạo deadlock40P01 thật.
- T3.4 GA-01:50request use case,5customer, capacity10 →10success/40conflict/held10/bookings10/outbox10. Ca HTTP riêng50request →10HTTP202/40HTTP409/held10/bookings10/outbox10. Mỗi request quantity1, sold0. Chạy đồng thời bằng executor/latch; không suy ra TPS hay bảo đảm mọi tải từ hai ca này.
- T3.5:11test frontend đạt; ca live UI dùng API/DB thật capacity3 →held3/sold0 sau success/replay/conflict, CORS preflight204. Đã kiểm tra trạng thái/lỗi qua DOM; chưa kiểm tra bố cục thực trong Chrome.

Máy chạy: Windows, Intel i5-11400H 6core/12logical, RAM16948453376byte (~15,78GiB), JDK25.0.5, PostgreSQL17.11, pool10/minIdle2/acquisition2s, lock1500ms/statement5s/attempt5s/total10s, không expiry worker. Bằng chứng từ working tree trên HEAD d476b78086b8d4daeacf4ed0060eba7d398684f2 (chưa commit thay đổi). Không có benchmark latency/p95/TPS trong Phần4. Pool exhaustion, statement cancel độc lập, mất kết nối đúng lúc COMMIT và deadlock40P01 thật chưa fault-inject; không báo các ca đó đã đạt.

## Gate Phần5 đã chạy ngày 2026-11-12

- T4.1: PostgreSQL unique label/state check/composite owner event/type FK; availability map id/label/status không công khai owner. V1–V7 từ DB sạch, migrate lại0; restart JAR cùng DB test “No migration necessary”. Không sửa V1–V6.
- T4.2: HTTP202/Location/tổng400000 cho2ghế,2item/1history/1outbox, cùng owner/deadline; reject empty/duplicate/>6/mixed shape/type sai/UUID lạ. Khóa ghế thấp trước rồi gặp ghế cao đã giữ rollback ghế thấp/key/booking, retry an toàn.
- T4.4 SEAT-01:50lệnh qua use case từ2customer, tập ghế đảo thứ tự →1success/49conflict/1booking/2HELD/cùng1owner.50HTTP tranh1ghế →1HTTP202/49HTTP409/1HELD/1booking.20duplicate đảo seatIds →1id/1body/1outbox/2HELD; cùng key đổi ghế409.
- Ca giao nhau một phần A1+A2 với A2+A3:2customer/usecase đồng thời →1success/1conflict,2HELD/1owner/1booking/1outbox/2AVAILABLE; ghế riêng của bên thua không bị giữ dở.
- T4.3/T4.4 EXP-01/SEAT-02:20worker tranh1booking →1EXPIRED,0HELD,history2/outbox2 (held+expired). Worker lặp no-op; replay key gốc sau expiry/rebook không lấy lại ghế owner mới. GA expiry giảm held đúng một lần và khách khác giữ lại toàn capacity. Due fixture chỉnh deadline trong DB test; live scheduler riêng đợi thời gian thật.
- Worker có row lock booking thì worker khác SKIP LOCKED/BUSY; no-due/terminal không release. PostgreSQL advisory gate chặn worker sau release/outbox trước commit; command mới chưa hoàn tất, observer vẫn thấy booking PENDING/ghếHELD; mở gate thì worker commitEXPIRED rồi command mới thành công. Không chấp nhận ghế chỉ dựa deadline.
- Guard payment: SUCCESS fixture recorded_at trước expires_at →DEFERRED_PAYMENT, giữ state/inventory/outbox. SCHEDULED/recorded_atNULL không cản expiry. Đây là kiểm tra an toàn ranh giới T4, không phải PAY-01 confirm/ticket/compensation của T5.
- Batch51booking →pass đầu50,pass sau1,pass tiếp0;held về0. Một booking có trigger lỗi sau outbox không làm rollback booking khỏe trong cùng pass;failed1/expired1 được ghi log. Sau bỏ fault, booking còn lại expire được.
- T4.5: giữ row ghế thứ2 bằng connection thật →POST503/Retry-After1/không partial hold;expiry gặp lock cũng rollback state/owner/history/outbox rồi retry được. Trigger lỗi sau booking/expiry outbox rollback toàn bộ. Tạo deadlock thật giữa2transaction cập nhật seat theo thứ tự đảo:SQLSTATE40P01 quan sát1lần,2transaction commit qua3attempt tổng;version cuối2 mỗi ghế,không hold/booking giả. Fault PostgreSQL phát40P01 liên tục tại bước lưu response →đúng3attempt/503,0hold/booking/outbox.
- UI:16test đạt, gồm seat states/selection, stable key sau503+reload, conflictrefresh, countdown0 không tựEXPIRED. Live UI DB sạch TTL4s/worker1s:202 choA1+A2/tổng400000,replay,clash409,schedulerEXPIRED/ghếAVAILABLE,rebook mới,expire lần2;GA2vé hết hạn trả capacity2. SQL không orphanhold/ownerEXPIRED/oversell/duplicateBookingExpired. GA UI Phần4 cũng chạy lại đạt. Manual Windows launcher TTL20/worker1 và verify-part5 đã chạy đạt tương tự trên demo, không sửa `.env`.

Máy/pool giống gatePhần4; baseHEAD cf6852568cc9a4da427ea7874fb9921c284f0d52 +working tree chưa commit. Scheduler mặc định5s/batch50; test gọi worker trực tiếp và force expiry.enabled=false qua test properties, đã chạy đủ58test ngay cả khi env EXPIRY_ENABLED=true. GA UI CLI false cũng đạt dưới env true. LiveTTL4/interval1/CLItrue và demoTTL20/interval1 ghi riêng, không gọi là số đo production. Chưa có browser visual QA, benchmark latency/p95/TPS, pool exhaustion/statement cancellation độc lập hoặc mất mạng đúng COMMIT; payment processing/Kafka thuộc phần sau. GitHub CI mới chưa chạy vì chưa commit/push.

## Gate Phần6 đã chạy ngày 2026-11-12

- T5.1: HTTP202 intent/ACCEPTED;20HTTP cùng requestId và20callback nội bộ →1ledger/1PaymentResultRecorded/1BookingConfirmed/3ticket/held0/sold3. Changedscenario409,fieldlạ/status/recordedAt/requestId sai400,token401/admin403/owner404;service ngoài profile403. Không có unsignedprovidercallback mới.
- T5.2 PAY-01: record result trước nhưng cố tình chưa gọi adapter,fixture deadline đến hạn/recorded_at trước1ms;expiry gọi cùngprocessor→CONFIRMED/2vé/0Expired,version đã tăng không làm bỏ result. Boundary fixture recorded_at==expires_at đượcconfirm;+1microsecond thìREFUND_REQUIRED/0vé. Fixtures chỉnh DB chỉ trong test,không nhận timestamp client.
- PAY-02: expirycommit trước success,ghế được cấp owner mới;oldbooking→REFUND_REQUIRED/1comp/0vé,newowner vẫn2HELD. Successmuộn trướcworker khi cònpending ghi đủ2transition EXPIRED/REFUND_REQUIRED cùngrelease/comp/history/outbox;processedcallbacklặpno-op.
- PAY-03: successsaucancel/fail chỉcomp/stategiữ;failure sauEXPIRED hoặcCONFIRMED khôngrelease lại;multiplelatepayments mỗipayment1comp. Pending cótimelysuccess vàfailurechưa xử lý thìsuccessưu tiên,terminal đãcommit không đảo.
- Race thời gian thật:8vòng payment chờ80ms,expiry chờ110ms với deadlineDB100ms; đọc storedrecorded_at đối chiếu kết quả,không bịa latency/TPS. Sốconfirm/refund theo lần chạy lưu artifact. Racecancel/success8vòng:chỉCONFIRMED+vé/sold hoặcCANCELLED+comp/0vé,held0. Chưa coi8vòng là chứng minh mọi tải.
- TICKET-01: GA3vé/ordinal1..3,seat2vé/ordinal1/đúngseatId/label vàSOLDowner;unique code/ordinal/FK sai bị chặn. Khôngvé choREFUND_REQUIRED,processedcallback/BookingConfirmed không là lệnh issue lại. Compensation chỉPENDING/attempt0 ở T5.
- Fault PostgreSQL: recordingoutboxlỗi rollbackledger/version;ticketinsertlỗi rollbackconfirm/inventory/history/confirmoutbox nhưngresultbền giữđểretry;failurehistorylỗi rollbackrelease;compensationlỗi rollbacklateexpiry+refund;seat thứ2bịlock→503/rollbackkhôngpartialsale. Retry cóđúngmột confirm/phátvé.
- Deadline chung cho record/process: test budget rút ngắn, trigger recording chậm0,8s khiến processor hết ngân sách trước khi bắt đầu. API service trả503,ledger vẫn bền/booking pending/held2/0vé; retry cùng requestId xác nhận đúng2vé/1confirm. Simulator và maintenance không cộng hai budget10s riêng.
- T5.5:cancelbodyrỗng/repeat200;body{}400;confirmed409;cancel gặp timelyresult phảicommitconfirm/vé trước409. Admincapacity2 dướiheld/sold3 chạyđồngthờipayment trả409/rollbackprice;price/activechange đua với paidseat vẫnitemsnapshot200000/tổng400000,SOLDkhông bịexpiryrelease.
- T5.4/Live:26unit/DOM,không tựCONFIRMEDsau202,stable requestId/scenarioqua503/reload,noautoPOST,trảcancelconflictrefreshticket. Reactjsdom+API/PostgreSQL thật TTL4/interval1:GA3+seat2CONFIRMED/5mãunique,replayunchanged,failure/cancelrelease,DELAY→EXPIRED→REFUND_REQUIRED/0vé. Restart ledgerSCHEDULED cùngDB phục hồi/intentcũ/no migration necessary; SQL0vé saistate/ordinal/booking,0oversell,0SOLDowner sai,0duplicatecomp,V1–V8. CảGAUI vàSeatUIPhần4/5 cũng chạy lại đạt.

MáyWindows/i5-11400H6core12logical/RAM16948453376byte,JDK25.0.5,PostgreSQL17.11,pool10/2/2s,lock1500ms/statement5s/attempt5s/total10s. BaseHEAD ca08aaf5b67a1666063d442600bb86c4ad4d16c7 +workingtree chưa commit. ManualWindows TTL20/interval1 cũng đạt,catalog riêng để xem vé. Chưabrowservisual/remoteCI/benchmark/pool exhaustion/statementcancel độc lập/mất mạng đúngCOMMIT;provider thật/Kafka/inbox/refundworker ngoài phạm vi.
