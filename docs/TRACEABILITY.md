# Đối chiếu PRD → hợp đồng → task → kiểm tra (T1.4)

| PRD / quy tắc | Dữ liệu / API / event | Task triển khai | Test nghiệm thu sau triển khai |
|---|---|---|---|
| §3.1–2 catalog ghế/GA | events, ticket_types, seats, inventory_ga; listEvents/getEvent/getAvailability/createEvent/patchEvent | T2.1–T2.4 | ADMIN-01, validation mode/giá/FK/sale window |
| §4.1 hold nguyên tử, tối đa6 | bookings/items/idempotency_keys; createBooking; BookingHeld | T3.1–T3.4, T4.1–T4.5 | GA-01, IDEM-01/02/03, SEAT-01/02 |
| §4.2 nhả bằng worker | seats/GA/history/outbox; BookingExpired | T4.1–T4.5 | EXP-01, SEAT-02 |
| §4.3 payment bền, late success | payments/compensations, simulatePayment; PaymentResultRecorded/BookingRefundRequired | T5.1–T5.5, T6.1–T6.5 | PAY-01/02/03, COMP-01 |
| §5 state machine duy nhất | BookingStatus enum6; getBooking/listMyBookings/listAdminBookings/cancelBooking; history và event terminal | T3.1–T3.5–T5.1–T5.5 | mọi cạnh PRD §5, no-op sau terminal; không cạnh REFUND_REQUIRED→CONFIRMED |
| §5 phát N vé idempotent | tickets unique(item,ordinal), getTickets; BookingConfirmed chỉ thông báo | T5.1–T5.5, T6.1–T6.5 | TICKET-01/02 |
| §3.7 principal demo, owner/admin | users/config seed; x-role/x-profiles và DemoBearer trên OpenAPI | T2.2/T2.4, T7.1/T7.4 | SEC-01;401 token lạ,403 role sai,404 owner sai,fail startup profile sai |
| §5 admin bảo toàn tồn/giá | patchEvent, FK/check và lock inventory | T2.4, T5.1–T5.5 | ADMIN-01 và race capacity/booking |
| §5 outbox/inbox/recovery | outbox/inbox, listOutbox/replayEvent,7 event type, aggregateVersion | T2.5, T6.1–T6.5 | MSG-01/02,COMP-01,TICKET-02 |
| §6 tải/truy vết | correlationId; body/CORS/timeout ADR0002 | T3.1–T3.5,T7.1/T7.4,T9.2/T9.3 | IDEM-03,LOAD-01;413 biên byte/chunked,415 media,preflight204/denied origin |

T1.4 đã kiểm tra cú pháp OpenAPI/$ref, fixture GA/seat và payload sai,7 event schemas, enum state, metadata role/profile/header/CORS; đo kích thước request UTF-8 và timeout DB bằng advisory lock. Những kiểm tra này không chứng minh FK/constraint đã tồn tại hoặc auth/CORS/error mapping đã chạy. Không có migration V2 hay controller nghiệp vụ trong T1.4.

## Gate triển khai bắt buộc

- T2.1: FK tổng hợp, CHECK/unique/index và delete policy trong DATA_MODEL; DB sạch thật. Không thay V1.
- T2.4/T7.1/T7.4: request đúng limit và limit+1 kể cả chunked; vượt tổng ghế200 dù từng type hợp lệ; POST/PATCH validate payload/domain; Origin được phép và bị từ chối; OPTIONS không yêu cầu token.
- T3.1–T3.5: SET LOCAL timeout trong transaction; cố tình khóa unique key và inventory riêng, rollback trước503, retry cùng key không tạo hold dở. Phần4 đã đạt các ca này và GA-01/IDEM-01/02/03; bằng chứng ở TEST_PLAN/PROGRESS. Pool exhaustion/statement cancel độc lập và mất kết nối đúng lúc COMMIT còn là ca tăng cường cần thực hiện sau; frontend đã kiểm tra giữ nguyên command khi network error/503, chưa thay cho fault injection COMMIT trên DB.
- T5.1–T5.5/T6.1–T6.5: result scheduling bền, aggregateId/result đúng booking, null recorded_at lúc scheduled, chỉ DB clock khi kết quả bền, timestamp không do client quyết định; replay giữ eventId và không bỏ inbox.
- Khi sửa contract chạy `npm ci --prefix tools/contracts` và `npm test --prefix tools/contracts`; các thay đổi schema/event/version phải review cùng PRD.

## Bằng chứng triển khai Phần5

- T4.1: V7 composite owner FK; test unique label/state/owner, availability không lộ owner; UI seat status từ API.
- T4.2/T4.4 SEAT-01: khóa từng UUID, đảo thứ tự và overlap,50lệnh/2customer và50HTTP chỉ1booking thắng;20duplicate chỉ1booking/1outbox. Input sai, multi-seat conflict và lỗi sau outbox không giữ một phần.
- T4.3/T4.4 EXP-01/SEAT-02:20worker/1transition; GA release/rebook; worker chưa commit thì command mới đợi và chỉ nhận ghế sau commit, worker cũ không nhả owner mới. RunOnce batch50+1, một lỗi không rollback booking khác. Result SUCCESS ghi trước hạn được defer/giữ tồn kho (T5.2 chưa triển khai).
- T4.5: lock timeout ghế/expiry rollback;40P01 thật retry (2transaction/3attempt tổng), SQLSTATE40P01 lỗi liên tục dừng3attempt/503; không partial hold. UI/live scheduler, restart DB test cùng FlywayV7 đạt; bằng chứng tại TEST_PLAN/PROGRESS/evidence.
