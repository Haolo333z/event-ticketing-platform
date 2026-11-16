# SOURCE RE-BASELINE — Kiểm toán Hiện trạng Kỹ thuật Repository EventFlow

> Ngày thực hiện: **2026-11-16 (Asia/Saigon)**  
> Người thực hiện: **Principal Software Architect & Lead Full-stack Engineer**  
> Căn cứ kiểm toán: Kiểm tra trực tiếp Git working tree, mã nguồn Java Backend, React Frontend, Database PostgreSQL, Docker Compose và bộ test suite tự động.

---

## 1. Trạng Thái Git & Snapshot Môi Trường

- **Nhánh hiện tại:** `main` (theo dõi `origin/main [ahead 3]`).
- **HEAD Commit SHA:** `dd2ceadea18bca524c855fa08df1b44f634b5104` (`feat(branding): nang cap toan dien dien mao chuan doanh nghiep sao kim branding`).
- **5 Commit gần nhất:**
  1. `dd2cead` — `feat(branding): nang cap toan dien dien mao chuan doanh nghiep sao kim branding`
  2. `ccabd1e` — `fix(ui): an nut ve cua toi doi voi tai khoan admin`
  3. `b8f890a` — `feat(p1): hoan thien luong giu ve co auth gate, modal ve cua toi va admin users leads`
  4. `5c10c00` — `hoan tat xac thuc thuc te bcrypt jwt va luu tru postgresql v10`
  5. `11a83af` — `chore(release): hoan tat nghiem thu T10.4 va dong goi phat hanh do an`
- **Trạng thái Working Tree:**
  - *Modified files*: `docs/PROGRESS.md`, `frontend/src/app/App.ts`, `frontend/src/app/DemoShell.ts`, `frontend/src/styles.css`.
  - *Untracked new files*: `frontend/src/app/{SolutionsPage.ts, PricingPage.ts, ContactPage.ts, MyBookingsPage.ts}`, `docs/evidence/visual-qa/multipage-*.png`, `tools/capture-multipage.ps1`, `docs/upgrade-v2/`, `prompts/`.
  - Không có file staged (`git diff --cached` rỗng).

---

## 2. Các Module Thực Tế Trong Mã Nguồn

### 2.1 Backend (Java 25 LTS, Spring Boot 4.1.2)
Tổ chức theo mô hình **Modular Monolith** trong package `com.haolo.ticketing`:
1. `bookings`:
   - `BookingController`: `/api/bookings` (Tạo booking GA/SEATED), `/api/bookings/{id}` (Tra cứu booking), `/api/me/bookings` (Tra cứu booking của principal hiện tại), `/api/bookings/{id}/cancel` (Hủy booking), `/api/bookings/{id}/tickets` (Xuất vé).
   - `AdminBookingController`: `/api/admin/bookings` (Tra cứu danh sách booking toàn nền tảng, có lọc status và phân trang).
   - `BookingCommandService`, `BookingQueryService`, `BookingCancelService`, `BookingPolicy`, `BookingTransactions`.
2. `events` (Catalog):
   - `CatalogController`: `/api/events` (Danh sách sự kiện mở bán), `/api/events/{id}` (Chi tiết sự kiện), `/api/events/{id}/availability` (Tồn kho GA và trạng thái ghế AVAILABLE/HELD/SOLD realtime).
   - `AdminCatalogController`: `/api/admin/events` (Tạo, sửa, xem danh sách, cập nhật tồn kho).
