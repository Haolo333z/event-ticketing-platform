# EVENTFLOW V2 — ĐẶC TẢ HỢP ĐỒNG GIAO DIỆN LẬP TRÌNH (API CONTRACT V2)

> **Phiên bản:** 2.0.0 · **Mã tài liệu:** `API-CONTRACT-V2-T11.2`  
> **Tác giả:** Principal Software Architect, Senior Security Architect, Senior Full-stack Engineer  
> **Trạng thái:** Đặc tả phê duyệt hợp đồng API (Approved API Contract Specification)  
> **Định dạng:** RESTful JSON API theo chuẩn OpenAPI 3.1.

---

## 1. NGUYÊN TẮC THIẾT KẾ & PHÂN LOẠI API

Toàn bộ các API trong EventFlow V2 được phân loại rõ ràng thành 3 nhóm trạng thái:
1. `[EXISTING]`: API đã có trong baseline v0.4 và đã được ghi nhận trong `docs/contracts/openapi.json`.
2. `[EXISTING_UNDOCUMENTED]`: API đã được triển khai trong mã nguồn thực tế ở giai đoạn P0/P1 nhưng chưa được cập nhật vào file `openapi.json` cũ.
3. `[PROPOSED_V2]`: API thiết kế mới phục vụ các tính năng V2 (Google OIDC, 4-Role RBAC, Organizer, Gateway Sandbox, Staff Check-in).

### Quy định chung về Headers:
- `Authorization: Bearer <jwt_access_token>`: Bắt buộc cho mọi endpoint yêu cầu xác thực.
- `Idempotency-Key: <uuid>`: Bắt buộc cho các thao tác đặt vé (`POST /api/bookings`), khởi tạo thanh toán (`POST /api/payments/checkout`), hoàn tiền và soát vé.
- `X-Correlation-Id: <uuid>`: Header truy vết log xuyên suốt các tầng.

---

## 2. DANH MỤC API THEO MODULE

### 2.1. Module Xác Thực & Tài Khoản (Authentication & Account)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[EXISTING_UNDOCUMENTED]` | `POST` | `/api/auth/register` | `permitAll()` | Không | Đăng ký tài khoản email/mật khẩu, băm BCrypt, gán mặc định vai trò `CUSTOMER`. |
| `[EXISTING_UNDOCUMENTED]` | `POST` | `/api/auth/login` | `permitAll()` | Không | Đăng nhập bằng email/password, trả về Access Token và gán Refresh Token Cookie. |
| `[EXISTING_UNDOCUMENTED]` | `GET` | `/api/auth/me` | `Authenticated` | Không | Lấy thông tin tài khoản hiện tại từ Principal đã xác thực. |
| `[PROPOSED_V2]` | `POST` | `/api/auth/refresh` | `permitAll()` | Không | Luân chuyển Refresh Token lấy Access Token mới; kiểm tra Token Family reuse. |
| `[PROPOSED_V2]` | `POST` | `/api/auth/logout` | `Authenticated` | Không | Thu hồi chuỗi refresh token hiện tại và xóa Cookie phía client. |
| `[PROPOSED_V2]` | `GET` | `/api/auth/oauth2/google` | `permitAll()` | Không | Khởi tạo luồng Google OIDC redirect kèm state và PKCE. |
| `[PROPOSED_V2]` | `GET` | `/api/auth/oauth2/callback/google` | `permitAll()` | Không | Xử lý redirect từ Google, xác minh Authorization Code, tạo hoặc liên kết tài khoản. |
| `[PROPOSED_V2]` | `POST` | `/api/auth/forgot-password` | `permitAll()` | Không | Gửi email chứa token đặt lại mật khẩu sử dụng 1 lần. |
| `[PROPOSED_V2]` | `POST` | `/api/auth/reset-password` | `permitAll()` | Không | Đặt lại mật khẩu mới với token băm SHA-256. |
| `[PROPOSED_V2]` | `PUT` | `/api/me/profile` | `Authenticated` | Không | Cập nhật họ tên, số điện thoại người dùng. |
| `[PROPOSED_V2]` | `POST` | `/api/me/change-password` | `Authenticated` | Không | Đổi mật khẩu tài khoản (yêu cầu mật khẩu cũ). |
| `[PROPOSED_V2]` | `POST` | `/api/me/external-identities/google/link` | `Authenticated` | Không | Liên kết tài khoản hiện tại với Google sau khi xác thực danh tính. |
| `[PROPOSED_V2]` | `DELETE` | `/api/me/external-identities/google` | `Authenticated` | Không | Hủy liên kết Google (chặn nếu không còn mật khẩu dự phòng). |

