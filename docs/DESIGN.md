# DESIGN — Hướng dẫn giao diện demo

## T8.3 — Khôi phục sự kiện

Quản trị demo có tab Khôi phục, filter Cần xử lý/Đã phục hồi/Tất cả và phân trang. Từ Outbox chọn Chi tiết event để xem cả event đã gửi. Panel mở ở đầu vùng recovery, tự cuộn/focus lý do; nút Đóng chi tiết rõ ràng. Hiển thị trạng thái publisher, lỗi an toàn, consumer receipt riêng,100lỗi consumer/DLQ gần nhất, audit ADMIN/requestId/reason/time và ngân sách còn lại.

Replay yêu cầu lý do1..500Unicode point; không tự POST khi mở/refresh, chặn double click. Khi mất phản hồi giữ nguyên request trong session để gửi lại, không tạo key mới. Budget0 khóa yêu cầu mới nhưng vẫn cho gửi lại request đang chưa rõ kết quả; server quyết định cached replay hay409. 202 chỉ xác nhận yêu cầu đã được chấp nhận, có thể là kết quả cũ; không suy đó là xử lý mới hoặc đã hoàn tất. Receipt audit không chứng minh payment xong; consumer đã ghi nhận chống trùng, consumer chưa ghi nhận có thể xử lý lần đầu. Record không có outbox chỉ xem để điều tra; không có nút bỏ qua giới hạn hoặc sửa raw record. Audit text được escape; không nhập token/payment payload vào reason.

## Hiện trạng T7

Form Tạo/Sửa nằm ngay dưới nút Tạo sự kiện mới, trước bảng và phân trang. Khi mở, cuộn tới panel và focus tên sự kiện sau khi dữ liệu sẵn sàng; nút Đóng biểu mẫu ở đầu panel dùng được cả khi đang tải hoặc tải lỗi, nút Hủy ở cuối form. Việc mở/đóng không gửi lệnh tạo/sửa.

DemoShell có hai persona khách/admin dùng fixture bearer riêng; chọn màn hình không cấp quyền. Customer giữ pending command khi đổi persona, không tự POST lại. AdminDashboard xem catalog mọi trạng thái, snapshot inventory, booking/history/filter/pagination và outbox qua API; EventEditor tạo/sửa catalog theo hợp đồng. Snapshot được lấy lại khi mở tồn kho, có thời điểm để phân biệt dữ liệu cũ. Chuyển tab không dùng lại dữ liệu của tab khác, bấm tab hiện tại không xóa trạng thái đang hiển thị.

Customer có loading/error/empty riêng cho catalog/history/detail, báo snapshot cũ khi refresh lỗi, nhãn EXPIRED/REFUND_REQUIRED lấy từ server; không tự nhả hold theo countdown. DOM/HTTP tests không thay kiểm tra layout Chrome/mobile. Recovery UI và metrics thuộc T8, không có task T7.5 trong TASKS. Các ghi chú Phần5/6 dưới đây là lịch sử trước khi có dashboard.

> Phiên bản: 0.4 · Mục đích: trình diễn nghiệp vụ và trạng thái backend rõ ràng

## 1. Phong cách

- Tên làm việc EventFlow.
- Nền #F7F8FA, chữ #17202A, màu chính #4F46E5, thành công #138A5B, cảnh báo #D97706, lỗi #C2413B.
- Font Inter hoặc sans-serif hệ thống; tối đa ba cấp cỡ chữ.
- Bo góc 8–12 px, viền nhẹ, khoảng trắng nhất quán; tránh animation gây nhiễu.
- Trạng thái có nhãn/icon, không dựa vào màu đơn lẻ.

## 2. Danh tính demo

- Màn hình vào demo có lựa chọn Khách hàng demo và Quản trị viên demo.
- Client dùng mock bearer token đã cấu hình cùng dữ liệu seed; token chỉ hoạt động local/test. UI nêu rõ đây là quyền demo.
- Không cho nhập user ID/role để đổi danh tính; không hiện token trong UI, URL, console hoặc log.
- Khi đổi persona, xóa dữ liệu hiển thị/quyền của persona cũ rồi đọc API bằng persona mới. Giữ pending command đã lưu theo phạm vi persona/booking để retry cùng key; không tự POST hoặc dùng command của persona khác.
- API admin vẫn được server kiểm tra role; ẩn nút ở frontend không phải kiểm soát quyền.

## 3. Màn hình

### Khách hàng

1. Danh sách sự kiện: tìm/lọc, nhãn ghế hoặc vé vào cửa, thời gian và giá từ server.
2. Chi tiết: địa điểm/thời gian, sơ đồ ghế hoặc nhóm vé, snapshot availability.
3. Xác nhận: ghế/số lượng, giá server trả, tổng tiền, thời hạn giữ.
4. Trạng thái booking: timeline và polling có giới hạn; có nút tải lại.
5. Lịch sử booking: mã đơn, sự kiện, số lượng/chỗ, thời gian, trạng thái.