3. `identity` (Xác thực & Định danh):
   - `AuthController`: `/api/auth/register` (Đăng ký email/password băm BCrypt), `/api/auth/login` (Đăng nhập mật khẩu, sinh JWT), `/api/auth/me` (Lấy thông tin tài khoản hiện tại).
   - `AdminUserController`: `/api/admin/users` (Phân trang danh sách người dùng, tìm kiếm theo tên, email, SĐT).
   - `ContactLeadController`: `/api/contact/leads` (Tiếp nhận thông tin đối tác tổ chức sự kiện lưu vào bảng `ticketing.contact_leads`).
   - `AdminLeadController`: `/api/admin/leads` (Xem danh sách lead tư vấn).
   - `JwtService`: Phát hành và xác thực token HMAC-SHA256 (Jackson 3 ObjectMapper, thời hạn 24 giờ).
   - `MockBearerFilter`: Bộ lọc bảo mật 2 chế độ (Ưu tiên token JWT thực tế; fallback sang mock bearer token nếu profile `local/test` bật).
   - `SecurityConfiguration`: Cấu hình phân quyền Spring Security Stateless, bảo vệ các endpoint theo `hasRole('CUSTOMER')`, `hasRole('ADMIN')` hoặc `permitAll()`.
4. `inventory`:
   - `GaInventory`: Kiểm tra sức chứa `held + sold <= capacity` với Row-level Lock.
   - `SeatInventory`: Khóa ghế nguyên tử (All-or-nothing multi-seat reservation), sắp xếp seat IDs tăng dần chống Deadlock.
   - `InventoryConfirmation`, `InventoryRelease`.
5. `payments`:
   - `PaymentController`: `/api/bookings/{id}/simulate-payment` (Mô phỏng thanh toán với kịch bản SUCCESS, FAILURE, DELAY_TIMEOUT).
   - `PaymentSimulatorService`, `PaymentResultProcessor`, `RefundRecoveryService`.
6. `expiry`:
   - `ExpiryScheduler`, `ExpiryService`: Quét độc lập các booking hết hạn giữ chỗ (Hold TTL 600s), đối chiếu clock PostgreSQL, nhả tài nguyên ghế và inventory GA an toàn, không block luồng mua vé.
7. `messaging`:
   - `OutboxWriter`, `OutboxPublisher`: Mô hình Transactional Outbox ghi sự kiện vào `ticketing.outbox_events` cùng transaction với booking, publisher gửi sang Apache Kafka 4.2 sau khi commit DB.
   - `EventConsumer`: Consumer Kafka ghi nhận vào `ticketing.inbox_events` với `(consumer, event_id)` để Deduplication (At-least-once + dedup).
8. `operations`:
   - `OperationsController`: `/api/admin/operations/outbox`, `/api/admin/operations/recovery`, `/api/admin/operations/events/{id}`, `/api/admin/operations/events/{id}/replay` (Phục hồi replay có ngân sách tối đa 3 lần).
   - `TraceContext`, `TraceLog`, `DatabaseHealthIndicator`, `MessagingHealthIndicator`.
9. `tickets`:
   - `TicketService`: Cấp phát vé độc nhất có mã code ngẫu nhiên và `unique(booking_item_id, ticket_ordinal)` khi booking chuyển trạng thái `CONFIRMED`.

---

### 2.2 Database (PostgreSQL 17.11)
- Schema: `ticketing`
- Flyway migrations: Đã áp dụng **10 migrations** (V1 đến V10), gồm **19 bảng**:
  1. `ticketing.users` (id, demo_role, display_name, email, password_hash, phone, created_at)
  2. `ticketing.events` (id, title, description, venue, starts_at, sale_start_at, sale_end_at, status, created_at, updated_at)
  3. `ticketing.ticket_types` (id, event_id, name, mode, price_vnd, active, capacity)
  4. `ticketing.inventory_ga` (ticket_type_id, capacity, held, sold, version)
  5. `ticketing.seats` (id, ticket_type_id, label, status, version)
  6. `ticketing.bookings` (booking_id, account_id, status, expires_at, total_amount_vnd, aggregate_version, created_at)
  7. `ticketing.booking_items` (id, booking_id, ticket_type_id, quantity, unit_price_vnd)
  8. `ticketing.booking_status_history` (id, booking_id, from_status, to_status, occurred_at)
  9. `ticketing.idempotency_keys` (account_id, operation, idempotency_key, payload_hash, booking_id, created_at)
  10. `ticketing.payments` (payment_id, booking_id, scenario, request_id, recorded_at, processed_at)
  11. `ticketing.payment_compensations` (id, payment_id, status, reason, recorded_at)
  12. `ticketing.compensation_attempts` (id, compensation_id, attempt_no, status, error_message, attempted_at)
  13. `ticketing.tickets` (id, booking_id, booking_item_id, ticket_ordinal, code, seat_id, seat_label, created_at)
  14. `ticketing.outbox_events` (event_id, event_type, aggregate_type, aggregate_id, payload, booking_id, status, created_at)
  15. `ticketing.inbox_events` (consumer_name, event_id, processed_at)
  16. `ticketing.message_failures` (id, consumer_name, event_id, error_message, stack_trace, failed_at)
  17. `ticketing.projection_events` (id, event_id, event_type, aggregate_id, aggregate_version, payload, recorded_at)
  18. `ticketing.event_projections` (event_id, total_bookings, total_tickets, total_revenue_vnd, last_applied_version, updated_at)
  19. `ticketing.contact_leads` (id, full_name, email, phone, company_name, event_scale, message, created_at)

