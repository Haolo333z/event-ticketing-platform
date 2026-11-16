# EVENTFLOW V2 — FEATURE BACKLOG & REQUIREMENTS SPECIFICATION
## CHI TIẾT DANH MỤC TÍNH NĂNG & TIÊU CHUẨN NGHIỆM THU

> **Phiên bản:** 2.0.0 · **Mã tài liệu:** `DOC-REC-02`  
> **Người biên soạn:** Senior Product Manager, Lead QA Engineer, Senior Full-stack Engineer  
> **Mục tiêu:** Cung cấp đặc tả chi tiết toàn bộ tính năng V2, tiêu chuẩn chấp nhận (Acceptance Criteria), ánh xạ API, bảng dữ liệu SQL và phân loại ưu tiên triển khai.

---

## 1. BẢNG TỔNG HỢP PHÂN LOẠI BACKLOG THEO VAI TRÒ VÀ ĐỘ ƯU TIÊN

| Mã Epic | Vai trò Người dùng | Tổng số tính năng | Ưu tiên P0 (Cốt lõi) | Ưu tiên P1 (Nên có) | Ưu tiên P2 (Mở rộng) |
|---|---|---|---|---|---|
| **EPIC-CUST** | CUSTOMER (Khách hàng) | 12 | 7 | 3 | 2 |
| **EPIC-ORGN** | ORGANIZER (Nhà tổ chức) | 10 | 6 | 3 | 1 |
| **EPIC-STAF** | STAFF (Nhân sự soát vé) | 6 | 4 | 2 | 0 |
| **EPIC-ADMN** | ADMIN (Quản trị viên) | 10 | 6 | 3 | 1 |
| **EPIC-CORE** | NỀN TẢNG KỸ THUẬT | 8 | 6 | 2 | 0 |
| **TỔNG CỘNG** | **4 VAI TRÒ + HỆ THỐNG** | **46** | **29 (63%)** | **13 (28%)** | **4 (9%)** |

---

## 2. EPIC-CUST: TÍNH NĂNG DÀNH CHO KHÁCH HÀNG (CUSTOMER)

### CUST-01: Đăng ký & Đăng nhập Email + Mật khẩu `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Người dùng đăng ký tài khoản bằng email, mật khẩu (tối thiểu 6 ký tự), họ tên và số điện thoại. Đăng nhập để nhận JWT Token.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Băm mật khẩu an toàn với BCrypt.
  - [x] Chặn trùng lặp email với mã lỗi `400 DUPLICATE_EMAIL`.
  - [x] Đăng nhập sai mật khẩu trả về `401 INVALID_CREDENTIALS`.
  - [x] Lưu bền vững vào bảng `ticketing.users`.
- **API Ánh xạ:** `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`.
- **Dữ liệu SQL:** `ticketing.users`.

### CUST-02: Đăng nhập Google (Google OIDC Single Sign-On) `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khách hàng có thể đăng nhập 1-click bằng tài khoản Google thông qua giao thức OpenID Connect chuẩn PKCE.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Khởi tạo luồng Authorization Code Flow với state và nonce bảo mật chống CSRF.
  - [ ] Backend Spring Security OAuth2 xác thực chữ ký ID Token từ Google IdP (`accounts.google.com`).
  - [ ] Tự động tạo bản ghi tài khoản liên kết trong bảng `ticketing.external_identities` (provider='GOOGLE', subject=sub).
  - [ ] Nếu email đã tồn tại do đăng ký mật khẩu trước đó, yêu cầu xác nhận mật khẩu (Explicit Linking) để chống chiếm đoạt tài khoản (Account Takeover).
- **API Ánh xạ:** `GET /oauth2/authorization/google`, `GET /login/oauth2/code/google`, `POST /api/auth/link-google`.
- **Dữ liệu SQL:** `ticketing.users`, `ticketing.external_identities`.

### CUST-03: Khám phá Sự kiện & Tìm kiếm Đa tiêu chí `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khách hàng duyệt danh sách sự kiện trên trang chủ `/` và trang chuyên biệt `/dat-ve`.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Tìm kiếm tức thì theo tiêu đề, địa điểm, nghệ sĩ.
  - [x] Bộ lọc theo thể loại: Đại nhạc hội, Nhà hát & Thính phòng, Hội nghị Công nghệ, Thể thao.
  - [x] Hiển thị thẻ poster 16:9 sắc nét, thời gian mở bán, địa điểm và mức giá khởi điểm.
