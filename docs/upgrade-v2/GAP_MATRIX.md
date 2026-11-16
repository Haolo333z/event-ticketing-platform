# GAP MATRIX — Ma Trận Đối Soát Yêu Cầu Nâng Cấp V2

> Ngày thực hiện: **2026-11-16 (Asia/Saigon)**  
> Căn cứ đối chiếu: Toàn bộ danh mục yêu cầu trong [UPGRADE_REQUIREMENTS_V2.md](UPGRADE_REQUIREMENTS_V2.md) và kết quả kiểm toán mã nguồn thực tế tại [SOURCE_REBASELINE.md](SOURCE_REBASELINE.md).

---

## 1. Định Nghĩa 4 Trạng Thái Đánh Giá

1. **`IMPLEMENTED_AND_VERIFIED`**: Tính năng đã được cài đặt hoàn chỉnh ở cả tầng backend/SQL/frontend và đã có kiểm thử tự động (Unit / Integration / Contract) chạy PASS thực tế.
2. **`IMPLEMENTED_BUT_NOT_VERIFIED`**: Mã nguồn đã có đầy đủ logic nhưng chưa có bộ test tự động chuyên biệt hoặc mới chỉ kiểm chứng qua thao tác thủ công (manual curl / API test).
3. **`PARTIALLY_IMPLEMENTED`**: Đã có một phần mã nguồn (ví dụ: đã có bảng DB hoặc giao diện Frontend, hoặc API cơ bản) nhưng còn thiếu các yêu cầu cốt lõi theo tiêu chuẩn V2 (ví dụ: thiếu refresh token rotation, thiếu event scope, thiếu audit log).
4. **`NOT_IMPLEMENTED`**: Tính năng hoàn toàn chưa có trong mã nguồn hoặc chưa có bảng dữ liệu tương ứng.

---

## 2. Ma Trận Đối Soát Chi Tiết Từng Yêu Cầu

### 2.1. Authentication & Account (AUTH-001 → AUTH-015)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **AUTH-001** | Đăng ký email / password thật | **PARTIALLY_IMPLEMENTED** | `AuthController.java` (`POST /api/auth/register`), `V10__user_authentication.sql` (`users.email`, `password_hash`), `AuthModal.ts` | Manual API verify (HTTP 201), duplicate email (HTTP 400). Chưa có JUnit test tự động trong Maven suite. | Thiếu email verification token, thiếu password complexity policy nâng cao, thiếu audit log. |
| **AUTH-002** | Đăng nhập email / password | **PARTIALLY_IMPLEMENTED** | `AuthController.java` (`POST /api/auth/login`), `BCryptPasswordEncoder`, `JwtService.java`, `AuthModal.ts` | Manual API verify (HTTP 200 kèm JWT token), wrong pass (HTTP 401). | Thiếu rate limiting / brute-force protection, thiếu cơ chế lockout tài khoản tạm thời. |
| **AUTH-003** | Đăng xuất | **PARTIALLY_IMPLEMENTED** | `DemoShell.ts` (`handleLogout` xóa token khỏi `localStorage`) | UI clear token hoạt động trong visual QA. | Chưa có cơ chế vô hiệu hóa / blacklist token hoặc revoke session ở server-side. |
| **AUTH-004** | Refresh session / token rotation | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có bảng `refresh_tokens`, chưa có logic token family / reuse detection. |
| **AUTH-005** | Google OIDC login | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có Spring Security OAuth2 Client config, chưa có endpoint tiếp nhận Google Authorization Code. |
| **AUTH-006** | Tạo account Google lần đầu qua `sub` | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có bảng `external_identities(provider, provider_subject)`. |
| **AUTH-007** | Liên kết tài khoản Google (Link) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có API và luồng re-auth để liên kết an toàn. |
| **AUTH-008** | Hủy liên kết Google (Unlink) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có logic chặn hủy phương thức đăng nhập duy nhất. |
| **AUTH-009** | Xem / cập nhật hồ sơ cá nhân | **PARTIALLY_IMPLEMENTED** | `AuthController.java` (`GET /api/auth/me`), `DemoPrincipal` | API trả về thông tin user thật HTTP 200. | Chưa có endpoint cập nhật thông tin (`PUT /api/auth/me`). |
| **AUTH-010** | Đổi mật khẩu | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có endpoint `POST /api/auth/change-password` và cơ chế xác thực mật khẩu cũ. |
| **AUTH-011** | Quên / đặt lại mật khẩu | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có bảng `password_reset_tokens` và dịch vụ gửi mã reset. |
| **AUTH-012** | Xác thực email (Email verification) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có bảng `email_verification_tokens` và email adapter. |
| **AUTH-013** | Trạng thái tài khoản (ACTIVE/LOCKED/DISABLED) | **NOT_IMPLEMENTED** | Bảng `ticketing.users` chưa có cột `status` | Chưa có | Cần thêm cột `status` vào `users` và kiểm tra chặn đăng nhập khi LOCKED/DISABLED. |
| **AUTH-014** | Bootstrap tài khoản Admin an toàn | **PARTIALLY_IMPLEMENTED** | V10 seed sẵn `admin@eventflow.vn` / `Admin@123` băm BCrypt | Khởi tạo thành công trong Flyway V10. | Chưa có dynamic bootstrap script / CLI / rotate admin credentials. |
| **AUTH-015** | Bảo vệ Mock Identity chỉ chạy local/test | **IMPLEMENTED_AND_VERIFIED** | `DemoSettings.java`, `MockBearerFilter.java`, `DemoAuthConfigurationTest.java` | 3 tests trong `DemoAuthConfigurationTest` PASS. Tự fail startup nếu chạy ngoài local/test. | Hoàn tất; cần giữ vững trong các profile tương lai. |

