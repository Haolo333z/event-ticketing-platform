# PROMPT — HƯỚNG DẪN THỰC THI TASK T13: PHÂN HỆ QUẢN TRỊ NHÀ TỔ CHỨC (ORGANIZER MANAGEMENT, VENUES & STAFF ASSIGNMENTS)

Bạn là **Senior Full-stack Engineer & Security Specialist** tiếp quản repository `event-ticketing-platform` để triển khai **Task T13 (Phase 2 — Quản Lý Nhà Tổ Chức, Địa Điểm & Phân Bổ Nhân Sự)** theo đúng Lộ trình Nâng cấp EventFlow V2.

---

## 1. MỤC TIÊU CỦA TASK T13

Xây dựng trọn vẹn phân hệ dành riêng cho **Nhà tổ chức sự kiện (ORGANIZER)**:
1. Đăng ký/khởi tạo hồ sơ đối tác (`organizer_profiles`).
2. Quản lý vòng đời sự kiện: Tạo sự kiện mới (`DRAFT`), chỉnh sửa thông tin, chọn địa điểm chuẩn (`venues`), phân loại danh mục (`event_categories`) và xuất bản mở bán (`PUBLISHED`).
3. Cấu hình các hạng vé: Thiết lập loại vé GA (General Admission) hoặc cấu hình ghế chỉ định (Seated).
4. Phân công nhân sự (Staff Assignment): Gán tài khoản vai trò `STAFF` phụ trách soát vé sự kiện có giới hạn phạm vi (Event Scope Access Control).
5. Bảo vệ tuyệt đối ranh giới dữ liệu giữa các Organizer (Organizer A không bao giờ được xem/sửa sự kiện của Organizer B).

---

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI THỰC THI

1. **Kiểm tra trạng thái Git:**
   - Chạy lệnh: `git status --short --branch`, `git rev-parse HEAD`, `git log -3 --oneline`.
   - **BẢO VỆ CÁC THAY ĐỔI CHƯA COMMIT:** Tuyệt đối không chạy `git restore .`, `git reset --hard` hoặc `git clean -fd`. Không ghi đè các file đa trang thương mại (`SolutionsPage.ts`, `PricingPage.ts`, `ContactPage.ts`, `MyBookingsPage.ts`), các file identity vừa làm ở T12 và tài liệu trong `docs/upgrade-v2/`.
