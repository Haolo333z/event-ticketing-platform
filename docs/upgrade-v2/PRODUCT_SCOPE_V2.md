# PRODUCT SCOPE V2 — EventFlow Full-stack Event Ticketing Platform

> Chủ dự án đã chọn: 4 vai trò · email/password + Google Sign-In · thanh toán qua gateway.  
> Trạng thái: **Scope V2 được chốt ngày 2026-11-16; chưa triển khai/kiểm thử.**  
> V2 là phần mở rộng của baseline v0.4, không thay thế lịch sử task/evidence cũ.

## 1. Tầm nhìn sản phẩm

EventFlow là website đặt vé sự kiện full-stack cho phép người mua tự tạo tài khoản, tìm sự kiện, chọn vé/ghế, thanh toán qua một cổng thanh toán được tích hợp, nhận vé điện tử và quản lý đơn hàng. Nhà tổ chức quản lý các sự kiện thuộc quyền sở hữu; nhân viên sự kiện thực hiện check-in cho sự kiện được phân công; quản trị viên quản lý toàn nền tảng.

Sản phẩm phải sử dụng dữ liệu SQL bền vững cho nghiệp vụ. Giao diện không được giả lập chức năng bằng dữ liệu hardcode hoặc localStorage. Các hoạt động quan trọng phải đi qua API backend, kiểm tra quyền và ghi dữ liệu bằng transaction phù hợp.

## 2. Các quyết định phạm vi đã xác nhận

| Quyết định | Giá trị được chốt | Ghi chú |
|---|---|---|
| Vai trò | `CUSTOMER`, `STAFF`, `ORGANIZER`, `ADMIN` | Có phân quyền theo phạm vi sự kiện; xem mục 3 |
| Đăng nhập | Email/password + Google Sign-In | Google dùng OpenID Connect; không dùng Google token trực tiếp làm token API nội bộ |
| Thanh toán | Một cổng thanh toán bên ngoài | Tích hợp sandbox end-to-end trước; production chỉ bật khi có tài khoản/duyệt merchant và secrets |
| Gateway vendor | Chưa chốt | Khuyến nghị VNPAY sandbox cho lần tích hợp đầu tại Việt Nam; cần chủ dự án xác nhận trước khi adapter provider-specific được triển khai |
| SQL | PostgreSQL | Nguồn sự thật cho account, role, catalog, booking, payment, tickets, check-in, audit |
| Kiến trúc | Giữ modular monolith + EDA hiện có | Chỉ mở rộng qua ADR/migration/API contract; không rewrite booking engine |
| Simulator | Giữ cho automated tests/local fault tests | Không phải lựa chọn thanh toán chính trên customer production-like journey |

## 3. Bốn vai trò và phạm vi quyền

### 3.1 CUSTOMER — Khách hàng

- Đăng ký bằng email/password hoặc đăng nhập Google.
- Quản lý hồ sơ và phương thức đăng nhập được liên kết.
- Duyệt, tìm kiếm, lọc và xem sự kiện đã xuất bản.
- Chọn loại vé GA hoặc ghế chỉ định.
- Tạo booking, giữ vé, thanh toán qua gateway và xem trạng thái.
- Xem đơn hàng, vé điện tử, QR/check-in status và lịch sử thanh toán của chính mình.
- Hủy booking chỉ khi trạng thái/chính sách cho phép; không tự hủy đơn đã confirmed trái chính sách.
- Gửi yêu cầu hỗ trợ/hoàn tiền theo chính sách được công bố.

### 3.2 STAFF — Nhân viên sự kiện

- Được ADMIN cấp role STAFF; được ORGANIZER/ADMIN phân công vào một hoặc nhiều event cụ thể.
- Chỉ xem lịch sự kiện, attendee list tối thiểu và vé thuộc event được phân công.
- Quét QR hoặc nhập mã vé để kiểm tra vé.
- Check-in một vé đúng một lần; thao tác lặp phải trả trạng thái “đã check-in”, không tăng lượt check-in.
- Xem kết quả scan hợp lệ/không hợp lệ/đã sử dụng/không thuộc event.
- Xem lịch sử check-in trong phạm vi event được giao.
- Không được sửa giá, tồn kho, payment, refund, tài khoản người dùng hoặc cấu hình nền tảng.

### 3.3 ORGANIZER — Nhà tổ chức

- Được ADMIN cấp role ORGANIZER; không tự nâng role khi đăng ký công khai.
- Quản lý hồ sơ nhà tổ chức và các event thuộc quyền sở hữu/được giao.
- Tạo, chỉnh sửa, lưu nháp, gửi xuất bản hoặc đóng bán event theo policy.
- Quản lý thông tin sự kiện, poster, lịch, địa điểm, ticket types, giá, sale window và inventory trong giới hạn an toàn.
- Xem bookings, doanh số, vé bán, lượng vé còn và danh sách attendee của event thuộc quyền.
- Mời/phân công STAFF vào event của mình nếu policy cho phép; không tự cấp role ADMIN/ORGANIZER/STAFF toàn nền tảng.
- Gửi yêu cầu hoàn tiền/hủy event theo policy; không tự giả lập kết quả gateway hoặc sửa ledger thanh toán.

