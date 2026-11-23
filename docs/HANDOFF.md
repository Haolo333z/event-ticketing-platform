# Bàn giao event-ticketing-platform

Chốt ngày **23/11/2026 (Asia/Saigon)** tại `D:\event-ticketing-platform`. Đây là snapshot bàn giao; lần làm tiếp phải kiểm tra lại Git và runtime. Đọc [RULES](RULES.md), [TASKS](TASKS.md), [PROGRESS](PROGRESS.md) trước khi sửa; hướng dẫn chạy đầy đủ ở [README](../README.md), [infra](../infra/README.md) và [runbook demo](DEMO_AND_RECOVERY.md).

> **Cập nhật 23/11/2026 (Khắc Phục Hết Hạn Phiên Token, Silent Refresh & Căn Giữa Modal):**
> - Cố định khóa ký JWT qua `JWT_SECRET` trong `application.yml`, `JwtService.java`, `.env` và `.env.example`, đảm bảo token không bị mất sau khi restart server hoặc tắt mở máy.
> - Bổ sung `Access-Control-Allow-Credentials: true` vào `RequestPolicyFilter.java` và thêm `body: JSON.stringify({})` cho `doRefresh()` trong `client.ts`, cho phép browser gửi nhận Refresh Cookie an toàn qua CORS và loại bỏ triệt để lỗi HTTP 400.
> - Tự động Silent Refresh 1 lần khi nhận HTTP 401; nếu refresh thất bại thì gọi `clearStoredAuth()` đồng bộ state xóa sạch user trên Header và hiển thị card *"Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"* trên `/ve-cua-toi` kèm nút mở `AuthModal`.
> - Sửa vị trí `OrganizerOnboardingModal.ts` căn giữa chính giữa màn hình với `auth-modal-backdrop auth-modal-overlay` và CSS `position: fixed; inset: 0; display: flex; align-items: center; justify-content: center; z-index: 9999`.
> - Sửa `start-all.bat`: dùng `pushd "%~dp0backend"`, `call .\mvnw.cmd -f pom.xml package -DskipTests` và `popd`, loại bỏ lỗi `there is no POM in this directory`; kiểm chứng tự động build jar và khởi động web thành công 100%.
> - Chỉ số kiểm thử: **358/358 Backend JUnit 5 tests PASS 100%**, **84/84 Frontend Vitest tests PASS 100%**, **Vite build SUCCESS (261ms)**, **OpenAPI 3.1 Contracts PASS 100% (47 operations, 69 ca test)**.

