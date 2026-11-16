# EVENTFLOW V2 — ĐỀ XUẤT ĐẶC TẢ HỢP ĐỒNG API (API RECOMMENDATION)
## RESTFUL API CONTRACT & OPENAPI 3.1 SPECIFICATION

> **Dự án:** EventFlow — High-Concurrency Event Ticketing Platform  
> **Chuyên gia phụ trách:** Principal Software Architect, Senior Full-stack Engineer, Security Architect  
> **Phiên bản:** V2.0-PROPOSAL · **Mã tài liệu:** `API-REC-V2-01`  
> **Định dạng:** OpenAPI 3.1 & RFC 7807 Problem Details  
> **Trạng thái:** Bản đề xuất kỹ thuật (Chờ Chủ dự án và Tech Lead phê duyệt)

---

## 1. NGUYÊN TẮC THIẾT KẾ & PHÂN LOẠI TRẠNG THÁI API

Hợp đồng API EventFlow V2 phân loại minh bạch trạng thái của từng endpoint:
1. `[HIỆN CÓ]`: API đã vận hành từ baseline v0.4 và đã có trong tài liệu hợp đồng cũ.
2. `[HIỆN CÓ NHƯNG CHƯA DOC]`: API đã được cài đặt trong mã nguồn backend thực tế ở giai đoạn P0/P1 nhưng chưa có trong tài liệu hợp đồng cũ.
3. `[MỚI ĐỀ XUẤT]`: API được thiết kế mới để đáp ứng đầy đủ nghiệp vụ thương mại V2 (Google OIDC, 4-Role RBAC, Organizer, Gateway Sandbox, Staff Check-in).
4. `[CHƯA PHÊ DUYỆT]`: Các API nâng cao (Voucher, Loyalty, Đa cổng thanh toán) chờ phê duyệt từ Chủ dự án.

### Tiêu Chuẩn HTTP Headers:
- `Authorization: Bearer <jwt_access_token>`: Bắt buộc cho mọi API yêu cầu xác thực.
- `Idempotency-Key: <uuid>`: Bắt buộc cho các API ghi nhận giao dịch tài chính hoặc kho: `POST /api/bookings`, `POST /api/payments/checkout`, `POST /api/staff/events/{eventId}/check-in`.
- `X-Correlation-Id: <uuid>`: Header truy vết phân tán xuyên suốt Frontend -> Backend -> Database -> Kafka.

### Định Dạng Lỗi Chuẩn (RFC 7807 Problem Details):
```json
{
  "type": "https://eventflow.vn/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Vé này đã được check-in lúc 19:40:05 tại Cổng A!",
  "instance": "/api/staff/events/c9f8a9cb/check-in",
  "code": "TICKET_ALREADY_USED",
  "timestamp": "2026-11-16T19:42:00Z"
}
```

---

## 2. DANH MỤC ENDPOINTS CHI TIẾT THEO PHÂN HỆ

### 2.1. Phân Hệ Xác Thực & Quản Lý Tài Khoản (Authentication & Account)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `POST` | `/api/auth/register` | `permitAll()` | Không | Đăng ký tài khoản email/mật khẩu, băm BCrypt, gán mặc định vai trò `CUSTOMER`. |
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `POST` | `/api/auth/login` | `permitAll()` | Không | Đăng nhập tài khoản, trả về JWT Access Token (15p) và gán HttpOnly Refresh Cookie (7 ngày). |
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `GET` | `/api/auth/me` | `Authenticated` | Không | Trả về thông tin danh tính, email, vai trò của người dùng hiện tại từ SecurityContext. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/auth/refresh` | `permitAll()` | Không | Luân chuyển Refresh Token Cookie; cấp Access Token mới; kiểm tra Token Family reuse. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/auth/logout` | `Authenticated` | Không | Thu hồi chuỗi Token Family hiện tại trong DB và xóa Refresh Cookie. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/auth/oauth2/google` | `permitAll()` | Không | Khởi tạo luồng Google OIDC redirect kèm CSRF state và PKCE challenge. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/auth/oauth2/callback/google` | `permitAll()` | Không | Tiếp nhận Authorization Code từ Google, trao đổi ID Token, tạo/liên kết tài khoản. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/auth/forgot-password` | `permitAll()` | Không | Gửi email chứa mã reset mật khẩu một lần (TTL 15 phút). |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/auth/reset-password` | `permitAll()` | Không | Xác minh token băm SHA-256 và đặt mật khẩu mới. |
| `[MỚI ĐỀ XUẤT]` | `PUT` | `/api/me/profile` | `Authenticated` | Không | Cập nhật họ tên hiển thị, số điện thoại liên hệ cá nhân. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/me/change-password` | `Authenticated` | Không | Đổi mật khẩu (xác minh mật khẩu cũ trước khi đổi). |

---

### 2.2. Phân Hệ Khám Phá & Danh Mục Sự Kiện (Public Catalog)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[HIỆN CÓ]` | `GET` | `/api/events` | `permitAll()` | Không | Danh sách sự kiện mở bán công khai (`status = 'PUBLISHED'`). Lọc theo search, category, city. |
| `[HIỆN CÓ]` | `GET` | `/api/events/{id}` | `permitAll()` | Không | Chi tiết sự kiện: thời gian, địa điểm, mô tả, danh mục các loại vé. |
| `[HIỆN CÓ]` | `GET` | `/api/events/{id}/availability` | `permitAll()` | Không | Snapshot tồn kho vé GA và danh sách ghế AVAILABLE/HELD/SOLD theo thời gian thực. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/categories` | `permitAll()` | Không | Danh mục thể loại sự kiện (Concert, Kịch, Hội nghị...). |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/venues` | `permitAll()` | Không | Danh mục địa điểm biểu diễn chuẩn hóa kèm sức chứa tối đa. |