### Quản trị viên

1. Dashboard: booking theo trạng thái, inventory, outbox pending/cảnh báo.
2. Quản lý sự kiện: tạo/sửa, bật/tắt bán, cấu hình khu vực/loại vé.
3. Sơ đồ ghế/sức chứa GA: trạng thái có màu và nhãn.
4. Chi tiết booking: history, expiry time, event/correlation ID trong demo.
5. Recovery view: event lỗi/outbox pending; replay chỉ local và qua API phân quyền, có audit. API chi tiết ở [API_CONTRACT](API_CONTRACT.md).

## 4. Trạng thái hiển thị

| Backend status | Nhãn UI | Hành động/giải thích |
|---|---|---|
| PENDING_PAYMENT | Đang chờ thanh toán | Countdown theo expiresAt từ server. |
| CONFIRMED | Đã xác nhận | Hiện vé demo nếu đã phát hành. |
| PAYMENT_FAILED | Thanh toán thất bại | Không giữ inventory. |
| CANCELLED | Đã hủy | Không giữ inventory. |
| EXPIRED | Đã hết hạn | Hold đã nhả; không có vé. |
| REFUND_REQUIRED | Cần hoàn tiền | Payment thành công muộn; không phát vé, cần bù trừ. |

Transition late payment là EXPIRED -> REFUND_REQUIRED. Nếu cả hai transition xảy ra, timeline hiển thị EXPIRED trước REFUND_REQUIRED; trạng thái cuối không xóa mốc hết hạn.

## 5. Tương tác quan trọng

- Lựa chọn ghế trên UI chỉ tạm thời; server mới xác nhận hold.
- Khóa nút đặt trong lúc gửi. Khi retry cùng thao tác, giữ nguyên idempotency key và payload.
- Countdown lấy expiresAt từ server; hết giờ thì tải trạng thái, không tự chuyển booking ở client.
- Với HOLD_EXPIRY_PROCESSING hoặc inventory chưa sẵn sàng trong lúc worker nhả hold, báo “Vé đang được cập nhật, vui lòng tải lại”; cho phép retry. Không tự đánh dấu ghế trống.
- Khi hết hàng, làm mới availability và yêu cầu chọn tài nguyên khác.
- Giá/tổng tiền lấy từ response API. Payment simulator dùng POST /api/bookings/{id}/simulate-payment với SUCCESS, FAILURE, DELAY_TIMEOUT; luôn ghi “Demo — không thu tiền thật”.
- REFUND_REQUIRED không hiển thị vé như booking đã xác nhận; nêu trạng thái bù trừ.

## 6. Thành phần và accessibility

- AppShell, TopNav, DemoPersonaPicker, PageHeader, Toast, LoadingState, ErrorState, EmptyState.
- EventCard, EventFilters, EventTypeBadge, AvailabilityIndicator.
- SeatMap, SeatLegend, SeatButton; TicketTypeSelector, QuantityStepper, PriceSummary, HoldCountdown.
- BookingStatusBadge, BookingTimeline, PaymentSimulationPanel (demo/dev only).
- AdminMetricCard, InventoryTable, OutboxTable, RetryStatus.
- Mobile-first; seat map cuộn ngang, bàn phím dùng được; có focus ring, label, tab order và lỗi gắn với trường.
- Không hiển thị stack trace/token/dữ liệu nhạy cảm. UI chỉ trình bày kết quả server; backend quyết định inventory, giá, quyền và state.


Phần5 đã có lưới ghế theo label API, nhãn Trống/Đang giữ/Không khả dụng và aria-pressed cho ghế đang chọn. Không bịa tọa độ venue; public map không lộ owner. Countdown đến0 chờ GET/availability thật; poll3s cập nhật EXPIRED và ghế trống sau worker commit. Có DOM interaction và UI HTTP/PostgreSQL integration, chưa kiểm tra layout trực quan trên browser thật. Các màn payment/admin/recovery chưa hoàn thiện. Schema/transaction tại DATA_MODEL, event tại EVENT_CATALOG.

Phần6 có Checkout với ba scenario/cancel và vé từ API. UI nói rõ không thu tiền thật/không nhập thẻ. requestId/scenario/bookingId được giữ khi network/503/500 và reload; không tự gửi payment khi mở lại. Poll pending đến expiresAt+90s, poll EXPIRED thêm90s để nhận REFUND_REQUIRED, sau đó dùng nút cập nhật thủ công. ChỉCONFIRMED hiển thị ticket code/GAordinal/seatLabel do server trả;REFUND_REQUIRED giải thích nghĩa vụ bù trừ đangPENDING,không hiển thị vé. Browser visual QA chưa chạy; admin/recovery còn thuộc phần sau.