---

### 2.2. Module Danh Mục Công Khai (Public Catalog)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[EXISTING]` | `GET` | `/api/events` | `permitAll()` | Không | Danh sách sự kiện mở bán (chỉ trả về sự kiện có `status = 'PUBLISHED'`). Hỗ trợ query params: `search`, `category`, `page`, `size`. |
| `[EXISTING]` | `GET` | `/api/events/{id}` | `permitAll()` | Không | Chi tiết sự kiện công khai (thông tin, địa điểm, các loại vé). |
| `[EXISTING]` | `GET` | `/api/events/{id}/availability` | `permitAll()` | Không | Snapshot tồn kho vé GA và danh sách ghế AVAILABLE/HELD/SOLD realtime. |
| `[PROPOSED_V2]` | `GET` | `/api/categories` | `permitAll()` | Không | Danh sách các thể loại sự kiện (Concert, Kịch, Hội nghị...). |
| `[PROPOSED_V2]` | `GET` | `/api/venues` | `permitAll()` | Không | Danh mục địa điểm biểu diễn công khai kèm sức chứa. |

---

### 2.3. Module Đặt Vé Khách Hàng (Customer Bookings)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[EXISTING]` | `POST` | `/api/bookings` | `hasRole('CUSTOMER')` | Không | Tạo yêu cầu giữ chỗ vé GA hoặc Vé Ghế chỉ định (All-or-Nothing, Row-lock ACID). Bắt buộc `Idempotency-Key`. |
| `[EXISTING]` | `GET` | `/api/bookings/{id}` | `hasRole('CUSTOMER')` | Own Data | Tra cứu chi tiết đơn hàng (chỉ chủ sở hữu hoặc Admin mới đọc được). |
| `[EXISTING]` | `POST` | `/api/bookings/{id}/cancel` | `hasRole('CUSTOMER')` | Own Data | Hủy đơn hàng đang trong trạng thái `PENDING_PAYMENT`, nhả kho tức thì. |
| `[EXISTING]` | `GET` | `/api/me/bookings` | `hasRole('CUSTOMER')` | Own Data | Danh sách toàn bộ đơn hàng và vé đã mua của tài khoản đăng nhập. |
| `[EXISTING]` | `GET` | `/api/bookings/{id}/tickets` | `hasRole('CUSTOMER')` | Own Data | Xuất danh sách vé điện tử kèm mã vé khi đơn hàng `CONFIRMED`. |
| `[PROPOSED_V2]` | `GET` | `/api/tickets/{id}/qr` | `hasRole('CUSTOMER')` | Own Data | Trả về chuỗi QR Token có chữ ký số HMAC-SHA256 phục vụ soát vé. |

---

### 2.4. Module Cổng Thanh Toán & Hoàn Tiền (Payment Gateway & Refunds)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[EXISTING]` | `POST` | `/api/bookings/{id}/simulate-payment` | `hasRole('CUSTOMER')` | Own Data | Mô phỏng thanh toán (Simulator Adapter) phục vụ bộ test tự động và CI/CD. |
| `[PROPOSED_V2]` | `POST` | `/api/payments/checkout` | `hasRole('CUSTOMER')` | Own Data | Khởi tạo thanh toán với cổng Sandbox (VNPAY); trả về `paymentUrl` đã ký số. Bắt buộc `Idempotency-Key`. |
| `[PROPOSED_V2]` | `GET` | `/api/payments/return` | `permitAll()` | Không | Endpoint đón tiếp trình duyệt khách hàng quay về sau khi thanh toán trên cổng; chỉ đọc trạng thái, không cấp vé. |
| `[PROPOSED_V2]` | `GET` | `/api/payments/vnpay/ipn` | `permitAll()` | Không | **Server-to-Server Webhook IPN Callback**: Tiếp nhận kết quả thanh toán từ VNPAY, xác thực chữ ký SHA-256, xử lý idempotent. |
| `[PROPOSED_V2]` | `GET` | `/api/bookings/{id}/payment-status` | `hasRole('CUSTOMER')` | Own Data | Polling tra cứu trạng thái thanh toán đơn hàng realtime. |
| `[PROPOSED_V2]` | `POST` | `/api/admin/refunds/{id}/process` | `hasRole('ADMIN')` | Toàn cục | Kích hoạt gọi API hoàn tiền sang cổng đối tác cho giao dịch `REFUND_REQUIRED`. |