---

### 2.3. Phân Hệ Đặt Vé Khách Hàng (Customer Bookings)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[HIỆN CÓ]` | `POST` | `/api/bookings` | `hasRole('CUSTOMER')` | Không | Giữ chỗ vé GA hoặc ghế chỉ định (All-or-Nothing ACID). Yêu cầu `Idempotency-Key`. |
| `[HIỆN CÓ]` | `GET` | `/api/bookings/{id}` | `hasRole('CUSTOMER')` | Own Data | Tra cứu chi tiết đơn hàng (chỉ chủ sở hữu đơn hoặc Admin mới đọc được). |
| `[HIỆN CÓ]` | `POST` | `/api/bookings/{id}/cancel` | `hasRole('CUSTOMER')` | Own Data | Khách chủ động hủy đơn hàng đang giữ chỗ, nhả kho vé ngay lập tức. |
| `[HIỆN CÓ]` | `GET` | `/api/me/bookings` | `hasRole('CUSTOMER')` | Own Data | Lấy danh sách toàn bộ lịch sử đơn hàng và vé điện tử của tài khoản đăng nhập. |
| `[HIỆN CÓ]` | `GET` | `/api/bookings/{id}/tickets` | `hasRole('CUSTOMER')` | Own Data | Xuất danh sách vé điện tử (E-tickets) kèm mã định danh sau khi đơn `CONFIRMED`. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/tickets/{id}/qr` | `hasRole('CUSTOMER')` | Own Data | Sinh mã QR động có chữ ký số HMAC-SHA256 phục vụ soát vé tại cổng. |

---

### 2.4. Phân Hệ Thanh Toán & Hoàn Tiền (Payment Gateway & Refunds)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[HIỆN CÓ]` | `POST` | `/api/bookings/{id}/simulate-payment` | `hasRole('CUSTOMER')` | Own Data | Trình mô phỏng thanh toán nội bộ phục vụ CI/CD và automated test suite. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/payments/checkout` | `hasRole('CUSTOMER')` | Own Data | Khởi tạo giao dịch thanh toán qua cổng Sandbox (VNPAY); trả về URL ký số. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/payments/return` | `permitAll()` | Không | Tiếp nhận redirect của khách hàng từ cổng thanh toán; chỉ render thông báo UI. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/payments/vnpay/ipn` | `permitAll()` | Không | **Webhook IPN (Server-to-Server):** Tiếp nhận kết quả thanh toán từ cổng, xác minh chữ ký HMAC-SHA512, cập nhật đơn hàng an toàn. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/bookings/{id}/payment-status` | `hasRole('CUSTOMER')` | Own Data | Polling kiểm tra trạng thái thanh toán đơn hàng theo thời gian thực. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/admin/refunds/{id}/process` | `hasRole('ADMIN')` | Toàn cục | Kích hoạt lệnh hoàn tiền tự động qua API cổng đối tác cho đơn thanh toán trễ. |

---

### 2.5. Phân Hệ Quản Trị Nhà Tổ Chức (Organizer Management)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/organizer/events` | `hasRole('ORGANIZER')` | Owner Only | Danh sách các sự kiện do chính nhà tổ chức đăng nhập sở hữu. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/organizer/events` | `hasRole('ORGANIZER')` | Owner Only | Khởi tạo sự kiện mới ở trạng thái `DRAFT`. |
| `[MỚI ĐỀ XUẤT]` | `PUT` | `/api/organizer/events/{id}` | `hasRole('ORGANIZER')` | Owner Only | Chỉnh sửa thông tin sự kiện (thời gian, địa điểm, mô tả, poster). |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/organizer/events/{id}/publish` | `hasRole('ORGANIZER')` | Owner Only | Xuất bản sự kiện ra Public Catalog sau khi cấu hình đủ vé/ghế. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/organizer/events/{id}/ticket-types` | `hasRole('ORGANIZER')` | Owner Only | Thiết lập loại vé GA hoặc định nghĩa sơ đồ ghế khán phòng. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/organizer/events/{id}/staff` | `hasRole('ORGANIZER')` | Owner Only | Phân công tài khoản nhân viên (Staff) phụ trách soát vé sự kiện. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/organizer/events/{id}/reports` | `hasRole('ORGANIZER')` | Owner Only | Báo cáo doanh số bán vé, tỷ lệ lấp đầy khán phòng và dòng tiền theo sự kiện. |

---

### 2.6. Phân Hệ Soát Vé Của Nhân Viên (Staff Check-in)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/staff/events` | `hasRole('STAFF')` | Assigned Events | Danh sách các sự kiện mà nhân viên hiện tại được phân công phụ trách. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/staff/events/{eventId}/check-in` | `hasRole('STAFF')` | Assigned Events | Quét mã QR soát vé; xác thực chữ ký số; ghi nhận nguyên tử chống check-in trùng (`UNIQUE(ticket_id)`). Yêu cầu `Idempotency-Key`. |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/staff/events/{eventId}/stats` | `hasRole('STAFF')` | Assigned Events | Thống kê số lượng khách đã check-in / tổng số vé đã phát hành của sự kiện. |