---

### 2.3 Frontend (React 19, TypeScript 7, Vite 8)
- Mô hình: Single Page Application tích hợp **Client-Side HTML5 Router** (`window.location.pathname` + `history.pushState` + `popstate`).
- Hệ thống 7 trang chuyên biệt (Dedicated Pages):
  1. `/` hoặc `/home`: Trang Chủ (Hero banner Sao Kim Deep Royal Blue, 4 trụ cột công nghệ, quy trình 4 bước, FAQ, footer).
  2. `/dat-ve`: Trang Đặt Vé & Sơ Đồ Ghế (Catalog, bộ lọc thể loại GA/Ghế, Sân khấu chính, chọn ghế realtime, giữ vé ACID, đếm ngược 10 phút, thanh toán, E-Ticket QR Code SVG).
  3. `/giai-phap`: Trang Chi Tiết Giải Pháp Doanh Nghiệp ([SolutionsPage.ts](file:///d:/event-ticketing-platform/frontend/src/app/SolutionsPage.ts)) với 3 phân khúc ngành (Concert, Nhà hát, Hội nghị) và ảnh chất lượng cao.
  4. `/bang-gia`: Trang Bảng Giá & So Sánh Gói Dịch Vụ ([PricingPage.ts](file:///d:/event-ticketing-platform/frontend/src/app/PricingPage.ts)) với bảng ma trận 12 tiêu chí.
  5. `/ve-cua-toi`: Trang Quản Lý Vé Cá Nhân Toàn Màn Hình ([MyBookingsPage.ts](file:///d:/event-ticketing-platform/frontend/src/app/MyBookingsPage.ts)) tra cứu từ `/api/me/bookings`, hiển thị Boarding Pass QR SVG.
  6. `/lien-he`: Trang Liên Hệ & Đặt Lịch Tư Vấn ([ContactPage.ts](file:///d:/event-ticketing-platform/frontend/src/app/ContactPage.ts)) kết nối API PostgreSQL `/api/contact/leads`.
  7. `/admin`: Cổng Quản Trị Viên ([AdminDashboard.ts](file:///d:/event-ticketing-platform/frontend/src/app/AdminDashboard.ts)) quản lý sự kiện, tồn kho, đơn hàng, người dùng và replay Kafka Outbox.
- Xác thực: [AuthModal.ts](file:///d:/event-ticketing-platform/frontend/src/app/AuthModal.ts) hỗ trợ cả Đăng nhập & Đăng ký tài khoản thật với BCrypt & JWT.

---

## 3. Khác Biệt Giữa Tài Liệu Baseline V0.4 và Thực Tế Source Code Hiện Tại

| Hạng mục | Tài liệu Baseline V0.4 ghi nhận | Thực tế Source Code hiện tại | Ghi chú & Đánh giá |
|---|---|---|---|
| **Số lượng migration** | V1–V9 (9 files) | **V1–V10 (10 files)** | Đã bổ sung `V10__user_authentication.sql` trong giai đoạn P0 |
| **Số lượng bảng DB** | 18 bảng | **19 bảng** | Bổ sung `ticketing.contact_leads` và mở rộng `users` (email, password_hash, phone) |
| **Xác thực tài khoản** | Mock bearer token (`demo-customer-token`, `demo-admin-token`) | **JWT Token thực tế + BCrypt** kết hợp Dual-mode Mock fallback | Người dùng có thể đăng ký tài khoản thật, đăng nhập và giữ vé gắn đúng `account_id` |
| **Bảo mật mật khẩu** | Không có mật khẩu | **BCryptPasswordEncoder (Spring Security Crypto)** | Mật khẩu được băm an toàn 10 vòng |
| **Giao diện người dùng** | Prototype kỹ thuật 1 màn hình cuộn | **Hệ thống Đa Trang (7 trang)** chuẩn Sao Kim Branding | Bổ sung Mega Menu 2 cột, Deep Royal Blue Hero, Floating dock, Sơ đồ ghế Sân khấu |
| **Bảng Quản trị Admin** | Quản lý Event, Bookings, Outbox | Thêm tab **Quản lý Người dùng** (`GET /api/admin/users`) | Tìm kiếm và phân trang người dùng thực tế từ DB |
| **OpenAPI Contract** | 17 paths / 18 operations | **17 paths / 18 operations** | Các API mới của P0 (`/api/auth/*`, `/api/admin/users`, `/api/contact/leads`) chưa được thêm vào file spec |

---

## 4. Kết Quả Chạy Các Bộ Test Suite Hiện Tại

1. **Frontend Tests:**
   - Lệnh: `npm.cmd test --prefix frontend`
   - Kết quả: **54/54 PASS** (0 failures, 2.11s).
2. **Frontend Build & Typecheck:**
   - Lệnh: `npm.cmd run build --prefix frontend`
   - Kết quả: **BUILD SUCCESS** (`tsc --noEmit` 0 errors, Vite 28 modules trong 227ms).
3. **Contract Tests:**
   - Lệnh: `npm.cmd test --prefix tools/contracts`
   - Kết quả: **69/69 PASS** (OpenAPI 3.1, 18 operations).
4. **Backend Integration & Concurrency Tests:**
   - Lệnh: `backend/mvnw.cmd -f backend/pom.xml test`
   - Kết quả: **134/134 PASS** (0 failures, 0 errors, 121 skipped integration suites cho local run).

---

## 5. Ranh Giới Bất Biến Kỹ Thuật Cần Bảo Vệ Cho V2

1. **PostgreSQL là nguồn sự thật (Source of Truth):**
   - Không chuyển sang Redis hoặc bộ nhớ đệm làm lớp kiểm tra tồn kho duy nhất.
   - Giữ nguyên ràng buộc bất biến GA `held + sold <= capacity` và kiểm tra ghế có tối đa 1 chủ sở hữu hiện hành.
2. **Atomic All-or-nothing & Deadlock Prevention:**
   - Khóa ghế theo thứ tự ID tăng dần cố định, rollback toàn bộ nếu có bất kỳ ghế nào đã bị chiếm hoặc bận.
3. **Idempotency & State Machine:**
   - `PENDING_PAYMENT → CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED`; duy nhất `EXPIRED → REFUND_REQUIRED`.
   - Khi một booking đã bị Expiry Worker commit `EXPIRED` và nhả ghế ra kho, dù cổng thanh toán báo thành công muộn (Late Success), tuyệt đối **KHÔNG** được confirm lại hoặc chiếm lại ghế; bắt buộc chuyển sang luồng bồi hoàn/hoàn tiền (`REFUND_REQUIRED`).
4. **At-least-once EDA (Transactional Outbox + Inbox Dedup):**
   - Outbox event commit cùng transaction với dữ liệu nghiệp vụ; Inbox dedup theo `(consumer_name, event_id)` trước khi commit transaction nhận tin.
5. **Tính bất biến của Migration cũ:**
   - Giữ nguyên toàn bộ Flyway migrations V1 đến V10 đã chạy; mọi mở rộng cho V2 bắt buộc sử dụng **V11+**.