---

### 2.5. Module Quản Trị Sự Kiện Của Nhà Tổ Chức (Organizer Management)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[PROPOSED_V2]` | `GET` | `/api/organizer/events` | `hasRole('ORGANIZER')` | Owner Only | Danh sách các sự kiện thuộc quyền sở hữu của Organizer hiện tại. |
| `[PROPOSED_V2]` | `POST` | `/api/organizer/events` | `hasRole('ORGANIZER')` | Owner Only | Tạo sự kiện mới ở trạng thái `DRAFT`. |
| `[PROPOSED_V2]` | `PUT` | `/api/organizer/events/{id}` | `hasRole('ORGANIZER')` | Owner Only | Cập nhật thông tin sự kiện (chặn nếu không phải chủ sở hữu). |
| `[PROPOSED_V2]` | `POST` | `/api/organizer/events/{id}/publish` | `hasRole('ORGANIZER')` | Owner Only | Xuất bản sự kiện lên Public Catalog (yêu cầu cấu hình đủ vé/ghế). |
| `[PROPOSED_V2]` | `POST` | `/api/organizer/events/{id}/ticket-types` | `hasRole('ORGANIZER')` | Owner Only | Cấu hình loại vé GA hoặc Sơ đồ ghế khán phòng. |
| `[PROPOSED_V2]` | `POST` | `/api/organizer/events/{id}/staff` | `hasRole('ORGANIZER')` | Owner Only | Phân công nhân viên (Staff) phụ trách soát vé sự kiện. |
| `[PROPOSED_V2]` | `GET` | `/api/organizer/events/{id}/reports` | `hasRole('ORGANIZER')` | Owner Only | Báo cáo doanh số, số vé đã bán và tỷ lệ lấp đầy theo sự kiện. |

---

### 2.6. Module Soát Vé Của Nhân Viên (Staff Check-in)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[PROPOSED_V2]` | `GET` | `/api/staff/events` | `hasRole('STAFF')` | Assigned Events | Danh sách các sự kiện mà nhân viên hiện tại được phân công phụ trách. |
| `[PROPOSED_V2]` | `POST` | `/api/staff/events/{eventId}/check-in` | `hasRole('STAFF')` | Assigned Events | Quét mã QR, xác minh chữ ký HMAC, thực hiện ghi nhận check-in nguyên tử (`UNIQUE(ticket_id)`). Bắt buộc `Idempotency-Key`. |
| `[PROPOSED_V2]` | `GET` | `/api/staff/events/{eventId}/stats` | `hasRole('STAFF')` | Assigned Events | Thống kê số lượng khách đã check-in / tổng số vé đã phát hành. |

---