---

### 2.7. Phân Hệ Quản Trị Hệ Thống Toàn Nền Tảng (Admin Portal)

| Trạng thái | Method | Endpoint | Quyền hạn (Role) | Event Scope | Chức năng nghiệp vụ |
|---|---|---|---|---|---|
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `GET` | `/api/admin/users` | `hasRole('ADMIN')` | Toàn cục | Danh sách người dùng hệ thống có phân trang và tìm kiếm theo họ tên, email. |
| `[MỚI ĐỀ XUẤT]` | `POST` | `/api/admin/users/{id}/roles` | `hasRole('ADMIN')` | Toàn cục | Cấp hoặc thu hồi vai trò (`STAFF`, `ORGANIZER`, `ADMIN`). Ghi nhật ký audit log. |
| `[MỚI ĐỀ XUẤT]` | `PUT` | `/api/admin/users/{id}/status` | `hasRole('ADMIN')` | Toàn cục | Khóa hoặc kích hoạt lại tài khoản người dùng (`ACTIVE`, `LOCKED`). |
| `[HIỆN CÓ]` | `GET` | `/api/admin/events` | `hasRole('ADMIN')` | Toàn cục | Xem toàn bộ sự kiện trên nền tảng bất kể trạng thái hay người tạo. |
| `[HIỆN CÓ]` | `POST` | `/api/admin/events` | `hasRole('ADMIN')` | Toàn cục | Tạo sự kiện ở cấp quản trị toàn cục. |
| `[HIỆN CÓ]` | `PATCH` | `/api/admin/events/{id}` | `hasRole('ADMIN')` | Toàn cục | Điều chỉnh sức chứa hoặc chuyển trạng thái sự kiện. |
| `[HIỆN CÓ]` | `GET` | `/api/admin/bookings` | `hasRole('ADMIN')` | Toàn cục | Xem toàn bộ đơn hàng trên hệ thống, lọc theo trạng thái (`PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`). |
| `[HIỆN CÓ]` | `POST` | `/api/admin/outbox/replay` | `hasRole('ADMIN')` | Toàn cục | Kích hoạt replay sự kiện outbox bị lỗi với số lần giới hạn (tối đa 3 lần). |
| `[MỚI ĐỀ XUẤT]` | `GET` | `/api/admin/audit-logs` | `hasRole('ADMIN')` | Toàn cục | Tra cứu lịch sử thao tác nhạy cảm trong hệ thống. |
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `POST` | `/api/contact/leads` | `permitAll()` | Không | Tiếp nhận biểu mẫu đăng ký tư vấn đối tác tổ chức sự kiện. |
| `[HIỆN CÓ NHƯNG CHƯA DOC]` | `GET` | `/api/admin/leads` | `hasRole('ADMIN')` | Toàn cục | Quản lý danh sách đối tác đăng ký tư vấn từ bảng `contact_leads`. |

---

