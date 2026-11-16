# PROMPT — HƯỚNG DẪN THỰC THI TASK T16: PHÂN HỆ SOÁT VÉ CHECK-IN & VẬN HÀNH SỰ KIỆN DÀNH CHO STAFF (STAFF PORTAL & ATOMIC CHECK-IN)

Bạn là **Senior Full-stack Engineer & Security Specialist** tiếp quản repository `event-ticketing-platform` để triển khai **Task T16 (Phase 5 — Soát Vé Check-in & Vận Hành Sự Kiện Dành Cho Staff)** theo đúng Lộ trình Nâng cấp EventFlow V2.

---

## 1. MỤC TIÊU CỦA TASK T16

Xây dựng trọn vẹn phân hệ làm việc dành riêng cho **Nhân viên soát vé sự kiện (STAFF)**:
1. **Flyway Migration V14 (`V14__ticket_check_ins.sql`):**
   - Tạo bảng `ticketing.ticket_check_ins` ghi nhận phiên soát vé nguyên tử.
   - Ràng buộc bất biến tuyệt đối: `CONSTRAINT uq_ticket_checked_in UNIQUE (ticket_id)` ngăn chặn 100% tình trạng quét trùng lặp vé (Anti-duplicate Scan).
2. **Backend Check-in Engine (`CheckInService`):**
   - Endpoint `POST /api/staff/events/{eventId}/check-in`:
     - `@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")`.
     - Xác thực nhân viên được phân công vào sự kiện (`event_staff_assignments.revoked_at IS NULL` hoặc vai trò `ADMIN`).
     - Hỗ trợ cả 2 phương thức: Quét mã Signed QR Token (`ticketId.ticketCode.eventId.issuedAt.signature`) và Nhập mã vé thủ công (`ticketCode`).
     - Xác minh chữ ký số HMAC-SHA256 của QR token qua `TicketQrService.verifyQrToken(...)`.
     - Kiểm tra vé thuộc đúng sự kiện và đơn hàng có trạng thái `CONFIRMED`.
     - Giao dịch nguyên tử: Chèn bản ghi vào `ticket_check_ins`. Nếu vé đã check-in trước đó, bắt lỗi ràng buộc duy nhất và từ chối ngay với HTTP 409 Conflict (`TICKET_ALREADY_USED`) kèm thông tin thời gian quét lần đầu và tên cổng.
3. **Thống Kê Soát Vé Thời Gian Thực:**
   - Endpoint `GET /api/staff/events/{eventId}/stats`: Trả về tổng vé đã phát hành, số lượng đã check-in, số lượng chưa check-in và tỷ lệ lấp đầy (%).
4. **Cổng Vận Hành Sự Kiện Staff Dashboard (`/staff` — `StaffCheckInPage.ts`):**
   - Danh sách các sự kiện mà nhân viên hiện tại được phân công (`GET /api/staff/events`).
   - Màn hình quét QR qua Camera trình duyệt (có khung quét laser) và ô nhập mã vé thủ công tức thì.
   - Phản hồi trực quan siêu nhanh (<100ms):
     - Màn hình Xanh (HỢP LỆ — MỜI VÀO): Tên khán giả, Hạng vé, Số ghế (VIP A-12).
     - Màn hình Đỏ (CẢNH BÁO QUÉT TRÙNG): *"Vé này đã được quét lúc 19:40:05 tại Cổng A!"*.
     - Màn hình Vàng (VÉ KHÔNG HỢP LỆ): Vé giả hoặc không thuộc sự kiện này.
   - Âm thanh phản hồi trực quan (Audio Beep khi thành công, Buzzer khi quét trùng).

---

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI THỰC THI

1. **Kiểm tra trạng thái Git:**
   - Chạy lệnh: `git status --short --branch`, `git rev-parse HEAD`.
   - **BẢO VỆ CÁC THAY ĐỔI:** Giữ working tree sạch sẽ, không chạy reset/clean.