### 2.7. Module Quản Trị Hệ Thống Toàn Nền Tảng (Admin Portal)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Mô tả chức năng |
|---|---|---|---|---|---|
| `[EXISTING_UNDOCUMENTED]` | `GET` | `/api/admin/users` | `hasRole('ADMIN')` | Toàn cục | Danh sách người dùng hệ thống có phân trang và tìm kiếm theo tên, email, SĐT. |
| `[PROPOSED_V2]` | `POST` | `/api/admin/users/{id}/roles` | `hasRole('ADMIN')` | Toàn cục | Gán hoặc thu hồi vai trò (`STAFF`, `ORGANIZER`, `ADMIN`). Ghi audit log. |
| `[PROPOSED_V2]` | `PUT` | `/api/admin/users/{id}/status` | `hasRole('ADMIN')` | Toàn cục | Cập nhật trạng thái tài khoản (`ACTIVE`, `LOCKED`, `DISABLED`). |
| `[EXISTING]` | `GET` | `/api/admin/events` | `hasRole('ADMIN')` | Toàn cục | Xem toàn bộ sự kiện trên nền tảng. |
| `[EXISTING]` | `POST` | `/api/admin/events` | `hasRole('ADMIN')` | Toàn cục | Tạo sự kiện toàn cục. |
| `[EXISTING]` | `PATCH` | `/api/admin/events/{id}` | `hasRole('ADMIN')` | Toàn cục | Cập nhật sức chứa, trạng thái sự kiện. |
| `[EXISTING]` | `GET` | `/api/admin/bookings` | `hasRole('ADMIN')` | Toàn cục | Danh sách đơn hàng toàn hệ thống có lọc theo trạng thái. |
| `[EXISTING]` | `POST` | `/api/admin/outbox/replay` | `hasRole('ADMIN')` | Toàn cục | Kích hoạt replay sự kiện outbox bounded (tối đa 3 lần). |
| `[PROPOSED_V2]` | `GET` | `/api/admin/audit-logs` | `hasRole('ADMIN')` | Toàn cục | Tra cứu nhật ký kiểm toán hệ thống. |
| `[EXISTING_UNDOCUMENTED]` | `POST` | `/api/contact/leads` | `permitAll()` | Không | Tiếp nhận đăng ký tư vấn đối tác tổ chức sự kiện. |
| `[EXISTING_UNDOCUMENTED]` | `GET` | `/api/admin/leads` | `hasRole('ADMIN')` | Toàn cục | Xem danh sách leads đối tác tư vấn từ bảng `ticketing.contact_leads`. |

---

## 3. CHI TIẾT CÁC SCHEMA REQUEST / RESPONSE TRỌNG YẾU

### 3.1. Khởi Tạo Thanh Toán Gateway: `POST /api/payments/checkout`
- **Request Body:**
  ```json
  {
    "bookingId": "c9f8a9cb-bb96-4f83-b768-099d0e723d11",
    "provider": "VNPAY",
    "returnUrl": "http://localhost:5173/payment-return"
  }
  ```
- **Response (HTTP 201 Created):**
  ```json
  {
    "attemptId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "merchantRef": "EVF_c9f8a9cb_1700000000",
    "amountVnd": 1500000,
    "currency": "VND",
    "gatewayUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=150000000&vnp_Command=pay&vnp_CreateDate=20261116152000&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+EventFlow&vnp_OrderType=billpayment&vnp_ReturnUrl=...&vnp_TmnCode=...&vnp_TxnRef=EVF_c9f8a9cb_1700000000&vnp_Version=2.1.0&vnp_SecureHash=3f4a...",
    "expiresAt": "2026-11-16T15:30:00Z"
  }
  ```

### 3.2. Soát Vé Nguyên Tử: `POST /api/staff/events/{eventId}/check-in`
- **Request Body:**
  ```json
  {
    "qrToken": "c9f8a9cb.e7d6c5b4.1700000000.a1b2c3d4e5f6...",
    "gateName": "GATE_A_VIP"
  }
  ```
- **Response Thành Công (HTTP 200 OK):**
  ```json
  {
    "status": "CHECKED_IN",
    "ticketId": "e7d6c5b4-0000-4000-8000-000000000001",
    "ticketCode": "TKT-GA-0001",
    "attendeeName": "Nguyễn Văn A",
    "ticketType": "VIP Sân Khấu",
    "seatLabel": "VIP-A1",
    "checkedInAt": "2026-11-16T19:45:12Z",
    "gate": "GATE_A_VIP"
  }
  ```
- **Response Quét Trùng (HTTP 409 Conflict):**
  ```json
  {
    "status": "ALREADY_CHECKED_IN",
    "code": "TICKET_ALREADY_USED",
    "message": "Vé này đã được quét vào lúc 19:40:05 tại cổng GATE_B!",
    "firstCheckInAt": "2026-11-16T19:40:05Z",
    "firstGate": "GATE_B"
  }
  ```