---

### 2.2. Four-Role RBAC (RBAC-001 → RBAC-010)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **RBAC-001** | CUSTOMER là vai trò mặc định | **IMPLEMENTED_BUT_NOT_VERIFIED** | `AuthController.java` (dòng 55: gán cứng `'CUSTOMER'`), `ticketing.users.demo_role` | Manual registration verify tạo đúng role `CUSTOMER`. Chưa có automated test chuyên biệt trong Maven suite kiểm tra role gán qua API đăng ký. | Cần chuyển sang mô hình quan hệ chuẩn `roles` / `user_roles` và bổ sung automated test. |
| **RBAC-002** | STAFF role | **NOT_IMPLEMENTED** | DB constraint `demo_role` chỉ cho phép `CUSTOMER/ADMIN` | Chưa có | Cần mở rộng bảng `roles` bổ sung code `STAFF` và API quản lý staff. |
| **RBAC-003** | ORGANIZER role | **NOT_IMPLEMENTED** | DB constraint `demo_role` chỉ cho phép `CUSTOMER/ADMIN` | Chưa có | Cần bổ sung role `ORGANIZER` và luồng cấp quyền của Admin. |
| **RBAC-004** | ADMIN role | **PARTIALLY_IMPLEMENTED** | `SecurityConfiguration.java` (`hasRole('ADMIN')`), `DemoPrincipal` | Kiểm tra phân quyền `/api/admin/**` chặn 403 đối với Customer. | Chưa có quản lý phân quyền Admin qua bảng quan hệ `user_roles`. |
| **RBAC-005** | Hỗ trợ gán nhiều role cho 1 tài khoản | **NOT_IMPLEMENTED** | `ticketing.users` chỉ có 1 cột `demo_role` đơn trị | Chưa có | Cần bảng quan hệ `ticketing.user_roles(user_id, role_id)`. |
| **RBAC-006** | Phân quyền theo phạm vi sự kiện (Event Scope) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `organizer_profiles` và `event_staff_assignments` để chặn truy cập chéo sự kiện. |
| **RBAC-007** | Thực thi phân quyền tại tầng Server-side | **PARTIALLY_IMPLEMENTED** | `SecurityConfiguration.java` (`authorizeHttpRequests`) | 401/403 enforced cho `/api/admin/**` và `/api/bookings/**`. | Chưa có kiểm tra phân quyền mức đối tượng (Object-level security theo eventId). |
| **RBAC-008** | Ghi nhật ký kiểm toán phân quyền (Role Audit) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Chưa có bảng `audit_logs` lưu actor, target, reason, timestamp. |
| **RBAC-009** | Chống leo thang đặc quyền (Privilege Escalation) | **PARTIALLY_IMPLEMENTED** | Client không gửi role qua register body | Đã kiểm chứng API. | Cần cấm client tự gửi `organizerId`, `staffId` hoặc `role` trong mọi API nghiệp vụ. |
| **RBAC-010** | Chặn truy cập chéo giữa các Organizer | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bổ sung kiểm tra quyền sở hữu `event.organizer_id == current_user.organizer_id`. |