### 3.4 ADMIN — Quản trị toàn nền tảng

- Đăng nhập bằng tài khoản đã bootstrap/cấp quyền an toàn; không có public self-registration cho ADMIN.
- Quản lý người dùng, role, trạng thái tài khoản và lời mời.
- Quản lý toàn bộ organizer, staff assignment, event, category, venue và catalog.
- Xem toàn bộ booking/payment/refund/check-in và báo cáo toàn nền tảng.
- Duyệt/khởi tạo refund theo policy và quyền được audit.
- Quản lý nội dung homepage/FAQ nếu module CMS được chấp nhận.
- Theo dõi outbox/Kafka/recovery với quyền được bảo vệ; thao tác replay phải giữ audit, idempotency và budget hiện có.
- Không xem/ghi secrets merchant hoặc dữ liệu thẻ thanh toán qua UI.

### 3.5 Quy tắc gán role

- Tài khoản đăng ký mới luôn có `CUSTOMER` mặc định.
- `STAFF` và `ORGANIZER` chỉ được cấp bởi ADMIN hoặc quy trình invitation được ADMIN ủy quyền; không nhận role từ request body hoặc Google profile.
- `ADMIN` chỉ được cấp qua bootstrap an toàn/ADMIN hiện hữu, không thể tự đăng ký hoặc tự nâng cấp.
- Một user có thể có nhiều role grants (ví dụ CUSTOMER + ORGANIZER) để một người vừa mua vé vừa tổ chức sự kiện. Role được lưu tách khỏi provider identity.
- Role không tự động tạo quyền toàn cục: ORGANIZER bị giới hạn theo event ownership; STAFF bị giới hạn theo event assignment.
- Mọi thay đổi role/assignment phải có audit actor, target, scope, thời gian và lý do.

## 4. Phạm vi chức năng theo module

### 4.1 Identity & Access — P0

- Đăng ký email/password, xác minh email khi email delivery được cấu hình.
- Đăng nhập email/password, đăng xuất, refresh/revoke phiên.
- Google Sign-In bằng OIDC Authorization Code flow qua backend.
- Liên kết/hủy liên kết Google identity với tài khoản đang đăng nhập sau re-auth.
- Quên/đặt lại mật khẩu; không tiết lộ email tồn tại hay không.
- Hồ sơ tài khoản, đổi mật khẩu, trạng thái ACTIVE/LOCKED/DISABLED.
- Role-based authorization và event-scoped authorization.
- Tài khoản admin bootstrap; quản lý mời staff/organizer.

### 4.2 Public Event Catalog — P0

- Homepage, danh mục, event cards, featured/upcoming events.
- Search theo keyword; filter category/date/location/price/status nếu dữ liệu/API hỗ trợ.
- Sorting và pagination server-side.
- Event detail với poster, mô tả, thời gian, venue, organizer, ticket types, sale window và availability.
- Chỉ event PUBLISHED và trong policy public mới xuất hiện public catalog.
- Dữ liệu smoke/test fixture không được công khai bằng cách lọc substring/tên; phải phân biệt qua lifecycle/visibility/seed profile.

### 4.3 Organizer Catalog Management — P0

- Organizer dashboard cho event của chính organizer.
- CRUD event, draft/submit/publish/close/cancel theo state policy.
- Quản lý ticket types, GA capacity, seat sections/labels và sale windows.
- Quản lý poster/media metadata; file ảnh lưu filesystem/object store, SQL lưu metadata và path/key; không nhét ảnh base64 lớn vào SQL.
- Kiểm tra không giảm capacity dưới held+sold, không sửa/xóa ghế HELD/SOLD, không đổi mode khi có booking.

### 4.4 Booking & Ticket — P0

- Giữ nguyên nghiệp vụ GA/SEATED, idempotency, hold expiry, locking, all-or-nothing, booking history, Outbox/Inbox.
- Thay principal demo bằng account identity thật cho luồng customer; fixture mock chỉ tồn tại trong profile test/local có guard.
- My bookings, booking detail, status timeline, booking items, ticket wallet.
- Ticket chỉ cấp sau booking CONFIRMED; vé có mã opaque/QR payload được server xác thực.
- Staff check-in endpoint transaction-safe và unique per ticket/event; lưu actor/time/device metadata tối thiểu.

### 4.5 Payment Gateway — P0