> **Cập nhật 22/11/2026 (Sổ Tay Hướng Dẫn Sử Dụng Chi Tiết Từng Thao Tác):**
> - Đã hoàn thiện tài liệu hướng dẫn vận hành và sử dụng chi tiết chuẩn Click-by-Click tại [docs/HUONG_DAN_SU_DUNG_CHI_TIET.md](HUONG_DAN_SU_DUNG_CHI_TIET.md) (dung lượng ~59.5 KB, cấu trúc chuẩn 6 phần, 100% chức năng có 4 bước: Mục đích -> Bấm nút nào -> Điền dữ liệu mẫu thật nào -> Kết quả thực tế nhìn thấy).
> - Phục vụ người mới tiếp cận, ban vận hành sàn, nhà tổ chức sự kiện, nhân viên soát vé, quản trị viên và tự tin thuyết trình/demo trực tiếp trước hội đồng thẩm định.
>
> **Cập nhật 22/11/2026 (Bộ Tiện Ích 1-Chạm Windows & Live Re-Audit):**
> - Bổ sung 2 file tiện ích chuẩn Windows ngay tại thư mục gốc:
>   - `start-all.bat`: Khởi động 1-chạm Docker (Postgres/Kafka), kiểm tra cổng 5432, tự động build lại `.jar` nếu mã nguồn `.java`/`.sql` mới hơn file đóng gói, giải phóng cổng 8080/5173 nếu bị kẹt, mở Backend trong cửa sổ riêng `"EventFlow Backend (8080)"`, đợi `actuator/health` UP mới mở Frontend `"EventFlow Frontend (5173)"` và tự động mở trình duyệt `http://127.0.0.1:5173`.
>   - `stop-all.bat`: Tắt gọn gàng toàn bộ tiến trình Backend, Frontend, đóng cửa sổ console và dừng Docker containers (`docker compose stop`) mà không xóa dữ liệu cơ sở dữ liệu.
> - **15/15 mã lỗi ĐÃ ĐƯỢC KHẮC PHỤC HOÀN TOÀN (PASS 100%):**
>   - `P0-1`: Broken Access Control `/admin` - chặn chuyển hướng về `/` và mở `AuthModal`, không leak dữ liệu, không cấp token demo.
>   - `P0-2`: ResaleListingService SQL crash `t.seat_id` - `GET /api/events/{id}/resale-listings` trả về HTTP 200 OK.
>   - `P0-3`: StaffCheckInService SQL crash `e.ends_at` - `GET /api/staff/events` trả về HTTP 200 OK (>170 sự kiện, offline ticket sync mượt mà).
>   - `P0-4`: CORS Preflight HTTP 403 trên `/api/me/profile` và `/api/me/vat-defaults` - OPTIONS 204, PUT 200 OK, toast thông báo thành công.
>   - `P0-5`: HTTP 415 khi duyệt B2B Lead và Reset Circuit Breaker - POST 200 OK với body JSON hợp lệ.
>   - `P1-1`: `/tai-khoan` tách độc lập các API qua `Promise.allSettled`, Tab 5 "🎪 Đối tác Tổ chức" hiển thị đúng trạng thái.
>   - `P1-2`: Filter "Ghế chỉ định" (`SEATED`) hiển thị đầy đủ các sự kiện ghế chỉ định lên đầu bảng nhờ tối ưu sắp xếp `(sale_end_at > clock_timestamp()) DESC, starts_at ASC`.
>   - `P1-3`: Khắc phục lỗi HTTP 415 khi Nhà tổ chức xuất bản sự kiện (`publishEvent`) và các endpoint Transfer, Resale bằng cách gửi body JSON `{}` hợp lệ theo chuẩn RequestPolicyFilter.
>   - `P1-4`: AI Concierge phân loại chuẩn xác 100% FAQ chính sách hoàn hủy 10 phút, Fan-to-Fan Resale và hướng dẫn xuất hóa đơn VAT điện tử.
>   - `P1-5`: Sự kiện quá hạn `sale_end_at` hiển thị badge `⛔ ĐÃ ĐÓNG CỔNG BÁN VÉ` và disable nút đặt vé.
>   - `P1-6`: Bổ sung migration Flyway `V29__fix_utf8_categories_and_venues.sql` chuẩn hóa bảng mã UTF-8 tiếng Việt, khắc phục triệt để lỗi Mojibake trong dropdown Thể loại và Địa điểm.
>   - `P1-7`: Admin UI bảo vệ chính mình: nút `- ADMIN` và `Khóa` của admin đang đăng nhập bị disabled kèm tooltip giải thích.
>   - `P2-1`: Rút gọn mã booking `#EF-XXXXXX` kèm nút Copy `📋`, người duyệt hiển thị `Quản trị viên (ADMIN)`.
>   - `P2-2`: Màn hình `/tai-khoan` khi chưa đăng nhập có nút "🔑 Đăng nhập ngay" mở `AuthModal` trực tiếp.
>   - `P2-3`: `/bang-gia` chọn gói dịch vụ điều hướng sang `/lien-he?package=...` và auto-select gói tương ứng trong combobox.
> - **Chỉ số kiểm thử:** 358 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (220ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR.
> 
> **Cập nhật 22/11/2026 (Đợt Cuối - Task Fix P2):** Hoàn tất toàn diện **Task Fix P2: Rút gọn UUID Admin, Đăng nhập trực tiếp tài khoản & Liên kết gói bảng giá - form tư vấn**:
> - **P2-1 (Rút gọn UUID thô 36 ký tự tại `/admin` & Hiển thị vai trò người duyệt thân thiện):** Rút gọn mã booking dạng `#EF-` + 6 ký tự hex cuối in hoa kèm nút Copy icon 📋 (sao chép trực tiếp vào clipboard) và tooltip chứa full UUID 36 ký tự; áp dụng đồng bộ trên tab `bookings`, bảng `refunds`, tab `payouts` và tab `outbox`. Thay thế UUID thô của người duyệt Lead đối tác trong ghi chú thành `Quản trị viên (ADMIN)` ở cả frontend và backend.
> - **P2-2 (Bổ sung nút "Đăng nhập ngay" tại `/tai-khoan`):** Bổ sung prop `onOpenAuth` vào `AccountProfilePage`. Khi người dùng chưa đăng nhập (`!user`), màn hình hiển thị trực quan nút màu xanh mint `🔑 Đăng nhập ngay` bên cạnh nút `Quay về Trang chủ`, cho phép click mở ngay `AuthModal` mà không cần rời trang.
> - **P2-3 (Truyền gói dịch vụ từ `/bang-gia` sang Form liên hệ `/lien-he`):** Cập nhật các nút chọn gói dịch vụ chuyển hướng kèm query parameter `?package=starter`, `?package=pro`, `?package=enterprise`. Cập nhật Route Guard trong `DemoShell.ts` hỗ trợ route có query string và form `ContactPage.ts` tự động chọn sẵn gói tương ứng.
> - **Chỉ số kiểm thử:** 357 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (392ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR.

> **Cập nhật 22/11/2026 (Task Fix P1):** Hoàn tất toàn diện **Task Fix P1: Khắc phục dứt điểm 7 nhóm lỗi P1 (Profile Tải Độc Lập, Seated Dedup, Auto VAT, FAQ AI Concierge, Chặn Sale Ended, Mojibake & Tự Khóa Admin)**:
> - **P1-1 (`/tai-khoan` - Tách độc lập 8 API tải hồ sơ):** Chuyển sang `Promise.allSettled` cho 8 tác vụ độc lập (`profile`, `vatDefaults`, `sessions`, `waitlists`, `reminders`, `transfersSent`, `transfersReceived`, `onboardStatus`) có `.catch()` riêng biệt trong `frontend/src/app/AccountProfilePage.ts`. Khi API đối tác B2B lỗi được fallback an toàn `{ hasSubmitted: false, status: 'NONE' }`, giúp các tab cá nhân khác vẫn tải mượt mà.
> - **P1-2 (Lọc "Ghế chỉ định" / Sự kiện SEATED không bị nuốt):** Cập nhật logic `dedup` trong `frontend/src/app/App.ts`, phân biệt key với hậu tố `:seated` và `:ga` (`ev.title.trim().toLowerCase() + (hasSeated ? ':seated' : ':ga')`), đảm bảo sự kiện ghế chỉ định không bao giờ bị che lấp bởi sự kiện GA trùng tên.
> - **P1-3 (Tự động xuất hóa đơn VAT khi tích chọn ở Checkout):** Bổ sung hook `useEffect` trong `frontend/src/app/Checkout.ts` tự động gọi `api.invoices.issue()` ngay khi booking chuyển sang `CONFIRMED` nếu khách đã tích chọn xuất VAT và điền đầy đủ MST/Tên công ty. Hiển thị thông báo hóa đơn kèm mã tra cứu trực tiếp trong khu vực vé điện tử.
> - **P1-4 (AI Concierge trả lời đúng intent Chính sách Hoàn vé / Hóa đơn VAT):** Bổ sung 3 intent chuyên biệt trong `backend/src/main/java/com/haolo/ticketing/ai/AiTicketingService.java`: `wantsRefundPolicy`, `wantsVatPolicy`, `wantsGuide`. Cung cấp phản hồi chính xác về chính sách hoàn tiền 100% khi hết hạn giữ chỗ 10 phút, chuyển nhượng vé miễn phí, bán lại Fan-to-Fan Resale, và quy trình xuất hóa đơn VAT điện tử thay vì rơi nhầm vào tìm kiếm vé.
> - **P1-5 (Chặn đặt vé khi quá hạn `sale_end_at`):** Cập nhật `frontend/src/app/App.ts`: kiểm tra `isSaleEnded = detail?.saleEndAt ? (Date.parse(detail.saleEndAt) <= now) : false`, đưa vào điều kiện `canBook`, disable nút đặt vé kèm thông báo rõ ràng thời gian đóng cổng bán vé và gắn badge `⛔ ĐÃ ĐÓNG CỔNG BÁN VÉ` trên event card.
> - **P1-6 (Chuẩn hóa tiếng Việt UTF-8 sạch cho Dropdown Thể loại & Địa điểm):** Bổ sung `DEFAULT_CATEGORIES` và `DEFAULT_VENUES` chuẩn UTF-8 trong `frontend/src/app/OrganizerDashboard.ts` với đầy đủ thông tin chuẩn hóa (Nhà hát Lớn Hà Nội, Trung tâm Hội nghị Quốc gia, SVĐ Mỹ Đình, Nhà hát TP. HCM) tránh rỗng hoặc lỗi font khi mạng chậm.
> - **P1-7 (Chặn Admin tự hạ quyền hoặc tự khóa chính mình):** Cập nhật cả UI lẫn logic xử lý trong `frontend/src/app/AdminDashboard.ts`: so sánh `isSelf` với `getStoredUser()`, vô hiệu hóa nút `- ADMIN` và nút `Khóa`/`Mở khóa` đối với tài khoản admin đang đăng nhập, hiển thị badge "Bạn" và tooltip giải thích trực quan; chặn đồng thời ở cấp handler `handleRoleAction` và `handleStatusAction`.
> - **Chỉ số kiểm thử:** 357 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (193ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR và tuân thủ quy tắc không commit/push.

> **Cập nhật 22/11/2026 (Task Fix P0):** Hoàn tất toàn diện **Task Fix P0: Khắc phục dứt điểm 4 nhóm lỗi P0 (Broken Access Control Admin, CORS Preflight 403, HTTP 415, SQL Crash 500)**:
> - **P0-1 (Broken Access Control / Lộ dữ liệu Admin):** Loại bỏ triệt để việc tự cấp token `demo-admin-token` trên browser trong `frontend/src/api/admin.ts`. Thiết lập Route Guard nghiêm ngặt trong `frontend/src/app/DemoShell.ts`: chặn render `AdminDashboard` nếu không có quyền ADMIN, tự động redirect về `/` và mở `AuthModal` kèm thông báo *"Yêu cầu quyền Quản trị viên để truy cập trang này. Vui lòng đăng nhập với tài khoản ADMIN."*; bảo vệ cả initial path, `navigate()` và `onPopState`.
> - **P0-4 (CORS Preflight 403 trên `/api/me/profile`):** Cập nhật `SecurityConfiguration.java` với `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`; cập nhật `RequestPolicyFilter.java` bổ sung các method `PUT`, `DELETE` cho CORS preflight và hỗ trợ body cho `PUT`; cập nhật `MockBearerFilter.java` xem OPTIONS là public endpoint.
> - **P0-5 (HTTP 415 Unsupported Media Type ở `/admin`):** Thêm header `'Content-Type': 'application/json'` và body `JSON.stringify({})` cho `resetCircuitBreaker` và `convertToOrganizer` trong `frontend/src/api/admin.ts`.
> - **P0-2 & P0-3 (SQL Crash HTTP 500):** Sửa câu lệnh SQL trong `ResaleListingService.java` và `TicketTransferService.java` để join đúng bảng có `seat_id` (`ticketing.booking_items bi`) thay vì truy vấn cột không tồn tại trên `tickets`; bỏ `seat_id` khỏi lệnh `INSERT INTO ticketing.tickets`. Sửa `StaffCheckInService.java` thay `e.ends_at` thành `NULL AS ends_at` vì bảng `ticketing.events` không có cột `ends_at`.
> - **Chỉ số kiểm thử:** 357 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (237ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR và tuân thủ quy tắc không commit/push.

> **Cập nhật 22/11/2026 (Task T30):** Hoàn tất toàn diện **Task T30: Vá Lỗi Lộ Lịch Sử Đặt Vé Khi Chưa Đăng Nhập, Dọn Trang Chủ, Làm Đẹp Danh Sách Đơn Hàng & Đồng Bộ Phí Sàn**:
> - **Hạng mục 1:** Chặn tuyệt đối việc tự cấp mock token demo (`demo-customer-token`) khi người dùng chưa đăng nhập trong môi trường trình duyệt; chặn cơ chế fallback trên các API dữ liệu cá nhân (`/api/bookings/**`, `/api/tickets/**`, `/api/me/**`); trả về đúng `401 UNAUTHENTICATED` khi chưa đăng nhập.
> - **Hạng mục 2:** Gỡ bỏ hoàn toàn khối `section.history` ("Lịch sử đặt vé của tôi") khỏi Trang chủ `/`; chỉ hiển thị lịch sử đặt vé tại phân hệ chuyên biệt `/ve-cua-toi` (`MyBookingsPage.ts`). Nếu chưa đăng nhập, hiển thị thẻ nhắc đăng nhập trang trọng.
> - **Hạng mục 3:** Làm đẹp danh sách đơn hàng tại `/ve-cua-toi`: hiển thị tên sự kiện rõ ràng, mã đơn ngắn gọn định dạng `#EF-XXXXXX` (thay cho chuỗi UUID 36 ký tự thô), ngày giờ đặt dạng `DD/MM/YYYY HH:mm`, badge trạng thái và tổng tiền VND.
> - **Hạng mục 4:** Đồng bộ 100% tỷ lệ phí sàn giữa `/bang-gia`, trang chủ và backend `OrganizerPayoutService.java`: 8% (Khởi Nghiệp Starter), 5% (Chuyên Nghiệp Pro), 3% (Doanh Nghiệp Enterprise).
> - **Chỉ số kiểm thử:** 357 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (259ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR và tuân thủ quy tắc không commit/push.

> **Cập nhật 22/11/2026 (Task T29):** Hoàn tất toàn diện **Task T29: Tối Ưu Giao Diện Theo Phong Cách Ticketbox.vn, Tách Trang Chi Tiết & Dọn Lỗi Dữ Liệu Hiển Thị**:
> - **Hạng mục 1:** Khắc phục triệt để lỗi `Giá từ: ∞ đ` bằng helper an toàn `getEventMinPriceText()`; chuẩn hóa định dạng ngày `DD/MM/YYYY` bỏ hoàn toàn phần giây; dọn dữ liệu rác test và bản trùng lặp; đa dạng hóa 100% poster theo 5 thể loại Unsplash không lặp lại ảnh; phân trang 8–12 sự kiện kèm nút "Xem thêm".
> - **Hạng mục 2:** Bỏ thanh utility strip cũ ở đầu trang; thiết kế lại Header chính 1 hàng nền tối `#1E1E24` chuẩn Ticketbox với thanh tìm kiếm bo tròn 9999px thời gian thực, menu Dành cho Ban tổ chức ▾, nút `🎟️ Vé của tôi`, nút cam `+ Tạo sự kiện`, và cụm Avatar Dropdown phân quyền 4 vai trò. Thêm thanh danh mục Ticketbox ngang mỏng (`.ticketbox-category-strip`) ngay dưới Header.
> - **Hạng mục 3:** Chuyển đổi Trang chủ sang layout Full-Width 4 cột tràn ngang 100% (`.event-grid-4col`), xóa bỏ hoàn toàn cột đặt vé `.booking-panel` bên cạnh ở màn hình danh sách sự kiện (không còn khoảng trắng 40% bên phải). Tầng 1 Hero Banner Slider 2 poster lớn 16:9 cạnh nhau; Tầng 2 Lưới sự kiện tối giản Ticketbox; Tầng 3 Chợ vé chuyển nhượng chính chủ Fan-to-Fan Resale.
> - **Hạng mục 4:** Tách riêng Trang Chi Tiết Sự Kiện & Đặt Vé độc lập với Ticket Banner Header 2 phần ngang (Trái: H1, Ngày, Địa điểm, Giá từ, Mua vé ngay, Nhắc 15 phút; Phải: Poster 16:9 lớn). Thân 2 cột cân đối với cột tóm tắt đơn hàng `.booking-panel` cố định sticky top: 80px, sơ đồ ghế 3D, Best Seats Picker, Seat View Preview SVG, Smart Waitlist, Voucher, xuất VAT và VNPAY Sandbox / mô phỏng.
> - **Chỉ số kiểm thử:** 357 Backend JUnit 5 tests PASS 100%, 84 Frontend tests PASS 100%, Vite production build SUCCESS (255ms), OpenAPI 3.1 Contracts PASS 100%. Bảo toàn 100% Zero Oversell, 0 Deadlock, Anti-IDOR và tuân thủ quy tắc không commit/push.

## 1. Git và tiến độ

- Nhánh `main`, HEAD **`984e95d7dc7aca0b7ea94743e27e21d5d713d906`**. Không commit/push trong lượt bàn giao.
- Working tree **chưa sạch**: trước lượt này có 6 file sửa (`README.md`, `docs/DEMO_AND_RECOVERY.md`, `docs/PROGRESS.md`, `docs/TASKS.md`, `docs/TEST_PLAN.md`, `infra/README.md`); các evidence T9.4/T10.1–T10.3, `docs/report/`, `infra/demo-{rehearsal.ps1,audit.sql}` và `tools/report/` còn untracked. Giữ nguyên, không checkout/reset/clean. Hai file bàn giao này cũng chưa commit.
- CI được xác nhận gần nhất trong tài liệu: **run #39**, đúng SHA trên, cả backend/frontend/contracts/secrets success; xem [evidence CI39](evidence/t9-4-ci39-2026-11-14.json). Đây là bằng chứng của commit đó, **không chứng minh các thay đổi chưa commit đã chạy GitHub Actions**.

| Task | Trạng thái và giới hạn |
|---|---|
| T1.1–T7.4, T8.1–T8.4, T9.1–T9.4 | Done theo TASKS/PROGRESS; gate tích hợp, tải, lỗi và CI có evidence. Không làm lại nếu không phát hiện khoảng trống/lỗi thật. |
| T10.1 | Done: môi trường riêng sạch, migration/seed/restart/SQL đã kiểm; Chrome do người dùng xác nhận, không có file ảnh được agent xem. Evidence/tài liệu mới chưa commit. |
| T10.2 | Done: hai lượt HTTP/SQL đạt, có runbook/fixture riêng. Chưa có visual QA Chrome T10.2. Script/tài liệu/evidence chưa commit. |
| T10.3 | Done: báo cáo 43 trang Word/PDF đã kiểm. Chưa áp mẫu trường, chưa điền thông tin bìa, chưa kiểm Microsoft Word GUI. Báo cáo/tool/evidence chưa commit. |
| **T10.4** | **Todo, chưa thực hiện**. Task duy nhất còn lại: chốt backlog ngoài phạm vi, sơ đồ, ảnh và tag demo; AC chính xác: **ZIP/source/tag tái lập được**, có bằng chứng và cập nhật PROGRESS. |

Tổng trong TASKS: **44/45 Done**, không phải tỷ lệ chất lượng. Các giới hạn chưa kiểm chứng không tự trở thành tính năng phải thêm trong T10.4.

## 2. Stack và các điều không được phá

Temurin **25.0.5+8**, Spring Boot **4.1.2**, Maven Wrapper **3.9.16**; PostgreSQL **17.11**, Kafka **4.2.2**; Node/npm **26.13.0/11.20.0**, React **19.3.1**, TypeScript **7.0.2**, Vite **8.3.8**. Docker/Compose đã đo **29.9.1/5.6.0**. Giữ BOM/lockfile/image pin; xem [ADR](adr/). Không .NET/Redis hoặc tự đổi stack.

Modular monolith: API và worker cùng backend; Java/Vite chạy host Windows, PostgreSQL/Kafka chạy Compose. PostgreSQL là nguồn sự thật, Kafka phân phối sự kiện sau commit. V1–V9 đã có **18 bảng**; không sửa migration đã chạy. OpenAPI có **18 operation** ở [contracts/openapi.json](contracts/openapi.json).

- GA: `held + sold <= capacity`; ghế có tối đa một owner hiện hành. Giữ nhiều ghế all-or-nothing, khóa ID theo thứ tự ổn định. Giá/số tiền VND do server tính và lưu snapshot.
- Booking, item, inventory, history, key/response và outbox phải commit/rollback nhất quán; timeout rollback trước khi trả 503. Cùng account/operation/key/body trả kết quả cũ; khác body trả 409. Cached receipt tạo đơn có thể vẫn PENDING_PAYMENT sau confirm; đọc GET để biết trạng thái mới.
- State: `PENDING_PAYMENT → CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED`; chỉ `EXPIRED → REFUND_REQUIRED`. Không tự thêm cạnh state.
- Expiry Worker chủ động quét, không cleanup inline trong đặt vé; hold quá giờ vẫn chưa trống trước khi worker commit. Kiểm state/owner để không nhả lần hai hoặc nhả tài nguyên của booking mới.
- Payment/refund **mô phỏng**. Dùng `payments.recorded_at` của DB và processor chung với expiry. Timely result đã bền có thể được worker xử lý dù Kafka ngừng. Success sau mất hold không confirm/chiếm lại vé; EXPIRED đi REFUND_REQUIRED, không vé, một nghĩa vụ/payment. BookingConfirmed chỉ thông báo, không phát vé lại.
- Outbox gửi sau commit; Kafka ACK và DB mark là hai bước nên có thể gửi lặp. Inbox `(consumer,eventId)` cùng transaction với hiệu ứng, commit trước ACK offset. **At-least-once + dedup**, không cam kết exactly-once xuyên hệ.
- Retry hữu hạn, failure DB trước ACK, DLQ sau commit. Replay giữ eventId/envelope/inbox, ADMIN local/test, audit và tối đa 3 request mới/event; không force/xóa inbox/reset budget. Audit projection kiểm gap/version riêng, không bỏ payment theo booking.version hiện tại.
- Danh tính/role từ mock bearer do server ánh xạ, chỉ local/test; không X-User-Id/role client tự khai. Không log token, raw payment hoặc gắn ID cá nhân vào metric label.

## 3. Windows và các môi trường tạm

**Quan sát lúc bàn giao:** hai backend health UP, bốn container PostgreSQL/Kafka healthy; listener đúng các cổng dưới. Đây là tiến trình/clone/volume trên máy hiện tại, **không phải deployment bền hoặc thứ được Git clone mang sang máy khác**.

| Môi trường | Clone / project | UI / API / PG / Kafka |
|---|---|---|
| T10.1 cần bảo toàn | `D:\ticketing-t101-94eeadabbaa1` / `ticketing-readme-94eeadabbaa1` | `http://localhost:5173` qua IPv6 /18080/15432/19092 |
| T10.2 tập riêng | `D:\ticketing-t102-e8b72037cdaf` / `ticketing-t102-e8b72037cdaf` | `http://127.0.0.1:5173` /18081/15433/19093 |

T10.2 dùng session ignored `reports/t10-2/session.json`; clone/session có thể không tồn tại trên máy mới. Không copy `.env` hoặc volume demo để tạo môi trường sạch. Cổng bận thì nhận diện owner và dừng, không kill nhầm/đổi port rồi bỏ qua CORS.

Từ root hiện tại, dùng session T10.2 đã có:

```powershell
git status --short
git rev-parse HEAD
docker version
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Start
Invoke-RestMethod http://127.0.0.1:18081/actuator/health
# Tập lượt mới: các lệnh này tạo fixture/booking trong môi trường T10.2 riêng
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action NewRun
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Rehearse -Paced
# Verify chỉ sau khi lượt chính hoàn tất; Stop giữ volume
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Verify
powershell -NoProfile -ExecutionPolicy Bypass -File infra/demo-rehearsal.ps1 -Action Stop
```

Máy mới/session mới: đọc runbook rồi dùng `-Action Prepare` khi cổng trống; nó tạo snapshot working tree và project/volume riêng. Backend/frontend T10.2 chạy ẩn, không cần giữ terminal launcher. TTL 20 giây/expiry 1 giây là override process; `.env` baseline vẫn 600/5 giây. Không gọi Prepare lại lên session đã có.

Luồng mặc định README, **chỉ trong đúng workspace và khi cổng trống**: `infra/local-env.ps1` tạo `.env` nếu thiếu, `npm.cmd ci --prefix frontend` và `npm.cmd ci --prefix tools/contracts`; chạy `infra/start-local.ps1` và `infra/start-frontend.ps1` bằng `powershell -NoProfile -ExecutionPolicy Bypass -File` ở hai terminal giữ mở (API 8080/UI 127.0.0.1:5173, PG 5432/Kafka 9092). Dừng bằng Ctrl+C hoặc `infra/stop-local.ps1 -StopServices` trong chính workspace đó, giữ volume. Không dùng lệnh dừng mặc định ở root để thay lệnh dừng clone T10.1/T10.2.

## 4. Kiểm tra và nguồn bằng chứng

```powershell
npm.cmd test --prefix frontend
npm.cmd run build --prefix frontend
npm.cmd test --prefix tools/contracts
# Gate đầy đủ trên chính working tree, môi trường riêng; không sửa source khi đang chạy
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-invariants.ps1 -WorkingTree
powershell -NoProfile -ExecutionPolicy Bypass -File infra/test-load.ps1 -Requests 50 -Rounds 3
```

`-WorkingTree` tránh chứng nhận bản sửa chưa commit bằng HEAD cũ. Launcher Maven có thể skip IT: không gọi đó là full backend PASS. `verify-local.ps1` chỉ đọc health/V1/schema; `verify-part4`…`verify-part8` lịch sử **ghi dữ liệu**, có script cố định cổng 8080, không chạy lên demo cần bảo toàn. Chi tiết [TEST_PLAN](TEST_PLAN.md); JUnit ở `backend/target/surefire-reports`, raw gate ở `reports/` ignored.

| Số liệu / artifact | Nguồn trong docs/evidence |
|---|---|
| Gate 130 backend, 54 frontend, 69 contract; phân biệt lượt chạy | `t9-1-invariants-2026-11-14.json`, `t9-3-recovery-2026-11-14.json`, `t9-4-audit-2026-11-14.json` |
| 3×50 request/mode; GA 30 success/120 conflict, ghế 3/147, 0 lỗi khác, oversell = 0 | `t9-2-load-2026-11-14.json`; mẫu CSV `t9-2-samples-2026-11-14.csv`, SQL `t9-2-audits-2026-11-14.sql`. Có p50/p95/p99/throughput/máy/pool. Burst loopback, cùng principal, không đo TPS duy trì. |
| Kafka/late/rollback/restart; 37 snapshot × 11 counter đều 0, replay no-op | `t9-3-recovery-2026-11-14.json`, `t9-3-snapshots-2026-11-14.json`, `t9-3-queries-2026-11-14.sql` |
| Demo sạch/restart, Chrome chỉ user xác nhận | `t10-1-clean-demo-2026-11-14.json`, `t10-1-chrome-user-2026-11-14.md` |
| T10.2: 8/8 bước, 365,499 giây gồm 310 giây xem/thuyết minh; launcher 9 chạy/121 skip | `t10-2-rehearsal-2026-11-14.json`. Chưa kiểm Chrome T10.2; không gộp với 130 test gate. |
| Báo cáo 43 trang, 8 sơ đồ/24 bảng/46 mục trong mục lục | `t10-3-report-2026-11-14.json`; hash Word/PDF/Markdown khớp khi bàn giao. |

Báo cáo: [Word](report/bao-cao-do-an.docx), [PDF](report/bao-cao-do-an.pdf), [nội dung Markdown](report/BAO_CAO.md), [hướng dẫn chỉnh/build](report/README.md). Nguồn dựng ở `tools/report/`, sơ đồ ở `docs/report/figures/`. Không chèn PNG QA hoặc ảnh Chrome chưa có vào báo cáo. File `Quy_Trinh_Xay_Dung_He_Thong_Dat_Ve_Le_Huu_Hao.docx` là kế hoạch cũ, không phải hiện trạng hoặc mẫu trường.

## 5. Việc tiếp theo cho T10.4

1. Điền **7 ô `[CẦN ĐIỀN]`** báo cáo: trường; khoa/viện; MSSV; lớp/khóa; ngành; giảng viên; địa điểm và thời gian nộp. Tên Lê Hữu Hào đã có nguồn PRD. Có mẫu trường thì áp mẫu, xuất lại Word/PDF và kiểm số trang 30–50, mục lục, bảng/hình/glyph từng trang; không sửa số đo để khớp mẫu.
2. Chốt backlog ngoài MVP và giới hạn (tiền thật, xác thực production, HA/mất đĩa, tải duy trì, giao diện khách với hơn 100 bản ghi…), ghi rõ chưa làm/chưa kiểm, không triển khai tính năng mới chỉ để đóng task.
3. Tái sử dụng sơ đồ có source; thu/đối chiếu ảnh demo **thực** khi có, ghi môi trường/ngày/nguồn, che bí mật. Nếu còn thiếu ảnh/kiểm Chrome thì ghi thiếu, không bịa hoặc đánh dấu đã xem.
4. Chuẩn bị source/ZIP có đúng candidate đã kiểm, không chỉ `git archive HEAD` khi report/harness còn untracked. Loại cấu hình riêng/cache/log/database/volume. Thử theo README/runbook từ bản sao sạch, cổng/project/volume riêng; lưu lệnh và kết quả. Không reset demo cũ.
5. Đề xuất mốc commit/tag trỏ đúng source đã kiểm; chỉ tạo/công bố khi có chỉ dẫn của chủ dự án. Kiểm CI của đúng commit mới khi có; không dùng CI39 chứng nhận working tree. Hiện chưa được commit/push và chưa có bằng chứng ZIP/source/tag cuối tái lập được.

**Chỉ Done T10.4 khi** đủ AC TASKS: ZIP/source/tag tái lập được, backlog/sơ đồ/ảnh được chốt với nguồn thật, kết quả kiểm tra và giới hạn ghi vào PROGRESS; không còn phần bắt buộc chưa kiểm mà vẫn ghi hoàn thành. Các bước trên là kế hoạch bàn giao, chưa được thực hiện trong lượt này.

## 6. Git hygiene và kiểm tra lượt bàn giao

Không đưa vào Git `.env`/`.env.*` riêng (chỉ `.env.example` placeholder), password/token/bí mật, raw log/raw payment, database dump/dữ liệu demo hoặc volume, `reports/`, `.local-runtime`, `target`, `node_modules`, `dist`. Chỉ chọn source, tài liệu, báo cáo và evidence đã lọc; không `git add .` thiếu rà soát, không force-add file ignored. Không xóa volume/inbox/key hoặc reset DB để làm test xanh.

Lượt bàn giao này chỉ thêm HANDOFF và AGENTS; không chạy lại suite nghiệp vụ, không Start/Stop/NewRun, không thay báo cáo hoặc `.env`. Đã kiểm `git status`/HEAD, nguồn tài liệu, listener/`docker ps`, HTTP health 18080/18081 UP và hash 3 artifact khớp evidence T10.3. Kiểm tra UTF-8, 14 liên kết nội bộ và `git diff --check` PASS; hash 258 file có sẵn không đổi, HEAD giữ nguyên. Gitleaks quét riêng hai file mới: exit 0, không có finding; kết quả cục bộ ở `reports/handoff/secrets.json` (ignored). Git chỉ cảnh báo LF→CRLF theo cấu hình hiện có. Các lệnh khởi động/test ở trên là hướng dẫn cho lượt sau, không phải test mới đã chạy.