2. **Đọc tài liệu dự án:**
   - Đọc kỹ `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
   - Đọc thiết kế chi tiết:
     - [docs/upgrade-v2/DATABASE_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/DATABASE_RECOMMENDATION.md) (Mục 3.2 — Nhóm Organizer & Events).
     - [docs/upgrade-v2/API_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/API_RECOMMENDATION.md) (Mục 2.5 — Module Organizer Management).
     - [docs/upgrade-v2/FEATURE_BACKLOG_V2.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/FEATURE_BACKLOG_V2.md) (Nhóm ORG-001 đến ORG-010).
3. **Quy tắc bất biến Migration Database:**
   - **BẤT BIẾN V1–V11:** Tuyệt đối không sửa đổi, không xóa hoặc đổi tên bất kỳ file nào từ `V1` đến `V11`.
   - Toàn bộ thay đổi cấu trúc dữ liệu mới phải được viết vào migration mới:
     `backend/src/main/resources/db/migration/V12__organizer_venues_and_staff_assignments.sql`.
4. **Không phá vỡ Booking Engine & Invariants Cốt Lõi:**
   - Giữ nguyên cơ chế khóa hàng `SELECT FOR UPDATE` trên `inventory_ga` và `seats`.
   - Giữ nguyên cam kết 0% bán lố vé (Zero Oversell Invariant).
   - Giữ nguyên Transactional Outbox, Inbox Deduplication và Payment Simulator phục vụ CI test suite.
   - Giữ nguyên mock bearer token profile cho các integration test hiện có.
5. **Không tự ý commit/push:** Toàn bộ mã nguồn và test phải được giữ lại trong working tree để Tech Lead nghiệm thu.

---

## 3. CÁC HẠNG MỤC CẦN TRIỂN KHAI CHI TIẾT

### Hạng mục 1: Viết Migration V12 Additive Schema
- Tạo file: `backend/src/main/resources/db/migration/V12__organizer_venues_and_staff_assignments.sql`
- Nội dung:
  1. Tạo bảng `ticketing.organizer_profiles`:
     - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
     - `user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT UNIQUE`
     - `organization_name VARCHAR(255) NOT NULL`
     - `contact_email VARCHAR(255) NOT NULL`
     - `phone VARCHAR(32) NOT NULL`
     - `tax_code VARCHAR(64)`
     - `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'SUSPENDED'))`
     - `created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`
     - `updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`
  2. Bổ sung cột vào bảng `ticketing.events`:
     - `ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS organizer_id UUID REFERENCES ticketing.organizer_profiles(id) ON DELETE SET NULL;`
     - `CREATE INDEX IF NOT EXISTS events_organizer_idx ON ticketing.events(organizer_id);`
  3. Tạo bảng `ticketing.venues` (Địa điểm biểu diễn):
     - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
     - `name VARCHAR(255) NOT NULL`
     - `address TEXT NOT NULL`
     - `city VARCHAR(100) NOT NULL`
     - `country VARCHAR(64) NOT NULL DEFAULT 'Vietnam'`
     - `capacity INTEGER NOT NULL CHECK (capacity > 0)`
     - `seating_layout_json JSONB`
     - `created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`
     - Seed sẵn các địa điểm mẫu:
       - `('Nhà hát Lớn Hà Nội', 'Số 1 Tràng Tiền, Hoàn Kiếm', 'Hà Nội', 'Vietnam', 600)`
       - `('Trung tâm Hội nghị Quốc gia', 'Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm', 'Hà Nội', 'Vietnam', 3800)`
       - `('Nhà hát Thành phố Hồ Chí Minh', 'Số 7 Công Trường Lam Sơn, Bến Nghé, Quận 1', 'TP. Hồ Chí Minh', 'Vietnam', 500)`
       - `('Sân vận động Quốc gia Mỹ Đình', 'Đường Lê Đức Thọ, Mỹ Đình, Nam Từ Liêm', 'Hà Nội', 'Vietnam', 40000)`
  4. Tạo bảng `ticketing.event_categories` (Thể loại sự kiện):
     - `id VARCHAR(32) PRIMARY KEY`
     - `name VARCHAR(100) NOT NULL`
     - `description TEXT`
     - `icon_url TEXT`
     - Seed sẵn 4 thể loại:
       - `('CONCERT', 'Đại nhạc hội & Concert', 'Quy mô 5.000 - 50.000 khán giả mở bán đồng loạt', 'music')`
       - `('THEATER', 'Nhà hát & Nghệ thuật Hàn lâm', 'Phân tầng khán phòng và giữ ghế chỉ định chính xác', 'theater')`
       - `('CONFERENCE', 'Hội nghị Công nghệ & Triển lãm', 'Soát vé mã QR đa cổng, phân luồng khách VIP', 'conference')`
       - `('SPORTS', 'Sự kiện Thể thao & Giải đấu', 'Kiểm soát vé khán đài A, B, C, D tốc độ cao', 'sports')`
  5. Bổ sung khóa ngoại danh mục và địa điểm vào `ticketing.events`:
     - `ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS category_id VARCHAR(32) REFERENCES ticketing.event_categories(id) ON DELETE SET NULL;`
     - `ALTER TABLE ticketing.events ADD COLUMN IF NOT EXISTS venue_id UUID REFERENCES ticketing.venues(id) ON DELETE SET NULL;`
  6. Tạo bảng `ticketing.event_media`:
     - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
     - `event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE`
     - `media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('POSTER', 'HERO_BANNER', 'SEAT_MAP', 'GALLERY'))`
     - `file_path TEXT NOT NULL`
     - `file_url TEXT NOT NULL`
     - `mime_type VARCHAR(64) NOT NULL`
     - `file_size_bytes BIGINT NOT NULL`
     - `sort_order INTEGER NOT NULL DEFAULT 0`
     - `created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`
     - `CREATE INDEX idx_event_media_event ON ticketing.event_media(event_id, media_type);`
  7. Tạo bảng `ticketing.event_staff_assignments`:
     - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
     - `event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE CASCADE`
     - `staff_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE CASCADE`
     - `assigned_by UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT`
     - `assigned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`
     - `revoked_at TIMESTAMPTZ`
     - `CREATE UNIQUE INDEX idx_event_staff_active ON ticketing.event_staff_assignments(event_id, staff_user_id) WHERE revoked_at IS NULL;`
     - `CREATE INDEX idx_staff_assigned_lookup ON ticketing.event_staff_assignments(staff_user_id, event_id) WHERE revoked_at IS NULL;`

---

### Hạng mục 2: Triển Khai Backend Organizer Domain & Event Scope Security
- Package: `backend/src/main/java/com/haolo/ticketing/organizer/`
- Công việc:
  1. Tạo Entity / Records / Repositories:
     - `OrganizerProfile`, `Venue`, `EventCategory`, `EventStaffAssignment`.
     - `OrganizerProfileRepository` (tìm theo `user_id`, `id`).
     - `VenueRepository` (tìm tất cả địa điểm active).
     - `EventStaffAssignmentRepository` (tìm nhân viên đang được gán active, kiểm tra phân công).
  2. Xây dựng `EventSecurityService`:
     - Phương thức: `boolean isEventOwner(UUID eventId, UUID userId)`.
     - Kiểm tra sự kiện có `organizer_id` trùng khớp với `organizer_profiles.id` của người dùng đăng nhập (hoặc nếu người dùng là `ADMIN` thì có toàn quyền bypass).
  3. Xây dựng `OrganizerService`:
     - `getOrCreateProfile(UUID userId, OrganizerProfileRequest req)`: Tạo hoặc cập nhật hồ sơ doanh nghiệp của Organizer.
     - `listMyEvents(UUID userId)`: Danh sách các sự kiện do chính Organizer sở hữu.
     - `createDraftEvent(UUID userId, CreateEventRequest req)`: Tạo sự kiện ở trạng thái `DRAFT`, tự động gán `organizer_id`.
     - `updateEvent(UUID userId, UUID eventId, UpdateEventRequest req)`: Cập nhật thông tin sự kiện (chặn 403 nếu không phải chủ sở hữu).
     - `publishEvent(UUID userId, UUID eventId)`: Kiểm tra điều kiện mở bán (phải có ít nhất 1 `ticket_type`, sức chứa > 0) -> chuyển `status` sang `PUBLISHED`.
     - `addTicketType(UUID userId, UUID eventId, TicketTypeRequest req)`: Cấu hình loại vé GA hoặc SEATED vào sự kiện.
     - `assignStaff(UUID userId, UUID eventId, UUID staffUserId)`: Gán tài khoản Staff vào sự kiện (xác minh `staffUserId` có role `STAFF` trong `user_roles`).
     - `revokeStaff(UUID userId, UUID eventId, UUID staffUserId)`: Thu hồi quyền soát vé của Staff.
     - `listAssignedStaff(UUID userId, UUID eventId)`: Xem danh sách nhân viên đang phụ trách sự kiện.

---

### Hạng mục 3: Triển Khai REST API Cho Organizer
- Tạo Controller: `backend/src/main/java/com/haolo/ticketing/organizer/OrganizerEventController.java`
- Khai báo: `@RestController`, `@RequestMapping("/api/organizer")`, `@PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")`.
- Endpoints bắt buộc:
  - `GET /api/organizer/profile`: Lấy thông tin hồ sơ Organizer của tài khoản đang đăng nhập.
  - `POST /api/organizer/profile`: Khởi tạo/cập nhật hồ sơ Organizer.
  - `GET /api/organizer/events`: Danh sách các sự kiện của Organizer hiện tại.
  - `POST /api/organizer/events`: Tạo sự kiện mới trạng thái `DRAFT`.
  - `GET /api/organizer/events/{id}`: Xem chi tiết sự kiện của mình (chặn 403 nếu Organizer khác truy vấn).
  - `PUT /api/organizer/events/{id}`: Chỉnh sửa thông tin sự kiện.
  - `POST /api/organizer/events/{id}/publish`: Xuất bản sự kiện lên Public Catalog.
  - `POST /api/organizer/events/{id}/ticket-types`: Thêm loại vé GA hoặc SEATED.
  - `GET /api/organizer/events/{id}/staff`: Lấy danh sách nhân viên được phân công.
  - `POST /api/organizer/events/{id}/staff`: Phân công nhân viên soát vé.
  - `DELETE /api/organizer/events/{id}/staff/{staffUserId}`: Thu hồi quyền nhân viên.
  - `GET /api/organizer/venues`: Danh mục địa điểm biểu diễn.
  - `GET /api/organizer/categories`: Danh mục thể loại sự kiện.

