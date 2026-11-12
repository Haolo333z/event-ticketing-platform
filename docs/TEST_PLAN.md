# TEST_PLAN — Kiểm thử và bằng chứng đồ án

> Gồm kế hoạch toàn dự án và kết quả đã chạy đến Phần4. Các ca ghế/expiry/payment/Kafka vẫn là kế hoạch; không coi chúng đã đạt. Kết quả thực tại PROGRESS và evidence/part4-2026-11-12.json.

## Nguyên tắc

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