- Chỉ một provider trong first release; vendor chưa được owner chốt.
- Payment adapter/provider port; sandbox adapter tích hợp end-to-end.
- Backend tạo payment attempt/merchant reference và tính amount từ booking snapshot.
- Backend tạo signed checkout URL/redirect; frontend không tự tính amount hoặc ký request.
- Return URL chỉ để hiển thị trạng thái; callback/IPN server-to-server có xác thực chữ ký mới là nguồn cập nhật payment state theo provider contract.
- Validate merchant ID, transaction reference, amount, currency, provider status, timestamp/expiry và mapping booking trước khi áp dụng.
- Callback idempotent; duplicate/out-of-order callback không tạo vé hoặc release inventory lần hai.
- Query/reconciliation cho trạng thái không rõ; lưu provider reference và trạng thái đối soát.
- Late success sau khi booking đã EXPIRED/đã release inventory không được confirm lại hoặc lấy lại ghế; phải tạo refund/compensation và cảnh báo.
- Refund gateway cho late success là bắt buộc; refund do khách hàng/event cancellation có quy trình approval theo policy.
- Không lưu PAN/CVV/OTP hoặc credential nhạy cảm; secrets ngoài Git và SQL plaintext.

### 4.6 Staff Operations — P0

- Danh sách event được giao.
- Attendee list với PII tối thiểu.
- Scan QR bằng camera trên thiết bị hỗ trợ hoặc nhập code thủ công.
- Check-in atomic, chống scan trùng, audit người thao tác.
- Staff không được tự sửa check-in đã thực hiện; correction chỉ qua ADMIN/authorized flow có audit.

### 4.7 Admin Platform — P0/P1

P0:
- Quản lý users, role grants, trạng thái account và invitation.
- Quản lý events/categories/venues/ticket types.
- Xem tất cả booking/payment/refund/check-in.
- Gán organizer/staff vào event.
- Dashboard số liệu cốt lõi từ SQL/API thật.
- Audit log thao tác nhạy cảm.

P1:
- Báo cáo theo thời gian/event/organizer/ticket type.
- Export CSV/XLSX.
- Quản lý banner/FAQ/content đơn giản.
- Quản lý support tickets.

### 4.8 Notifications & Customer Support — P1

- In-app notifications: registration, booking, payment, ticket issue, event change, reminder.
- Email giao dịch khi SMTP/email service được cấu hình; local dùng mail catcher.
- Đánh dấu đã đọc, notification history.
- FAQ, contact/support ticket, trạng thái yêu cầu hỗ trợ.

### 4.9 Growth features — P2

- Coupons/discount codes, favorites, reviews, loyalty, recommendation, waitlist, transfer ticket, multiple gateway providers, multi-currency, mobile app, dynamic pricing, public organizer onboarding.
- Không đưa các chức năng này vào P0 trừ khi chủ dự án mở rộng riêng.

## 5. Những gì được giữ từ baseline v0.4

- Java 25/Spring Boot 4.1.2, React 19/TypeScript 7/Vite 8, PostgreSQL 17.11, Kafka 4.2.2, Docker Compose và Flyway theo ADR0001.
- Modular monolith; PostgreSQL là nguồn sự thật; Kafka phân phối event sau commit.
- GA/SEATED, booking all-or-nothing, hold expiry, idempotency, state machine, Outbox/Inbox, retry/DLQ/recovery.
- Các bất biến booking và inventory đã có; không rewrite vì thay đổi authentication/payment UI.
- Payment simulator được giữ làm test adapter/fault-injection profile, không làm payment channel chính của customer flow.
- V1–V9 migration đã chạy không chỉnh sửa; mọi schema change mới dùng migration mới V10+.

## 6. Không nằm trong V2 core

- Microservices per domain, Kubernetes, multi-region, Redis làm nguồn correctness, Internet-scale flash-sale queue.
- Lưu dữ liệu thẻ, xử lý card trực tiếp, tự xây dựng payment processor.
- Nhiều cổng thanh toán cùng lúc; chỉ một provider trong first gateway release.
- Production merchant account/live money trước khi có phê duyệt/credentials và kiểm thử pháp lý/vận hành.
- Multi-organizer settlement/payout tự động hoặc marketplace commission ở V2 core.
- Mobile native apps.

## 7. Định nghĩa sản phẩm hoàn thành V2

Một người chưa có tài khoản có thể đăng ký email/password hoặc Sign in with Google, sau đó:

1. Duyệt và tìm sự kiện public thật từ backend/PostgreSQL.
2. Mở chi tiết sự kiện và chọn GA hoặc ghế chỉ định.
3. Tạo booking, giữ chỗ và thanh toán qua gateway sandbox.
4. Nhận kết quả được xác minh từ callback/IPN server; xem booking/ticket sau khi xác nhận.
5. Tải/hiển thị vé QR, và staff được phân công có thể check-in đúng một lần.
6. Organizer chỉ quản lý event thuộc quyền của mình.
7. Admin quản lý bốn role, catalog, booking, payment/refund và các báo cáo nền tảng.
8. Restart ứng dụng không làm mất account/catalog/booking/payment/ticket/check-in data trong PostgreSQL.
9. Automated tests chứng minh authorization scope, callback signature/idempotency, no oversell, no duplicate ticket/check-in và recovery.

Mọi chức năng chỉ được coi là DONE khi có UI/API/SQL/test/evidence tương ứng. Screenshot đơn lẻ không chứng minh end-to-end behavior.