2. **Đọc tài liệu dự án:**
   - Đọc kỹ `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
   - Đọc thiết kế chi tiết:
     - [docs/upgrade-v2/API_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/API_RECOMMENDATION.md) (Mục 2.6 — Staff Check-in).
     - [docs/upgrade-v2/DATABASE_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/DATABASE_RECOMMENDATION.md) (Mục 3.4 — Check-in & Audit).
     - [docs/upgrade-v2/FEATURE_BACKLOG_V2.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/FEATURE_BACKLOG_V2.md) (Nhóm STF-001 đến STF-006).
3. **Quy tắc bất biến Migration Database:**
   - **BẤT BIẾN V1–V13:** Tuyệt đối không sửa đổi, không xóa hoặc đổi tên bất kỳ file nào từ `V1` đến `V13`.
   - Tạo migration mới: `backend/src/main/resources/db/migration/V14__ticket_check_ins.sql`.
4. **Không phá vỡ Booking Engine & Invariants Cốt Lõi:**
   - Giữ nguyên cơ chế khóa hàng `SELECT FOR UPDATE` trên `inventory_ga` và `seats`.
   - Giữ nguyên cam kết 0% bán lố vé (Zero Oversell Invariant).
   - Giữ nguyên Transactional Outbox, Inbox Deduplication và Payment Simulator phục vụ CI test suite.
   - Giữ nguyên mock bearer token profile cho các integration test hiện có.
5. **Không tự ý commit/push:** Toàn bộ mã nguồn và test phải được giữ lại trong working tree để Tech Lead nghiệm thu.

---

## 3. CÁC HẠNG MỤC CẦN TRIỂN KHAI CHI TIẾT

### Hạng mục 1: Viết Migration V14 Additive Schema
- Tạo file: `backend/src/main/resources/db/migration/V14__ticket_check_ins.sql`
- Nội dung:
  ```sql
  CREATE TABLE ticketing.ticket_check_ins (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      ticket_id UUID NOT NULL REFERENCES ticketing.tickets(id) ON DELETE RESTRICT,
      event_id UUID NOT NULL REFERENCES ticketing.events(id) ON DELETE RESTRICT,
      staff_user_id UUID NOT NULL REFERENCES ticketing.users(id) ON DELETE RESTRICT,
      gate_name VARCHAR(64) NOT NULL DEFAULT 'MAIN_GATE',
      device_info TEXT,
      checked_in_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
      CONSTRAINT uq_ticket_checked_in UNIQUE (ticket_id) -- RÀNG BUỘC BẢO ĐẢM KHÔNG QUÉT TRÙNG NGUYÊN TỬ
  );
  CREATE INDEX idx_check_ins_event ON ticketing.ticket_check_ins(event_id, checked_in_at);
  CREATE INDEX idx_check_ins_staff ON ticketing.ticket_check_ins(staff_user_id, checked_in_at);
  ```

---

### Hạng mục 2: Triển Khai Backend Check-in Engine & Security Scope
- Package: `backend/src/main/java/com/haolo/ticketing/checkin/`
- Công việc:
  1. Tạo Entity / DTO:
     - `CheckInRequest(String qrToken, String ticketCode, String gateName, String deviceInfo)`
     - `CheckInResult(String status, UUID ticketId, String ticketCode, String attendeeName, String ticketType, String seatLabel, String checkedInAt, String gateName, String firstCheckInAt, String message)`
     - `CheckInStats(long totalSold, long checkedInCount, long remainingCount, double checkInRatePercent)`
  2. Xây dựng `StaffCheckInService`:
     - `verifyStaffAssignment(UUID staffUserId, UUID eventId)`: Kiểm tra bản ghi trong `ticketing.event_staff_assignments` có `revoked_at IS NULL` (nếu user có role `ADMIN` thì bypass).
     - `performCheckIn(UUID staffUserId, UUID eventId, CheckInRequest req)`:
       - Giải mã và xác thực QR token qua `TicketQrService.verifyQrToken(...)` (nếu gửi `qrToken`) hoặc tra cứu theo `ticketCode`.
       - Truy vấn thông tin vé: `SELECT t.*, b.status as booking_status, b.account_id, u.display_name, tt.name as ticket_type_name, s.label as seat_label FROM ticketing.tickets t JOIN ticketing.bookings b ON b.id = t.booking_id JOIN ticketing.users u ON u.id = b.account_id JOIN ticketing.ticket_types tt ON tt.id = b.ticket_type_id LEFT JOIN ticketing.booking_items bi ON bi.id = t.booking_item_id LEFT JOIN ticketing.seats s ON s.id = bi.seat_id WHERE ...`.
       - Kiểm tra vé thuộc đúng sự kiện (`t.event_id = eventId` hoặc `b.event_id = eventId`). Nếu sai -> ném lỗi `ApiException(400, "EVENT_MISMATCH", "Vé không thuộc sự kiện này!")`.
       - Kiểm tra trạng thái đơn hàng phải là `CONFIRMED`. Nếu không -> ném lỗi `ApiException(400, "TICKET_NOT_CONFIRMED", "Vé chưa hoàn tất thanh toán!")`.
       - Ghi nhận check-in nguyên tử:
         ```sql
         INSERT INTO ticketing.ticket_check_ins (ticket_id, event_id, staff_user_id, gate_name, device_info)
         VALUES (:ticketId, :eventId, :staffUserId, :gateName, :deviceInfo)
         ```
       - Bắt ngoại lệ `DuplicateKeyException` (quét trùng lặp vé):
         Truy vấn bản ghi check-in đầu tiên từ `ticketing.ticket_check_ins` và trả về kết quả lỗi `409 CONFLICT` với chi tiết:
         ```json
         {
           "status": "ALREADY_CHECKED_IN",
           "code": "TICKET_ALREADY_USED",
           "message": "CẢNH BÁO: Vé này đã được quét lúc 19:40:05 tại Cổng A!",
           "firstCheckInAt": "2026-11-16T19:40:05Z",
           "gate": "GATE_A"
         }
         ```
       - Nếu thành công -> Trả về `200 OK` kèm thông tin khách và số ghế.
  3. Xây dựng `getEventStats(UUID eventId)`: Đếm tổng vé đã phát hành và số lượng trong `ticket_check_ins`.

---

### Hạng mục 3: Triển Khai REST API Cho Staff
- Tạo Controller: `backend/src/main/java/com/haolo/ticketing/checkin/StaffCheckInController.java`
- Khai báo: `@RestController`, `@RequestMapping("/api/staff")`, `@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")`.
- Endpoints:
  1. `GET /api/staff/events`:
     - Trả về danh sách các sự kiện mà nhân viên hiện tại được phân công (`WHERE staff_user_id = principal.accountId() AND revoked_at IS NULL`).
  2. `POST /api/staff/events/{eventId}/check-in`:
     - Tiếp nhận request quét vé, thực hiện soát vé nguyên tử.
  3. `GET /api/staff/events/{eventId}/stats`:
     - Thống kê tiến độ soát vé của sự kiện theo thời gian thực.
  4. `GET /api/staff/events/{eventId}/history`:
     - Lịch sử 50 lượt soát vé gần nhất của nhân viên tại cổng.