- **API Ánh xạ:** `GET /api/events?page=0&size=20&search=...&category=...`
- **Dữ liệu SQL:** `ticketing.events`, `ticketing.ticket_types`.

### CUST-04: Sơ đồ Ghế Sân khấu Realtime & Chọn vé GA `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Trực quan hóa sân khấu `🎭 SÂN KHẤU CHÍNH / STAGE`, phân biệt trạng thái từng vị trí ghế (Trống, Đang chọn, Đang giữ, Đã bán).
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Đồng bộ trạng thái ghế realtime từ database (`GET /api/events/{id}/availability`).
  - [x] Ghế đã bán (`SOLD`) tự động khóa, không cho phép chọn.
  - [x] Chống mua vượt quá số lượng tối đa 6 vé/đơn.
- **API Ánh xạ:** `GET /api/events/{id}`, `GET /api/events/{id}/availability`.
- **Dữ liệu SQL:** `ticketing.events`, `ticketing.ticket_types`, `ticketing.seats`, `ticketing.inventory_ga`.

### CUST-05: Khóa Giữ Chỗ Nguyên Tử (Atomic Hold 10 Phút) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khi khách hàng bấm "Giữ vé", backend sử dụng PostgreSQL Row-level Lock (`SELECT FOR UPDATE`) khóa độc quyền ghế hoặc tồn kho GA trong 10 phút.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Đơn hàng chuyển trạng thái `PENDING_PAYMENT`, gắn `expires_at = clock_timestamp() + INTERVAL '10 minutes'`.
  - [x] Người thứ hai chọn cùng ghế ngay lập tức nhận phản hồi `409 CONFLICT` hoặc `503 BOOKING_BUSY`.
  - [x] Hỗ trợ Idempotency-Key chống gửi lặp request do click đúp.
- **API Ánh xạ:** `POST /api/bookings` (Header: `Idempotency-Key`).
- **Dữ liệu SQL:** `ticketing.bookings`, `ticketing.booking_items`, `ticketing.idempotency_keys`, `ticketing.seats`.

### CUST-06: Cổng Giữ Vé Thông Minh (Auth Gate) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khách vãng lai chưa đăng nhập bấm giữ vé -> Bật modal đăng nhập/đăng ký. Đăng nhập xong tự động thực hiện giữ vé mà không mất lựa chọn ghế.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Lưu trữ hành động chờ `pendingBookingAction` trong bộ nhớ client.
  - [x] Sau khi `handleAuthSuccess`, tự động gọi lại hàm giữ vé.
- **Giao diện Ánh xạ:** `frontend/src/app/DemoShell.ts`, `frontend/src/app/AuthModal.ts`.

### CUST-07: Thanh Toán Sandbox (External Gateway) & Simulator `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Tích hợp Cổng thanh toán Sandbox bên ngoài (VNPAY / PayOS) tạo URL thanh toán, đồng thời duy trì Simulator cho kiểm thử tự động.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Khách hàng được chuyển hướng sang cổng thanh toán hoặc quét mã VietQR động.
  - [ ] Xử lý Webhook IPN từ cổng thanh toán để chuyển đơn hàng sang `CONFIRMED`.
  - [ ] Thanh toán đúng hạn: phát hành vé điện tử.
  - [ ] Thanh toán sau khi hết hạn giữ chỗ (Late Payment): Chuyển đơn sang `REFUND_REQUIRED`, hoàn trả tiền cho khách, không cấp vé.
- **API Ánh xạ:** `POST /api/bookings/{id}/initiate-payment`, `POST /api/payments/webhook`, `POST /api/bookings/{id}/simulate-payment`.
- **Dữ liệu SQL:** `ticketing.payments`, `ticketing.payment_compensations`.

