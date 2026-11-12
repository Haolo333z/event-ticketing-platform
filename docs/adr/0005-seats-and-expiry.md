# ADR0005 — Giữ ghế và hết hạn (T4.1–T4.5)

Ngày 2026-11-12. Giữ stack,8route và schema/event v1 hiện có. Không triển khai payment hoặc Kafka.

## Các điểm cần thống nhất trước code

- POST booking mở lại oneOf CreateBooking đã thiết kế: GA quantity hoặc SEATED seatIds (1..6 UUID khác nhau, một event/type). Hash GA giữ nguyên để replay key Phần4; hash ghế dùng prefix SEATED và seatIds chuẩn hóa lowercase/sắp theo chuỗi UUID. Đảo thứ tự ghế vẫn là cùng nội dung. Replay luôn trước recheck catalog/inventory.
- Schema V2 đã có ghế/owner/deadline/label unique. Bổ sung V7 composite FK seats(owner,event,type)→bookings để DB cũng chặn owner khác event/type; không sửa V1–V6. Không thêm tọa độ giả: seat map local là lưới theo label từ availability, trạng thái AVAILABLE/HELD/SOLD; không lộ owner cho public.
- Booking transaction: key → share event/type → booking → từng seat FOR UPDATE theo UUID string tăng dần → kiểm tra event/type/status → hold +item →history/outbox/response →commit. Ghế HELD quá hạn chưa thành AVAILABLE trước worker commit. Worker: lock booking → recheck due bằng DB clock/payment →inventory cùng thứ tự UUID →state/history/outbox. Không gọi mạng trong transaction.
- **Ranh giới T4/T5:** TASKS T4.3 yêu cầu kiểm tra payment bền nhưng PaymentResultProcessor thuộc T5.2. Trong Phần5, bất kỳ result terminal có recorded_at nào đều làm worker DEFERRED_PAYMENT, giữ nguyên state/inventory; SCHEDULED chưa có result bền không chặn expiry. Không tự confirm/fail/refund và không coi result chưa xử lý là không tồn tại. Test PostgreSQL chứng minh success đúng hạn không bị nhả nhầm. T5.2 sẽ thay nhánh defer bằng processor dùng chung theo PRD; đây không phải nghiệm thu payment processing.

## Worker và retry

Scheduler mặc định bật ở app, interval5s/batch50; test profile mặc định tắt. Vì env có thể ưu tiên hơn profile, integration tests đặt expiry.enabled=false trực tiếp trong test properties và GA live dùng CLI false; seat expiry live dùng CLI true. Đã chạy full suite với EXPIRY_ENABLED=true và assert không có scheduler bean trong ca điều khiển worker. Scheduler chọn tối đa50due booking, mỗi booking một transaction REQUIRES_NEW với SELECT FOR UPDATE SKIP LOCKED và recheck. Nhiều worker có thể chọn cùng candidate nhưng chỉ một transition thắng. Lỗi một booking được báo log có bookingId, không hủy cả batch; scheduler không nuốt lỗi thành success. Counters pass gồm expired/deferred/busy/failed.

Dùng cùng transaction runner với booking: lock1500ms/statement5s/attempt5s/total10s,40P01/40001 tối đa3attempt, không retry domain409; timeout rollback. Không giữ transaction chọn batch trong lúc gọi từng booking. Shutdown/restart quét lại dữ liệu bền, không cần lease/Redis. Batch có thể defer result đến T5; không cam kết overdue bằng0 khi chưa có payment processor hoặc DB lỗi.

## UI và kiểm chứng

UI chọn type GA/SEATED, nút ghế có label/status và aria-pressed/disabled; tối đa6ghế, payload/key bất biến qua network/503/reload. Availability poll và GET booking lấy sự thật server. Countdown chỉ hiển thị thời gian còn lại; đến0 không tự EXPIRED hoặc trống ghế, tiếp tục chờ GET/availability. Kết quả không biết giữ key;409refresh rồi cho chọn lại. Giá tổng chỉ từ response server.

Gate: constraints/map; multi-seat rollback;2customer/50request tranh ghế; đảo thứ tự;20duplicate; row/key timeout; PostgreSQL deadlock thật và bounded retry; worker song song/lặp; worker chưa commit không lấy lại được; lỗi sau outbox expiry rollback; GA expiry/rebook và toàn suite GA cũ. UI unit/DOM và UI HTTP/PostgreSQL thật; ghi rõ phần browser visual chưa kiểm tra nếu công cụ không khả dụng.
