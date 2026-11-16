# PROMPT — HƯỚNG DẪN THỰC THI TASK TIẾP THEO: HOÀN THIỆN GIAO DIỆN SẢN PHẨM & VISUAL QA ĐA THIẾT BỊ (TASK T18 - PHASE 7)

Bạn là **Principal UI/UX Architect & Senior Frontend Engineer** tiếp quản repository `event-ticketing-platform` để triển khai **Task T18 (Phase 7 — Hoàn Thiện Giao Diện Sản Phẩm, Design Tokens & Visual QA)** theo đúng Lộ trình Nâng cấp EventFlow V2.

---

## 1. MỤC TIÊU CỦA TASK T18

Hoàn thiện toàn diện diện mạo (UI), trải nghiệm người dùng (UX), tính nhất quán thương hiệu (Design Tokens) và kiểm thử thị giác trên đa độ phân giải (Visual QA) cho toàn bộ 4 vai trò của nền tảng EventFlow V2:

1. **Chuẩn Hóa Hệ Thống Design Tokens & Component Library (T18.1):**
   - Thống nhất bảng màu thương hiệu theo tiêu chuẩn doanh nghiệp (Sao Kim Branding palette: Primary Indigo `#4338ca` / `#6366f1`, Accent Sky `#0284c7`, Emerald Success `#059669`, Amber Warning `#d97706`, Rose Danger `#e11d48`, Dark Slate `#0f172a`).
   - Chuẩn hóa typography, hierarchy font chữ, spacing system (`4px`, `8px`, `12px`, `16px`, `24px`, `32px`), bo góc chuẩn (`border-radius: 8px, 12px, 16px, 20px`) và hiệu ứng đổ bóng `box-shadow` nhiều lớp.
   - Thống nhất các trạng thái tương tác: `hover`, `active`, `focus-visible`, `disabled`, `loading` (spinner / skeleton loader).

2. **Hoàn Thiện Giao Diện Khám Phá & Đặt Vé Sự Kiện (T18.2):**
   - Trang Chủ (`/`): Mega-menu điều hướng doanh nghiệp, hero banner tốc độ cao, danh mục nổi bật, đối tác doanh nghiệp, floating action dock.
   - Trang Sự Kiện & Đặt Vé (`/events` & `/dat-ve`): Danh sách thẻ sự kiện hiện đại, bộ lọc danh mục/địa điểm linh hoạt, tìm kiếm từ khóa realtime.
   - Sơ Đồ Ghế Trực Quan (Interactive Seat Map): Hiển thị sân khấu Stage 3D, phân khu ghế VIP/Standard, ghế đã bán/đang giữ/trống với màu sắc rõ nét, tooltip thông tin ghế và giá vé tức thì.

3. **Tối Ưu Trải Nghiệm Khách Hàng & Ví Vé E-Ticket (T18.3):**
   - Luồng Đặt Vé & Thanh Toán (`/checkout`): Countdown giữ vé 10 phút rõ ràng, tóm tắt đơn hàng sắc nét, lựa chọn phương thức thanh toán VNPAY Sandbox hoặc Mô phỏng nội bộ.
   - Trang Kết Quả Thanh Toán (`/payment-return`): Thông báo kết quả giao dịch thanh toán trực quan, hỗ trợ quay lại ví vé hoặc thử lại.
   - Trang Ví Vé Của Tôi (`/ve-cua-toi` & `/my-bookings`): Hiển thị thẻ vé sang trọng, mã QR Vector SVG ký số sắc nét, nút In Vé Điện Tử (`window.print()`) định dạng A4/vé nhiệt chuẩn.

4. **Tối Ưu Dashboard Chuyên Biệt Cho 4 Vai Trò (T18.4):**
   - **Customer Portal**: Quản lý hồ sơ, lịch sử mua vé, vé điện tử có QR code.
   - **Staff Portal (`/staff`)**: Bàn làm việc soát vé công nghệ cao, khung quét laser động, phản hồi màu sắc tức thì (Xanh/Đỏ/Vàng), âm thanh Web Audio API, thống kê check-in thời gian thực.
   - **Organizer Portal (`/organizer`)**: Quản lý hồ sơ ban tổ chức, danh sách sự kiện DRAFT/PUBLISHED/CLOSED, tạo/sửa sự kiện, thiết lập loại vé GA/ghế ngồi, phân công nhân sự soát vé.
   - **Admin Portal (`/admin`)**: 5 Tab nghiệp vụ động (Sự kiện & Kho vé, Người dùng & Phân quyền đa vai trò, Đơn hàng & Duyệt hoàn tiền, Báo cáo doanh thu KPI kèm Xuất CSV an toàn, Khách hàng Doanh nghiệp / Leads).

5. **Kiểm Thử Thị Giác Đa Thiết Bị (Visual QA) (T18.5):**
   - Sử dụng Playwright / Chrome DevTools tự động chụp ảnh màn hình bằng chứng (Screenshots) tại 3 độ phân giải tiêu chuẩn:
     - **Desktop Wide (1440 × 900)**
     - **Laptop Standard (1366 × 768)**
     - **Mobile Viewport (390 × 844 - iPhone standard)**
   - Lưu trữ các artifact ảnh chụp vào thư mục `brain` hoặc `docs/evidence/` để phục vụ nghiệm thu thị giác.

---

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI THỰC THI

1. **Kiểm tra trạng thái Git:**
   - Chạy lệnh: `git status --short --branch`, `git rev-parse HEAD`.
   - Tuyệt đối **KHÔNG commit**, **KHÔNG push Git** trừ khi có chỉ thị rõ ràng từ chủ dự án.
2. **Đọc tài liệu dự án:**
   - Đọc kỹ `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
3. **Bảo toàn tính toàn vẹn hệ thống:**
   - Không được làm gãy vỡ bất kỳ test suite nào trong số 234 test backend, 71 test frontend và 69 test contracts.
   - Bảo toàn 100% cơ chế khóa hàng nguyên tử `SELECT FOR UPDATE`, chống bán lố vé Zero Oversell, và mô hình token family của hệ thống xác thực.

---

## 3. CHECKLIST KIỂM CHỨNG BẮT BUỘC HOÀN THÀNH

- [ ] Toàn bộ 4 dashboard (Customer, Staff, Organizer, Admin) hiển thị đầy đủ, không vỡ layout trên cả 3 kích thước màn hình.
- [ ] Chạy `npm.cmd test --prefix frontend` -> PASS 100%.
- [ ] Chạy `npm.cmd run build --prefix frontend` -> `tsc --noEmit && vite build` SUCCESS.
- [ ] Chạy `& ./backend/mvnw.cmd -f backend/pom.xml test` -> 234 tests PASS 100%.
- [ ] Chạy `npm.cmd test --prefix tools/contracts` -> 69 cases PASS 100%.
- [ ] Cập nhật kết quả vào `docs/TASKS.md` và `docs/PROGRESS.md`.