---

### 2.3. Customer Catalog (CAT-001 → CAT-008)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **CAT-001** | Public Catalog chỉ hiện event hợp lệ | **IMPLEMENTED_AND_VERIFIED** | `CatalogController.java` (`GET /api/events`), `CatalogViews.java` | Test suite backend/frontend PASS. | Cần đồng bộ kiểm tra trạng thái `status = 'PUBLISHED'`. |
| **CAT-002** | Chi tiết sự kiện (Event Detail) | **IMPLEMENTED_AND_VERIFIED** | `CatalogController.java` (`GET /api/events/{id}`) | Backend test PASS, OpenAPI contract test PASS. | Bổ sung thêm thông tin nhà tổ chức công khai (`organizer_name`). |
| **CAT-003** | Tìm kiếm sự kiện theo từ khóa | **PARTIALLY_IMPLEMENTED** | Frontend `App.ts` có ô tìm kiếm lọc client-side | Visual QA đạt | Cần hỗ trợ query param `?search=` trên backend API `GET /api/events`. |
| **CAT-004** | Bộ lọc danh mục / thể loại sự kiện | **PARTIALLY_IMPLEMENTED** | Frontend `App.ts` lọc GA / SEATED | Visual QA đạt | Cần chuẩn hóa bảng `event_categories` và bộ lọc server-side. |
| **CAT-005** | Sắp xếp và phân trang phía Server | **PARTIALLY_IMPLEMENTED** | `AdminCatalogController` có phân trang; `CatalogController` chưa có | Test phân trang Admin PASS | Cần bổ sung phân trang `page/size` có giới hạn cho Public Catalog API. |
| **CAT-006** | Tra cứu tồn kho realtime (Availability) | **IMPLEMENTED_AND_VERIFIED** | `CatalogController.java` (`GET /api/events/{id}/availability`) | Test `failed availability refresh marks retained snapshot as stale` PASS. | Bảo toàn cơ chế đọc snapshot không khóa. |
| **CAT-007** | Tách biệt dữ liệu mẫu nội bộ (Fixture Segregation) | **PARTIALLY_IMPLEMENTED** | Đã dọn dẹp các sự kiện test rác, nạp 4 sự kiện chuẩn | Visual QA đạt | Cần phân tách qua trường `visibility` hoặc lifecycle `DRAFT/PUBLISHED`. |
| **CAT-008** | Vòng đời trạng thái sự kiện (Event Status) | **IMPLEMENTED_AND_VERIFIED** | Bảng `ticketing.events` có cột `status` (`DRAFT`, `PUBLISHED`, `CLOSED`, `CANCELLED`) | Admin update event status PASS | Cần bảo đảm public catalog chỉ select `status = 'PUBLISHED'`. |

---

