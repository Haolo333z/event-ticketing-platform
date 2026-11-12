# TEST_PLAN — Kiểm thử và bằng chứng đồ án

> Đây là kế hoạch; chưa có test/code hoặc số đo. Kết quả thực tế lưu sau khi triển khai, gồm máy, cấu hình DB pool và log.

## Nguyên tắc

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