---

### Hạng mục 4: Triển Khai Giao Diện Frontend Quản Trị Organizer
- Thư mục: `frontend/src/app/`
- Công việc:
  1. Tạo component `OrganizerDashboard.ts` (hoặc nâng cấp màn hình `/organizer`):
     - Dashboard thống kê tổng quan các sự kiện đang quản lý.
     - Nút "Tạo sự kiện mới" mở Modal nhập: Tên sự kiện, thể loại, chọn địa điểm (Dropdown lấy từ `/api/organizer/venues`), ngày bắt đầu, thời gian mở bán/kết thúc.
     - Bảng danh sách sự kiện: Badge trạng thái (`DRAFT` vàng cam, `PUBLISHED` xanh lá), số vé mở bán, nút "Chỉnh sửa", nút "Xuất bản" và nút "Phân công Staff".
     - Modal "Cấu hình vé": Cho phép nhập tên hạng vé, đơn giá VND, số lượng vé GA hoặc số ghế.
     - Modal "Phân công nhân viên": Chọn nhân viên từ danh sách, bấm "Gán vào sự kiện".
  2. Cập nhật `App.ts` và `DemoShell.ts`:
     - Hỗ trợ route `/organizer` dành riêng cho tài khoản có vai trò `ORGANIZER` hoặc `ADMIN`.
     - Ẩn/hiện menu "🎪 Khu vực Tổ chức" trên thanh Navbar dựa vào vai trò tài khoản thực tế.