### 2.4. Organizer Event Management (ORG-001 → ORG-009)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **ORG-001** | Bảng điều khiển riêng của Nhà tổ chức | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần màn hình Organizer Dashboard và route `/organizer`. |
| **ORG-002** | Tạo / Sửa / Quản lý sự kiện thuộc quyền | **PARTIALLY_IMPLEMENTED** | Admin có `AdminCatalogController` tạo/sửa toàn cục | Admin tests PASS | Cần API `/api/organizer/events` kiểm tra quyền sở hữu của Organizer. |
| **ORG-003** | Quản lý Địa điểm & Danh mục sự kiện | **NOT_IMPLEMENTED** | Venue đang là chuỗi text tự do trong `events.venue` | Chưa có | Cần bảng `ticketing.venues` và `ticketing.event_categories`. |
| **ORG-004** | Tải lên hình ảnh poster & tư liệu (Media Upload) | **NOT_IMPLEMENTED** | Ảnh hiện tại dùng URL tĩnh Unsplash | Chưa có | Cần backend multipart upload service, kiểm tra MIME, lưu file và bảng `event_media`. |
| **ORG-005** | Cấu hình loại vé GA và Sơ đồ ghế | **PARTIALLY_IMPLEMENTED** | Admin form có cấu hình GA và Ghế | Test `create form supports GA and seats` PASS | Cần đưa tính năng này vào phân quyền cho Organizer. |
| **ORG-006** | Hiển thị tồn kho & bảo đảm không giảm quá mức bán | **IMPLEMENTED_AND_VERIFIED** | `AdminCatalogService.patchEvent` kiểm tra `capacity >= held + sold` | Test `capacity conflict keeps the edit form` PASS | Giữ nguyên invariant; mở rộng quyền cho Organizer. |
| **ORG-007** | Báo cáo doanh số & danh sách người mua theo event | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần API báo cáo SQL theo `organizer_id`. |
| **ORG-008** | Phân công nhân viên soát vé (Staff Assignment) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `event_staff_assignments` và API gán nhân viên vào sự kiện. |
| **ORG-009** | Cổng kiểm duyệt xuất bản sự kiện (Publish Gate) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần validation gate trước khi chuyển trạng thái sang `PUBLISHED`. |

---

### 2.5. Booking, Tickets & Check-in (BKG-001 → BKG-004, TKT-001 → TKT-003, CHK-001 → CHK-005)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **BKG-001** | Gắn danh tính người dùng thực vào Booking | **IMPLEMENTED_BUT_NOT_VERIFIED** | `BookingController.java` (`POST /api/bookings` lấy `principal.accountId()`), `ticketing.bookings.customer_id` | Manual API verify tạo booking gắn đúng UUID tài khoản đăng nhập qua JWT P0. Chưa có automated integration test xác nhận luồng End-to-End từ Auth JWT thật sang Booking. | Mở rộng automated test xác thực JWT/OIDC thật và kiểm tra ràng buộc sở hữu. |
| **BKG-002** | Bảo toàn bất biến tồn kho GA & SEATED | **IMPLEMENTED_AND_VERIFIED** | `BookingCommandService`, `GaInventory`, `SeatInventory`, `BookingTransactions` | 134 backend tests PASS, 54 frontend tests PASS. | Tuyệt đối không thay đổi cơ chế khóa dòng ACID PostgreSQL. |
| **BKG-003** | Quản lý danh sách vé của tôi (My Bookings) | **IMPLEMENTED_AND_VERIFIED** | `BookingController.java` (`GET /api/me/bookings`), `MyBookingsPage.ts` | Test `select quantity... then fetch history` PASS, Visual QA đạt. | Mở rộng chi tiết vé điện tử kèm mã QR bảo mật. |
| **BKG-004** | Dòng thời gian lịch sử trạng thái đơn hàng | **IMPLEMENTED_AND_VERIFIED** | Bảng `ticketing.booking_status_history`, `BookingLifecycle.java` | Test `refund-required booking retains expired history` PASS. | Tiếp tục duy trì nguồn sự thật từ database. |
| **TKT-001** | Phát hành vé khi đơn hàng `CONFIRMED` | **IMPLEMENTED_AND_VERIFIED** | `TicketService.java`, bảng `ticketing.tickets`, `BookingController.tickets` | Test `success uses the real API result and displays server ticket code` PASS. | Giữ nguyên quy tắc unique(code) và unique(booking_item_id, ordinal). |
| **TKT-002** | Mã QR vé điện tử bảo mật | **PARTIALLY_IMPLEMENTED** | Frontend render mã QR SVG từ mã vé `t.code` | Visual QA đạt | Cần cơ chế tạo chữ ký số (Signed QR Token HMAC-SHA256) để chống giả mạo tại cổng soát vé. |
| **TKT-003** | Ví vé điện tử & In vé / PDF | **PARTIALLY_IMPLEMENTED** | Trang `/ve-cua-toi` dạng Boarding Pass có nút In vé (`window.print()`) | Visual QA đạt | Cần bổ sung endpoint backend xuất file PDF chính thức. |
| **CHK-001** | Phân công nhân viên sự kiện soát vé | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `event_staff_assignments` và ràng buộc kiểm tra assignment khi check-in. |
| **CHK-002** | Xác thực vé tại cổng soát vé | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần API `/api/staff/events/{eventId}/tickets/verify`. |
| **CHK-003** | Check-in nguyên tử (Atomic Check-in) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `ticketing.ticket_check_ins` với ràng buộc `UNIQUE(ticket_id)`. |
| **CHK-004** | Ghi nhận nhật ký kiểm toán soát vé | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần lưu staff_id, event_id, timestamp, kết quả vào SQL. |
| **CHK-005** | Quyền sửa lỗi / Hủy check-in của Admin | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần API hủy check-in dành riêng cho Admin có lý do và audit log. |