### CUST-08: Ví Vé Điện Tử & Mã QR Check-in (My Tickets) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khách hàng xem danh sách vé đã mua trên trang chuyên biệt `/ve-cua-toi`, xuất trình thẻ Boarding pass kèm mã QR SVG tại cổng.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Tra cứu vé cá nhân từ `GET /api/me/bookings` (bảo vệ quyền truy cập tài khoản chính chủ).
  - [x] Thẻ vé hiển thị mã check-in độc nhất, vị trí ghế ngồi, tên sự kiện, ngày giờ, mã QR SVG.
- **API Ánh xạ:** `GET /api/me/bookings`, `GET /api/bookings/{id}/tickets`.
- **Dữ liệu SQL:** `ticketing.bookings`, `ticketing.tickets`.

### CUST-09: Tải Vé Điện Tử Dạng File PDF `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Khách hàng có nút "Tải PDF" để lưu vé về máy hoặc in ấn mang theo khi tham dự sự kiện.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Sinh tệp PDF A4 chuẩn phong cách Boarding Pass với thư viện OpenPDF/iText.
  - [ ] Đính kèm mã QR vector sắc nét, hướng dẫn vào cổng và điều khoản vé.
- **API Ánh xạ:** `GET /api/bookings/{id}/tickets/{ticketId}/pdf`.

### CUST-10: Áp Dụng Mã Giảm Giá (Voucher / Coupon) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Nhập mã khuyến mãi trong bước thanh toán (ví dụ: `EVENTFLOW50` giảm 50.000 VNĐ).
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Kiểm tra hạn sử dụng, số lượt dùng còn lại, mức chiết khấu tối đa.
  - [ ] Cập nhật lại số tiền thanh toán `total_amount_vnd` hợp lệ trên server-side.
- **API Ánh xạ:** `POST /api/promotions/apply-voucher`.
- **Dữ liệu SQL:** `ticketing.vouchers`, `ticketing.voucher_usages`.

### CUST-11: Yêu Thích Sự Kiện (Wishlist / Bookmark) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Đánh dấu trái tim sự kiện quan tâm để xem lại nhanh trong hồ sơ cá nhân.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Lưu trữ cặp `(user_id, event_id)` trong database PostgreSQL.
  - [ ] Tab "Sự kiện đã lưu" trong trang cá nhân.
- **API Ánh xạ:** `POST /api/me/wishlist/{eventId}`, `DELETE /api/me/wishlist/{eventId}`, `GET /api/me/wishlist`.
- **Dữ liệu SQL:** `ticketing.user_wishlists`.

### CUST-12: Gửi Yêu Cầu Hỗ Trợ Đơn Hàng `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P2 (Nice-to-Have)
- **Mô tả:** Khách hàng gửi yêu cầu hỗ trợ (đổi email nhận vé, hoàn tiền sự kiện hoãn hủy).
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Tạo ticket hỗ trợ gắn với `booking_id`.
- **API Ánh xạ:** `POST /api/support/tickets`.
- **Dữ liệu SQL:** `ticketing.support_tickets`.

---

## 3. EPIC-ORGN: TÍNH NĂNG DÀNH CHO NHÀ TỔ CHỨC (ORGANIZER)

### ORGN-01: Cổng Tự Phục Vụ Organizer Dashboard `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Màn hình quản trị riêng biệt cho tài khoản có vai trò `ORGANIZER` quản lý các sự kiện do mình sở hữu.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Phân quyền Event Scope: Organizer A tuyệt đối không xem hoặc sửa sự kiện của Organizer B.
  - [ ] Thống kê doanh thu theo từng sự kiện, số lượng vé đã bán, tỷ lệ lấp đầy ghế.
- **API Ánh xạ:** `GET /api/organizer/dashboard`, `GET /api/organizer/events`.
- **Dữ liệu SQL:** `ticketing.organizer_events`, `ticketing.events`.

### ORGN-02: Quy Trình Khởi Tạo & Chỉnh Sửa Sự Kiện (Event Wizard) `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Form tạo sự kiện đa bước gồm: Thông tin chung, Địa điểm, Thời gian mở bán/kết thúc/bắt đầu, Poster sự kiện.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Validate nghiêm ngặt thời gian: `sale_start_at < sale_end_at <= starts_at`.
  - [ ] Sự kiện tạo mới có trạng thái ban đầu là `DRAFT` hoặc `PENDING_APPROVAL`.