---

## 4. CHIẾN LƯỢC KIỂM THỬ THỰC TẾ (TESTING & VERIFICATION)

Viết và chạy đầy đủ các test cases tự động:
1. **Migration Verification:** Test kiểm tra Flyway migration V12 áp dụng sạch sẽ trên PostgreSQL 17 (schema có 28 bảng).
2. **Organizer Profile Test:** Tạo hồ sơ Organizer, gán với `users(id)`.
3. **Event Lifecycle Test:**
   - Tạo sự kiện ở trạng thái `DRAFT`.
   - Cố tình gọi `publish` khi chưa có loại vé -> Nhận HTTP 400 Bad Request.
   - Thêm loại vé GA capacity 500 -> Gọi `publish` -> Chuyển thành công sang `PUBLISHED`.
   - Kiểm tra `GET /api/events` (public catalog): Sự kiện `PUBLISHED` hiển thị; sự kiện `DRAFT` bị ẩn.
4. **Event Scope & Security Isolation Test (QUAN TRỌNG):**
   - Đăng nhập với tài khoản `ORGANIZER_A` tạo Event 1.
   - Đăng nhập với tài khoản `ORGANIZER_B` gọi `PUT /api/organizer/events/{Event1_Id}` -> Bắt buộc nhận **HTTP 403 Forbidden**!
   - `ORGANIZER_B` gọi `GET /api/organizer/events` -> Không nhìn thấy Event 1 của `ORGANIZER_A`.
5. **Staff Assignment Test:**
   - Phân công một tài khoản có role `STAFF` vào sự kiện.
   - Kiểm tra bản ghi trong `event_staff_assignments`.
   - Cố tình phân công một tài khoản chỉ có role `CUSTOMER` -> Trả về lỗi 400/422.
6. **Full Regression Test Suite:**
   - Chạy lệnh: `backend/mvnw.cmd test` -> Toàn bộ các test cũ và mới đều PASS 100%.
   - Chạy lệnh: `npm.cmd test --prefix frontend` -> Toàn bộ tests frontend PASS.

---

## 5. ĐỊNH NGHĨA HOÀN THÀNH (DEFINITION OF DONE - DoD)

Task T13 chỉ hoàn thành khi:
1. Migration V12 chạy thành công trên PostgreSQL 17, dữ liệu fixtures cũ không bị ảnh hưởng.
2. Tài khoản vai trò `ORGANIZER` tự tạo được sự kiện, thêm hạng vé và xuất bản lên Public Catalog.
3. Phân quyền Event Scope hoạt động hoàn hảo: Không có lỗ hổng IDOR giữa các Organizer.
4. Phân công Staff hoạt động chuẩn xác, lưu trữ lịch sử gán/thu hồi.
5. Giao diện `/organizer` trực quan, kết nối API thật, không dùng mock local state.
6. 100% tests Backend và Frontend đều PASS.
7. Đã ghi nhận nhật ký tiến độ chi tiết (lệnh đã chạy, kết quả thực tế, bằng chứng kiểm chứng) vào `docs/PROGRESS.md` và cập nhật `docs/TASKS.md` (T13.1–T13.5 Done).
8. Tuyệt đối **không tự ý git commit** hoặc **git push**.