---

### 2.6. External Payment Gateway (PAY-001 → PAY-015)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **PAY-001** | Cổng thanh toán trừu tượng (Payment Gateway Port) | **NOT_IMPLEMENTED** | Hiện tại chỉ có `PaymentSimulatorService` | Chưa có | Cần định nghĩa interface `PaymentGatewayPort` tách biệt khỏi vendor SDK. |
| **PAY-002** | Khởi tạo giao dịch thanh toán phía Server | **NOT_IMPLEMENTED** | Chưa có bảng `payment_attempts` | Chưa có | Cần tạo record `payment_attempts` lấy số tiền snapshot từ booking, cấm nhận số tiền từ client. |
| **PAY-003** | Chuyển hướng thanh toán ký số bảo mật | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần logic tạo URL thanh toán và ký số SHA-256 (ví dụ `vnp_SecureHash`). |
| **PAY-004** | Tích hợp môi trường Sandbox trước | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần thông tin tài khoản sandbox VNPAY/MoMo từ chủ dự án. |
| **PAY-005** | Trang tiếp nhận kết quả trình duyệt (Return URL) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần trang Return URL chỉ hiển thị trạng thái chờ xác minh, không confirm vé trực tiếp. |
| **PAY-006** | Endpoint tiếp nhận IPN / Webhook phía Server | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần endpoint IPN xác thực chữ ký kiểm tra số tiền, mã đơn, trạng thái giao dịch. |
| **PAY-007** | Xử lý Callback Idempotent chống trùng | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `ticketing.payment_notifications` với khóa `UNIQUE(provider, transaction_ref)`. |
| **PAY-008** | Xử lý Callback sai thứ tự | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần chính sách máy trạng thái xác định, cấm đảo ngược trạng thái terminal. |
| **PAY-009** | Đối soát & Truy vấn giao dịch không rõ kết quả | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần service đối soát QueryDR khi gặp sự cố timeout mạng. |
| **PAY-010** | Xử lý thanh toán muộn sau khi vé đã bị thu hồi | **PARTIALLY_IMPLEMENTED** | Booking state machine đã có `EXPIRED -> REFUND_REQUIRED` | Backend tests PASS | Cần liên kết kết quả thanh toán muộn từ gateway sang luồng nghĩa vụ hoàn tiền thực tế. |
| **PAY-011** | Hoàn tiền tự động qua Gateway (Refund) | **NOT_IMPLEMENTED** | Chưa có bảng `refund_attempts` | Chưa có | Cần API gọi hoàn tiền idempotent qua cổng thanh toán. |
| **PAY-012** | Màn hình quản trị giao dịch thanh toán | **NOT_IMPLEMENTED** | AdminDashboard chỉ hiển thị booking summary | Chưa có | Cần tab quản trị Payment Attempts & Gateways trong Admin Dashboard. |
| **PAY-013** | Duy trì Payment Simulator cho Test tự động | **IMPLEMENTED_AND_VERIFIED** | `PaymentSimulatorService.java`, `PaymentController.java` | Test suite backend/frontend PASS | Giữ nguyên simulator cho CI/CD test harness; tách biệt khỏi luồng cổng thanh toán thật. |
| **PAY-014** | Kiểm tra toàn vẹn số tiền và đơn vị tiền tệ | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần kiểm tra khớp số tiền VND, mã giao dịch, mã merchant trước khi ghi nhận. |
| **PAY-015** | Khả năng tiếp nhận Callback qua HTTPS | **NOT_IMPLEMENTED** | Chưa cấu hình tunnel / domain HTTPS công khai | Chưa có | Cần phương án HTTPS công khai (Cloudflare Tunnel/Ngrok/Staging) cho IPN sandbox. |