- **API Ánh xạ:** `POST /api/organizer/events`, `PATCH /api/organizer/events/{id}`.
- **Dữ liệu SQL:** `ticketing.events`, `ticketing.organizer_events`.

### ORGN-03: Cấu Hình Loại Vé & Phân Bổ Tồn Kho GA `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Thiết lập các hạng vé không chỉ định chỗ (Vé Phổ thông, Vé VIP Đứng) với sức chứa `capacity` và giá tiền `price_vnd`.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Tạo tự động bản ghi trong `ticketing.ticket_types` và `ticketing.inventory_ga`.
  - [x] Không được phép giảm `capacity` nhỏ hơn số vé đã bán `sold`.
- **API Ánh xạ:** `POST /api/organizer/events/{id}/ticket-types/ga`.
- **Dữ liệu SQL:** `ticketing.ticket_types`, `ticketing.inventory_ga`.

### ORGN-04: Cấu Hình Sơ Đồ Ghế Ngồi Nhà Hát (Seated Layout Designer) `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Thiết lập sơ đồ hàng ghế (Hàng A1-A20, Hàng B1-B20...) và gán mức giá theo từng khu vực.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Lưu trữ từng ghế trong `ticketing.seats` với trạng thái `AVAILABLE`.
  - [x] Chống trùng lặp nhãn ghế (`UNIQUE(ticket_type_id, label)`).
- **API Ánh xạ:** `POST /api/organizer/events/{id}/ticket-types/seated`.
- **Dữ liệu SQL:** `ticketing.seats`, `ticketing.ticket_types`.

### ORGN-05: Quản Lý & Tải Lên Poster Sự Kiện (Media Upload) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Tải lên hình ảnh poster chính (tỷ lệ 16:9), ảnh banner và sơ đồ địa điểm.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Kiểm tra định dạng ảnh (JPG, PNG, WebP) và dung lượng tối đa 5MB.
  - [ ] Lưu file vào thư mục lưu trữ an toàn hoặc Cloud Storage, lưu URL vào database.
- **API Ánh xạ:** `POST /api/organizer/events/{id}/media`.
- **Dữ liệu SQL:** `ticketing.event_media`.

### ORGN-06: Phân Công Nhân Sự Soát Vé (Staff Assignment) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Organizer nhập email của nhân viên (vai trò `STAFF`) để gán quyền soát vé cho sự kiện cụ thể.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Nhân viên chỉ được cấp quyền soát vé đúng sự kiện được phân công.
  - [ ] Hỗ trợ phân quyền theo cổng vào (Gate A, Gate B).
- **API Ánh xạ:** `POST /api/organizer/events/{id}/staff`, `DELETE /api/organizer/events/{id}/staff/{userId}`.
- **Dữ liệu SQL:** `ticketing.event_staff_assignments`.

### ORGN-07: Quản Lý Đơn Hàng & Danh Sách Khán Giả (Attendee Management) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Xem danh sách người đã mua vé sự kiện của mình, xuất báo cáo CSV phục vụ công tác an ninh.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Danh sách gồm: Mã booking, Họ tên, Email, Số điện thoại, Mã vé, Vị trí ghế, Trạng thái Check-in.
  - [ ] Ẩn thông tin nhạy cảm của khách hàng theo chuẩn bảo mật dữ liệu.
- **API Ánh xạ:** `GET /api/organizer/events/{id}/attendees`, `GET /api/organizer/events/{id}/attendees/export-csv`.
- **Dữ liệu SQL:** `ticketing.bookings`, `ticketing.tickets`, `ticketing.users`.

### ORGN-08: Quản Lý Vòng Đời Sự Kiện (Event Lifecycle Management) `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Gửi yêu cầu mở bán (Submit for Review), Tạm dừng mở bán (Pause), Đóng sự kiện (Close) hoặc Hủy sự kiện (Cancel).
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Hủy sự kiện kích hoạt quy trình hoàn tiền tự động cho tất cả các vé đã bán.
- **API Ánh xạ:** `POST /api/organizer/events/{id}/publish-request`, `POST /api/organizer/events/{id}/close`.
- **Dữ liệu SQL:** `ticketing.events`.