## 3. ĐẶC TẢ CHI TIẾT REQUEST & RESPONSE CỦA CÁC LUỒNG TRỌNG YẾU

### 3.1. Khởi Tạo Thanh Toán Cổng Đối Tác: `POST /api/payments/checkout`
- **Request Headers:**
  - `Authorization: Bearer <access_token>`
  - `Idempotency-Key: e8c0b2d4-7a3f-4e9b-81d2-098234761234`
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
    "gatewayUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=150000000&vnp_Command=pay&vnp_CreateDate=20261116152000&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+EventFlow&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fpayment-return&vnp_TmnCode=DEMO_TMN&vnp_TxnRef=EVF_c9f8a9cb_1700000000&vnp_Version=2.1.0&vnp_SecureHash=3f4a...",
    "expiresAt": "2026-11-16T15:30:00Z"
  }
  ```

---

### 3.2. Webhook IPN Server-to-Server: `GET /api/payments/vnpay/ipn`
- **Request Query Parameters từ Cổng VNPAY:**
  `?vnp_Amount=150000000&vnp_BankCode=NCB&vnp_CardType=ATM&vnp_OrderInfo=Thanh+toan+ve+EventFlow&vnp_PayDate=20261116152500&vnp_ResponseCode=00&vnp_TmnCode=DEMO_TMN&vnp_TransactionNo=14598234&vnp_TransactionStatus=00&vnp_TxnRef=EVF_c9f8a9cb_1700000000&vnp_SecureHash=...`
- **Xử lý Backend:**
  1. Tính HMAC-SHA512 trên chuỗi raw param với bí mật `vnp_HashSecret`. So khớp với `vnp_SecureHash`. Nếu sai chữ ký -> Trả về `{"RspCode":"97","Message":"Invalid Checksum"}`.
  2. Tra cứu `merchantRef` trong `ticketing.payment_attempts`. Nếu không thấy -> Trả về `{"RspCode":"01","Message":"Order not found"}`.
  3. Kiểm tra số tiền `vnp_Amount / 100 == attempt.amount_vnd`. Nếu không khớp -> Trả về `{"RspCode":"04","Message":"Invalid Amount"}`.
  4. Nếu đơn hàng đã hoàn tất trước đó (`status = 'CONFIRMED'`) -> Trả về `{"RspCode":"02","Message":"Order already confirmed"}` (Idempotent OK).
  5. Nếu đơn hàng đã quá hạn (`committed_expiry_at < now()`) -> Chuyển sang `REFUND_REQUIRED` và trả về `{"RspCode":"00","Message":"Confirm Success"}`.
  6. Nếu hợp lệ trong thời hạn giữ vé -> Cập nhật `CONFIRMED`, phát hành vé điện tử trong Transaction ACID, gửi event Kafka Outbox và trả về `{"RspCode":"00","Message":"Confirm Success"}`.

---

### 3.3. Soát Vé Tại Cổng: `POST /api/staff/events/{eventId}/check-in`
- **Request Headers:**
  - `Authorization: Bearer <staff_jwt_token>`
  - `Idempotency-Key: d2e4f6a8-1b3c-5e7d-9a0f-123456789abc`
- **Request Body:**
  ```json
  {
    "qrToken": "c9f8a9cb.e7d6c5b4.1700000000.a1b2c3d4e5f6...",
    "gateName": "GATE_A_VIP"
  }
  ```
- **Trường Hợp Hợp Lệ (HTTP 200 OK):**
  ```json
  {
    "status": "CHECKED_IN",
    "ticketId": "e7d6c5b4-0000-4000-8000-000000000001",
    "ticketCode": "TKT-SEATED-A1",
    "attendeeName": "Nguyễn Văn A",
    "ticketType": "VIP Khán Phòng",
    "seatLabel": "A-12",
    "checkedInAt": "2026-11-16T19:45:12Z",
    "gate": "GATE_A_VIP"
  }
  ```
- **Trường Hợp Quét Trùng Lần 2 (HTTP 409 Conflict):**
  ```json
  {
    "status": "ALREADY_CHECKED_IN",
    "code": "TICKET_ALREADY_USED",
    "message": "Vé này đã được quét vào lúc 19:40:05 tại cổng GATE_B!",
    "firstCheckInAt": "2026-11-16T19:40:05Z",
    "firstGate": "GATE_B"
  }
  ```
- **Trường Hợp Sai Sự Kiện / Không Có Quyền (HTTP 403 Forbidden):**
  ```json
  {
    "status": "FORBIDDEN",
    "code": "EVENT_SCOPE_DENIED",
    "message": "Nhân viên không được phân công phụ trách soát vé sự kiện này!"
  }
  ```