---

### Hạng mục 4: Triển Khai Giao Diện Staff Portal (`/staff` — `StaffCheckInPage.ts`)
- Thư mục: `frontend/src/app/`
- Công việc:
  1. Xây dựng `frontend/src/app/StaffCheckInPage.ts`:
     - Route: `/staff`.
     - Phân quyền: Chỉ cho phép tài khoản có vai trò `STAFF` hoặc `ADMIN`.
     - Bộ chọn sự kiện: Dropdown các sự kiện được phân công.
     - Khung quét Camera Viewport: Hỗ trợ kích hoạt camera thiết bị để tự động đọc mã QR, hoặc nút chọn ảnh QR, kèm ô nhập mã vé thủ công (phòng khi camera mờ/hỏng).
     - Trạng thái phản hồi tức thì:
       - **Màn hình XANH LÁ (Thành công):** Âm thanh Beep, hiển thị to rõ Họ tên khách, Loại vé, Số ghế (VIP-A1), Cổng vào.
       - **Màn hình ĐỎ (Cảnh báo quét trùng):** Âm thanh Buzzer, rung nhẹ điện thoại, cảnh báo vé đã quét lúc mấy giờ.
       - **Màn hình VÀNG (Lỗi):** Vé sai sự kiện, vé chưa thanh toán hoặc mã QR giả mạo.
     - Thanh tiến độ thống kê realtime: `Đã check-in: 1.420 / 2.000 vé (71%)`.
  2. Cập nhật `App.ts` và `DemoShell.ts`:
     - Đăng ký route `/staff`.
     - Bổ sung nút "📱 Soát vé" trên thanh Navbar cho tài khoản vai trò `STAFF` hoặc `ADMIN`.