### ORGN-09: Yêu Cầu Quyết Toán Doanh Thu (Payout Request) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Sau khi sự kiện kết thúc thành công, Organizer gửi yêu cầu nhận tiền bán vé về tài khoản ngân hàng.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Trừ phí hoa hồng nền tảng (Platform fee, ví dụ: 5%).
  - [ ] Gửi yêu cầu lên Admin xét duyệt chuyển khoản.
- **API Ánh xạ:** `POST /api/organizer/events/{id}/payout-request`.
- **Dữ liệu SQL:** `ticketing.organizer_payouts`.

### ORGN-10: Quản Lý Địa Điểm Tổ Chức (Venue Profiles) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P2 (Nice-to-Have)
- **Mô tả:** Chọn địa điểm mẫu (Nhà hát Lớn Hà Nội, Trung tâm Hội nghị Quốc gia...) hoặc tạo địa điểm mới.
- **API Ánh xạ:** `GET /api/venues`, `POST /api/venues`.
- **Dữ liệu SQL:** `ticketing.venues`.

---

## 4. EPIC-STAF: TÍNH NĂNG DÀNH CHO NHÂN VIÊN SOÁT VÉ (STAFF)

### STAF-01: Đăng Nhập & Chọn Sự Kiện Được Phân Công `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Nhân viên đăng nhập tài khoản vai trò `STAFF`, hệ thống chỉ hiển thị danh sách các sự kiện mà nhân viên này được Organizer gán nhiệm vụ.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Chặn truy cập nếu Staff cố tình truy vấn sự kiện không được phân công (403 FORBIDDEN).
- **API Ánh xạ:** `GET /api/staff/assigned-events`.
- **Dữ liệu SQL:** `ticketing.event_staff_assignments`, `ticketing.events`.

### STAF-02: Giao Diện Quét Mã QR Di Động (Mobile QR Scanner) `[CẦN HOÀN THIỆN]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Giao diện web tối ưu responsive cho điện thoại thông minh, sử dụng camera thiết bị để quét mã QR trên vé điện tử của khán giả.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Tích hợp thư viện quét mã HTML5 (`html5-qrcode` hoặc thư viện tương đương nhẹ).
  - [ ] Tốc độ đọc mã dưới 500ms, hỗ trợ bật đèn Flash trong môi trường thiếu sáng.
- **Giao diện Ánh xạ:** `frontend/src/app/StaffCheckInView.ts`.

### STAF-03: Xác Thực Vé & Check-in Nguyên Tử (Atomic Check-in Engine) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khi quét mã QR, backend kiểm tra tính hợp lệ của vé và ghi nhận vào cổng ngay lập tức.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Kiểm tra vé có trạng thái `CONFIRMED` và thuộc đúng sự kiện đang soát.
  - [ ] Cơ chế Chống quét trùng (Anti Double Check-in): Áp dụng ràng buộc `UNIQUE(ticket_id)` trong bảng `ticketing.ticket_check_ins`.
  - [ ] Nếu quét lần đầu: Màn hình hiện màu XANH LÁ + Âm thanh báo thành công + Tên khách + Số ghế.
  - [ ] Nếu quét lần 2: Màn hình hiện màu ĐỎ BÁO ĐỘNG + Âm thanh cảnh báo + "VÉ ĐÃ VÀO CỔNG LÚC HH:mm:ss TẠI CỔNG A".
- **API Ánh xạ:** `POST /api/staff/events/{eventId}/check-in`.
- **Dữ liệu SQL:** `ticketing.ticket_check_ins`, `ticketing.tickets`.

### STAF-04: Tra Cứu Vé Khẩn Cấp Bằng Tay (Manual Search) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Khách hàng bị hỏng màn hình điện thoại hoặc không mở được QR, Staff tra cứu bằng Mã check-in (ví dụ: `EF-1fa49f6c...`), Số điện thoại hoặc Email.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Hiển thị thông tin vé tìm thấy và nút "Xác nhận vào cổng thủ công".
- **API Ánh xạ:** `GET /api/staff/events/{eventId}/tickets/search?query=...`
- **Dữ liệu SQL:** `ticketing.tickets`, `ticketing.bookings`, `ticketing.users`.