---

### 2.7. Admin & Platform Management (ADM-001 → ADM-010)

| ID | Yêu cầu nghiệp vụ V2 | Trạng thái | Bằng chứng mã nguồn / API / DB | Kiểm thử & Kết quả thực tế | Khoảng trống cần triển khai trong V2 |
|---|---|---|---|---|---|
| **ADM-001** | Tìm kiếm, xem danh sách và chi tiết người dùng | **IMPLEMENTED_AND_VERIFIED** | `AdminUserController.java` (`GET /api/admin/users`), `AdminDashboard.ts` (Tab Người dùng) | Backend test `AdminUserControllerTest` PASS, Visual QA đạt. | Bổ sung xem chi tiết vai trò và lịch sử đặt vé của user. |
| **ADM-002** | Quản lý cấp / thu hồi vai trò người dùng | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần API `POST /api/admin/users/{id}/roles` để gán/hủy role STAFF/ORGANIZER/ADMIN. |
| **ADM-003** | Hệ thống gửi lời mời (Invitations) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `invitations` cho phép mời Staff/Organizer qua email token. |
| **ADM-004** | Quản lý Sự kiện, Địa điểm, Danh mục toàn cục | **PARTIALLY_IMPLEMENTED** | `AdminCatalogController.java` quản lý sự kiện toàn hệ thống | Test Admin Event CRUD PASS | Cần chuẩn hóa bảng `venues` và `event_categories`. |
| **ADM-005** | Quản lý Đơn hàng, Thanh toán và Hoàn tiền | **PARTIALLY_IMPLEMENTED** | `AdminBookingController.java` xem danh sách booking và trạng thái | Test Admin Bookings PASS | Cần bổ sung lệnh duyệt/khởi tạo hoàn tiền gateway từ phía Admin. |
| **ADM-006** | Hiển thị trạng thái cấu hình Gateway | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần trang quản trị xem trạng thái kết nối sandbox, không hiển thị secrets. |
| **ADM-007** | Dashboard tổng hợp chỉ số kinh doanh từ SQL | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần query SQL tổng hợp doanh thu theo ngày, số vé bán, tỷ lệ hủy/hoàn tiền. |
| **ADM-008** | Xuất báo cáo dữ liệu định dạng CSV / Excel | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần API xuất file CSV có xử lý chống CSV Formula Injection. |
| **ADM-009** | Nhật ký kiểm toán an toàn (Audit Log) | **NOT_IMPLEMENTED** | Chưa có | Chưa có | Cần bảng `ticketing.audit_logs` bất biến dạng append-only. |
| **ADM-010** | Vận hành phục hồi Outbox Replay an toàn | **IMPLEMENTED_AND_VERIFIED** | `OperationsController.java`, `ReplayService.java` | Test `recovery adapter reuses bounded replay endpoint` PASS. | Bảo toàn giới hạn ngân sách tối đa 3 lần replay và ghi nhật ký lỗi. |

