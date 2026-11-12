# DESIGN — Hướng dẫn giao diện demo

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
- Khi đổi persona, xóa state phiên hiện tại rồi gọi API bằng persona mới.
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


Thiết kế schema/transaction ở [DATA_MODEL](DATA_MODEL.md), event ở [EVENT_CATALOG](EVENT_CATALOG.md). Wireframe sẽ hoàn thiện khi làm UI, chưa được kiểm chứng thực tế.