### STAF-05: Lịch Sử Quét Vé Thời Gian Thực (Live Check-in Feed) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Danh sách 10 lượt khách vừa vào cổng gần nhất kèm thời gian và vị trí ghế.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Cập nhật realtime số lượng: `Đã vào: 450 / Tổng vé: 500 (90%)`.
- **API Ánh xạ:** `GET /api/staff/events/{eventId}/check-in/stats`.
- **Dữ liệu SQL:** `ticketing.ticket_check_ins`.

### STAF-06: Hoạt Động Ngoại Tuyến Cục Bộ (Offline Cache Fallback) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Hỗ trợ tải trước danh sách mã vé hợp lệ vào máy Staff để kiểm tra nếu mạng tại cửa rạp bị nghẽn sóng.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Đồng bộ dữ liệu check-in ngược lên máy chủ ngay khi có kết nối trở lại.
- **API Ánh xạ:** `GET /api/staff/events/{eventId}/offline-manifest`, `POST /api/staff/events/{eventId}/sync-check-ins`.

---

## 5. EPIC-ADMN: TÍNH NĂNG DÀNH CHO QUẢN TRỊ VIÊN SÀN (ADMIN)

### ADMN-01: Bảng Quản Trị Hệ Thống Tổng Thể (Admin Master Dashboard) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Trung tâm điều hành toàn sàn dành riêng cho tài khoản có vai trò `ADMIN`.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Giám sát danh sách sự kiện, trạng thái tồn kho, danh sách booking toàn sàn.
  - [x] Giám sát hàng đợi Outbox Kafka, sự cố Dead Letter Queue và công cụ Replay sự kiện.
- **API Ánh xạ:** `GET /api/admin/events`, `GET /api/admin/bookings`, `GET /api/admin/operations/outbox`.
- **Dữ liệu SQL:** `ticketing.events`, `ticketing.bookings`, `ticketing.outbox_events`.

### ADMN-02: Quản Lý Người Dùng & Phân Quyền Đa Vai Trò (User Management) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Xem danh sách toàn bộ tài khoản đăng ký trong PostgreSQL, tìm kiếm theo Tên, Email, SĐT, phân trang.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Hiển thị đầy đủ thông tin: ID, Tên, Email, Số điện thoại, Vai trò, Ngày tạo.
  - [ ] Mở rộng gán vai trò: Cho phép Admin nâng quyền người dùng thành `ORGANIZER` hoặc `STAFF`.
  - [ ] Khóa / Mở khóa tài khoản khi phát hiện hành vi gian lận đầu cơ vé.
- **API Ánh xạ:** `GET /api/admin/users`, `PATCH /api/admin/users/{id}/roles`, `PATCH /api/admin/users/{id}/status`.
- **Dữ liệu SQL:** `ticketing.users`, `ticketing.user_roles`.

### ADMN-03: Kiểm Duyệt Sự Kiện Mới (Event Approval Workflow) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Duyệt các sự kiện do Organizer tạo trước khi chính thức xuất hiện trên trang chủ mở bán.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Admin có quyền Phê duyệt (`Approve`) -> Trạng thái chuyển sang `PUBLISHED`.
  - [ ] Admin có quyền Từ chối (`Reject`) kèm lý do gửi cho Organizer.
- **API Ánh xạ:** `POST /api/admin/events/{id}/approve`, `POST /api/admin/events/{id}/reject`.
- **Dữ liệu SQL:** `ticketing.events`.

### ADMN-04: Quản Lý Giao Dịch & Đối Soát Thanh Toán (Transaction Reconciliation) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Tra cứu danh sách toàn bộ các giao dịch qua Cổng thanh toán, đối soát trạng thái giữa Cổng và Database.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Đối chiếu mã tham chiếu giao dịch cổng (`vnp_TransactionNo`, `orderCode`) với `payment_intent_id`.
  - [ ] Phát hiện lệch tiền hoặc giao dịch treo để kích hoạt xử lý.
- **API Ánh xạ:** `GET /api/admin/payments`, `POST /api/admin/payments/{id}/reconcile`.
- **Dữ liệu SQL:** `ticketing.payments`, `ticketing.bookings`.

