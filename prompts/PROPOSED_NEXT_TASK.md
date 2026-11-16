# PROMPT — HƯỚNG DẪN THỰC THI TASK T17: PHÂN HỆ QUẢN TRỊ HỆ THỐNG TOÀN NỀN TẢNG, BÁO CÁO DOANH THU & TRUYỀN THÔNG (TASK T17)

Bạn là **Senior Full-stack Engineer & Security Specialist** tiếp quản repository `event-ticketing-platform` để triển khai **Task T17 (Phase 6 — Quản Trị Hệ Thống, Báo Cáo & Truyền Thông / Admin & Platform Management)** theo đúng Lộ trình Nâng cấp EventFlow V2.

---

## 1. MỤC TIÊU CỦA TASK T17

Xây dựng trọn vẹn phân hệ làm việc dành riêng cho **Quản trị viên hệ thống (ADMIN)**:
1. **Quản trị Người Dùng & Phân Quyền Đa Vai Trò (User Management & RBAC Admin):**
   - API & Giao diện xem danh sách người dùng phân trang, tìm kiếm theo tên/email.
   - Phân quyền động: Cấp hoặc thu hồi các vai trò `STAFF`, `ORGANIZER`, `ADMIN` vào `ticketing.user_roles`.
   - Cập nhật trạng thái tài khoản: Khóa hoặc kích hoạt lại tài khoản người dùng (`ACTIVE`, `LOCKED`).
2. **Quản Trị Đơn Hàng, Giao Dịch Thanh Toán & Duyệt Hoàn Tiền (Orders & Refunds Admin):**
   - API xem toàn bộ đơn hàng trên hệ thống, lọc theo trạng thái (`PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`, `REFUND_REQUIRED`).
   - Xem chi tiết nỗ lực thanh toán (`payment_attempts`), nhật ký IPN webhook (`payment_notifications`) và các yêu cầu hoàn tiền (`refund_attempts`).
   - Xử lý phê duyệt hoàn tiền (`POST /api/admin/refunds/{id}/process`), cập nhật trạng thái `COMPLETED` hoặc `REJECTED`.
3. **Báo Cáo Doanh Thu, Tỷ Lệ Lấp Đầy & Xuất Dữ Liệu CSV (Reporting & CSV Export):**
   - API tổng hợp doanh số bán vé, số lượng vé theo sự kiện và theo ngày.
   - Tính toán tỷ lệ lấp đầy khán phòng (occupancy rate).
   - Xuất dữ liệu báo cáo ra định dạng CSV an toàn (RFC 4180), chống CSV Injection (sanitization formula `=, +, -, @`).
4. **Hệ Thống Gửi Thông Báo / Hóa Đơn Điện Tử (Email Notifications & Outbox Pattern):**
   - Bảng `ticketing.email_notifications` (hoặc tích hợp Outbox).
   - Tự động sinh biên nhận hóa đơn điện tử E-Receipt sau khi đơn hàng `CONFIRMED`.
   - Mô phỏng dịch vụ gửi mail (Mail Service Mock / Staging Log) không làm chậm luồng đặt vé.
5. **Tiếp Nhận & Quản Lý Leads Tư Vấn Doanh Nghiệp (Enterprise Leads Management):**
   - Bảng `ticketing.contact_leads` lưu trữ các yêu cầu gửi từ Form liên hệ `/lien-he`.
   - API Admin xem danh sách leads, cập nhật trạng thái xử lý (`NEW`, `CONTACTED`, `CONVERTED`, `CLOSED`).

---

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI THỰC THI

1. **Kiểm tra trạng thái Git:**
   - Chạy lệnh: `git status --short --branch`, `git rev-parse HEAD`.
   - **BẢO VỆ CÁC THAY ĐỔI:** Giữ working tree sạch sẽ, không chạy reset/clean.
2. **Đọc tài liệu dự án:**
   - Đọc kỹ `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
   - Đọc thiết kế chi tiết:
     - [docs/upgrade-v2/API_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/API_RECOMMENDATION.md) (Mục 2.7 — Admin Portal).
     - [docs/upgrade-v2/DATABASE_RECOMMENDATION.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/DATABASE_RECOMMENDATION.md).
     - [docs/upgrade-v2/FEATURE_BACKLOG_V2.md](file:///d:/event-ticketing-platform/docs/upgrade-v2/FEATURE_BACKLOG_V2.md) (Nhóm ADM-001 đến ADM-010).
3. **Quy tắc bất biến Migration Database:**
   - **BẤT BIẾN V1–V14:** Tuyệt đối không sửa đổi, không xóa hoặc đổi tên bất kỳ file nào từ `V1` đến `V14`.
   - Tạo migration mới nếu cần: `backend/src/main/resources/db/migration/V15__admin_leads_and_audit_enhancements.sql`.
4. **Không phá vỡ Booking Engine & Invariants Cốt Lõi:**
   - Giữ nguyên cơ chế khóa hàng `SELECT FOR UPDATE` trên `inventory_ga` và `seats`.
   - Giữ nguyên cam kết 0% bán lố vé (Zero Oversell Invariant).
   - Giữ nguyên Transactional Outbox, Inbox Deduplication và Payment Simulator phục vụ CI test suite.
   - Giữ nguyên mock bearer token profile cho các integration test hiện có.
5. **Không tự ý commit/push:** Toàn bộ mã nguồn và test phải được giữ lại trong working tree để Tech Lead nghiệm thu.

---

## 3. CÁC TIÊU CHÍ NGHIỆM THU (ACCEPTANCE CRITERIA)

1. **Phân Quyền Server-Side:**
   - Mọi API quản trị `/api/admin/**` bắt buộc được bảo vệ bởi `@PreAuthorize("hasRole('ADMIN')")`.
   - Mọi người dùng với vai trò `CUSTOMER`, `STAFF`, hoặc `ORGANIZER` truy cập API admin đều bị từ chối với HTTP 403 Forbidden.
2. **Bảo Mật Cấp Quyền (Privilege Escalation Protection):**
   - Chỉ tài khoản có vai trò `ADMIN` mới được quyền thêm hoặc xóa vai trò của người dùng.
   - Chặn Admin tự xóa vai trò `ADMIN` của chính mình (chống self-lockout).
3. **Chống CSV Injection:**
   - Khi xuất báo cáo CSV, các trường văn bản bắt đầu bằng ký tự nguy hiểm (`=`, `+`, `-`, `@`) bắt buộc được escape bằng dấu nháy đơn `'` hoặc bao bọc an toàn.
4. **Test Suite Hoàn Hảo:**
   - Toàn bộ backend test suite PASS 100%.
   - Toàn bộ frontend test suite PASS 100%.
   - `npm run build --prefix frontend` thành công không lỗi.
   - `npm test --prefix tools/contracts` PASS 69/69 ca kiểm thử OpenAPI 3.1.