---

## 4. CHIẾN LƯỢC KIỂM THỬ THỰC TẾ (TESTING & VERIFICATION)

Viết và chạy đầy đủ các test cases tự động:
1. **Migration Verification Test (`V14MigrationVerificationTest`):**
   - Kiểm tra Flyway migration V14 chạy thành công trên PostgreSQL 17 (tổng 33 bảng).
2. **Staff Scope Security Test:**
   - Nhân viên A được phân công vào Event 1 -> Soát vé Event 1 thành công.
   - Nhân viên A cố tình soát vé Event 2 (không được phân công) -> Bắt buộc nhận **HTTP 403 Forbidden**!
   - Tài khoản vai trò `CUSTOMER` cố tình gọi API check-in -> Nhận **HTTP 403 Forbidden**!
3. **Atomic Anti-duplicate Scan Concurrency Test (QUAN TRỌNG):**
   - Giả lập 1 vé hợp lệ ở trạng thái `CONFIRMED`.
   - Gửi đồng thời 10 request check-in cùng 1 mã vé đó:
     - Đúng **1 request** nhận HTTP 200 OK (`CHECKED_IN`).
     - **9 request còn lại** bắt buộc nhận HTTP 409 Conflict (`ALREADY_CHECKED_IN`).
     - Bảng `ticket_check_ins` chỉ có duy nhất **1 bản ghi**!
4. **QR Signature Tampering Test:**
   - Gửi QR token bị sửa đổi chữ ký -> Nhận lỗi từ chối vé giả mạo.
5. **Full Regression Test Suite:**
   - Chạy lệnh: `backend/mvnw.cmd test` -> Toàn bộ tests backend PASS 100%.
   - Chạy lệnh: `npm.cmd test --prefix frontend` -> Toàn bộ tests frontend PASS 100%.

---

## 5. ĐỊNH NGHĨA HOÀN THÀNH (DEFINITION OF DONE - DoD)

Task T16 chỉ hoàn tất khi:
1. Migration V14 chạy sạch sẽ trên PostgreSQL 17.
2. Endpoint `POST /api/staff/events/{eventId}/check-in` soát vé chuẩn xác, xác thực chữ ký HMAC-SHA256 và chống quét trùng nguyên tử 100% bằng SQL constraint.
3. Phân quyền Event Scope của Staff được bảo đảm nghiêm ngặt: Staff chỉ được soát vé sự kiện được giao.
4. Giao diện `/staff` hoạt động mượt mà trên cả máy tính và di động, có camera scan và nhập mã thủ công.
5. 100% tests Backend và Frontend đều PASS.
6. Cập nhật nhật ký tiến độ vào `docs/PROGRESS.md` và `docs/TASKS.md` (đánh dấu T16.1–T16.4 Done).
7. Tuyệt đối **không tự ý git commit** hoặc **git push**.