### ADMN-05: Trung Tâm Xử Lý Hoàn Tiền (Refund Center) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Quản lý danh sách các đơn hàng ở trạng thái `REFUND_REQUIRED` (do thanh toán muộn sau khi vé đã bị thu hồi hoặc do sự kiện bị hủy).
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [ ] Kích hoạt hoàn tiền tự động qua API cổng thanh toán hoặc đánh dấu đã hoàn tiền thủ công kèm mã chứng từ ngân hàng.
- **API Ánh xạ:** `GET /api/admin/refunds`, `POST /api/admin/refunds/{id}/process`.
- **Dữ liệu SQL:** `ticketing.payment_compensations`, `ticketing.compensation_attempts`.

### ADMN-06: Quản Lý Yêu Cầu Tư Vấn Doanh Nghiệp (Leads Management) `[HIỆN CÓ]`
- **Mức ưu tiên:** P0 (Must-Have)
- **Mô tả:** Xem danh sách khách hàng doanh nghiệp gửi yêu cầu liên hệ từ Landing Page.
- **Tiêu chuẩn chấp nhận (Acceptance Criteria):**
  - [x] Hiển thị Họ tên, Email, Số điện thoại, Tên công ty, Quy mô sự kiện, Nội dung tư vấn từ bảng `ticketing.contact_leads`.
- **API Ánh xạ:** `GET /api/admin/leads`.
- **Dữ liệu SQL:** `ticketing.contact_leads`.

### ADMN-07: Quản Lý Danh Mục Sự Kiện (Categories Management) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Thêm/sửa/xóa các danh mục chuẩn hóa: Âm nhạc & Concert, Nhà hát & Nghệ thuật, Hội nghị & Công nghệ, Thể thao.
- **API Ánh xạ:** `GET /api/admin/categories`, `POST /api/admin/categories`, `PATCH /api/admin/categories/{id}`.
- **Dữ liệu SQL:** `ticketing.categories`.

### ADMN-08: Quản Lý Địa Điểm & Mặt Bằng (Venue Management) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Quản lý kho các địa điểm mẫu trên toàn quốc kèm sức chứa và bản đồ số.
- **API Ánh xạ:** `GET /api/admin/venues`, `POST /api/admin/venues`.
- **Dữ liệu SQL:** `ticketing.venues`.

### ADMN-09: Quản Lý Chương Trình Khuyến Mãi (Promotion Management) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Admin tạo mã giảm giá toàn sàn hoặc hỗ trợ Organizer tạo voucher cho sự kiện riêng.
- **API Ánh xạ:** `POST /api/admin/promotions/vouchers`.
- **Dữ liệu SQL:** `ticketing.vouchers`.

### ADMN-10: Nhật Ký Kiểm Toán Hệ Thống (Audit Logs) `[MỚI ĐỀ XUẤT]`
- **Mức ưu tiên:** P1 (Should-Have)
- **Mô tả:** Ghi nhận mọi thao tác nhạy cảm: duyệt sự kiện, đổi quyền tài khoản, hoàn tiền, can thiệp tồn kho.
- **API Ánh xạ:** `GET /api/admin/audit-logs`.
- **Dữ liệu SQL:** `ticketing.audit_logs`.

---

## 6. MA TRẬN ĐỐI CHIẾU ĐỊNH NGHĨA HOÀN THÀNH (DEFINITION OF DONE - DOD)

Một tính năng trong Backlog V2 chỉ được coi là **DONE** khi đáp ứng đầy đủ 5 tiêu chí:
1. **Database:** Có migration Flyway hợp lệ (V11+), áp dụng thành công trên PostgreSQL, có index và foreign keys.
2. **Backend API:** Viết bằng Java 25 / Spring Boot 4.1.2, có validate input, có phân quyền server-side (RBAC/Event Scope), xử lý ngoại lệ theo chuẩn `ApiErrors`.
3. **Frontend UI:** Xây dựng bằng React 19 / TypeScript, responsive, có đủ trạng thái loading/error/empty, kết nối API thật.
4. **Automated Tests:** Có Unit Test & Integration Test kiểm chứng (JUnit 5 + MockMvc/RestAssured), test contract OpenAPI tương thích.
5. **Documentation & Evidence:** Được ghi chép trong tài liệu liên quan, có log kiểm chứng và ảnh chụp màn hình Chrome headless.