---

## 3. Tổng Hợp Thống Kê Gap Matrix (Đã Hiệu Chỉnh T11.2)

| Nhóm yêu cầu V2 | Tổng số yêu cầu | IMPLEMENTED_AND_VERIFIED | IMPLEMENTED_BUT_NOT_VERIFIED | PARTIALLY_IMPLEMENTED | NOT_IMPLEMENTED |
|---|:---:|:---:|:---:|:---:|:---:|
| **2.1. Authentication & Account** | 15 | 2 (13.3%) | 0 (0.0%) | 4 (26.7%) | 9 (60.0%) |
| **2.2. Four-Role RBAC** | 10 | 0 (0.0%) | 1 (10.0%) | 3 (30.0%) | 6 (60.0%) |
| **2.3. Customer Catalog** | 8 | 4 (50.0%) | 0 (0.0%) | 4 (50.0%) | 0 (0.0%) |
| **2.4. Organizer Event Management** | 9 | 1 (11.1%) | 0 (0.0%) | 2 (22.2%) | 6 (66.7%) |
| **2.5. Booking, Tickets & Check-in** | 12 | 4 (33.3%) | 1 (8.3%) | 2 (16.7%) | 5 (41.7%) |
| **2.6. External Payment Gateway** | 15 | 1 (6.7%) | 0 (0.0%) | 1 (6.7%) | 13 (86.7%) |
| **2.7. Admin & Platform Management** | 10 | 2 (20.0%) | 0 (0.0%) | 2 (20.0%) | 6 (60.0%) |
| **TỔNG CỘNG** | **79** | **14 (17.7%)** | **2 (2.5%)** | **18 (22.8%)** | **45 (56.9%)** |

### Nhận Xét Tổng Quan:
- **Chuẩn hóa trạng thái kiểm thử (T11.2 calibration):** `RBAC-001` (Gán role CUSTOMER mặc định) và `BKG-001` (Gắn booking với authenticated account) đã được hiệu chỉnh chính xác từ `IMPLEMENTED_AND_VERIFIED` sang `IMPLEMENTED_BUT_NOT_VERIFIED` do mới chỉ được xác minh thủ công qua API manual curl, chưa có automated integration test trong Maven test suite.
- **Nền tảng nghiệp vụ cốt lõi (Core Invariants - 14 yêu cầu `IMPLEMENTED_AND_VERIFIED`):** Đặt vé GA/Ghế, cơ chế khóa dòng ACID PostgreSQL, máy trạng thái đơn hàng, outbox/inbox messaging, mock auth profile phục vụ test và xuất vé đã hoàn thành 100% và được kiểm chứng vững chắc qua cả JUnit và Node test runner.
- **Nền tảng đã chuẩn bị ở giai đoạn P0/P1 (18 yêu cầu `PARTIALLY_IMPLEMENTED`):** Bảng `ticketing.users` đã có email, password băm BCrypt, JWT service, `AuthController`, quản lý người dùng trong Admin Dashboard, và hệ thống Đa Trang.
- **Khối lượng công việc trọng tâm của V2 (45 yêu cầu `NOT_IMPLEMENTED`):**
  1. *Hạ tầng RBAC 4 vai trò & Event Scope*: Bảng `roles`, `user_roles`, `organizer_profiles`, `event_staff_assignments`.
  2. *Đăng nhập Google OIDC*: Spring Security OAuth2 Client Authorization Code flow và bảng `external_identities`.
  3. *Tích hợp Cổng thanh toán Sandbox*: `PaymentGatewayPort`, adapter VNPAY sandbox, bảng `payment_attempts`, `payment_notifications` và cơ chế IPN.
  4. *Soát vé Check-in*: Bảng `ticket_check_ins`, kiểm tra vé bằng mã QR có chữ ký số và giao diện dành riêng cho Staff.
  5. *Quản trị Organizer Dashboard*: Không gian quản lý sự kiện độc lập cho đối tác tổ chức.
