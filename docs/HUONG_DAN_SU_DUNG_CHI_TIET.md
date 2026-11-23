# SỔ TAY HƯỚNG DẪN SỬ DỤNG CHI TIẾT TỪNG THAO TÁC (CLICK-BY-CLICK USER MANUAL)
## NỀN TẢNG BÁN VÉ SỰ KIỆN TRỰC TUYẾN EVENTFLOW ENTERPRISE 2026

> **Dành cho:** Ban Giám khảo, Hội đồng Đánh giá Đồ án, Chủ sàn (Admin), Nhà tổ chức sự kiện (Organizer), Nhân viên soát vé (Staff) và Khách hàng (Customer).  
> **Cam kết hệ thống:** 0% Bán lố vé (Zero Oversell), Chống Deadlock tuyệt đối, Bảo mật Anti-IDOR, Vé QR ký số HMAC-SHA256, Soát vé Offline-First và Hệ sinh thái Trí tuệ Nhân tạo AI Suite 2026.

---

## MỤC LỤC TỔNG QUAN

- [Phần 1: Cách Khởi Động, Tắt Hệ Thống & Bảng Dữ Liệu Mẫu Có Sẵn](#phần-1-cách-khởi-động-tắt-hệ-thống--bảng-dữ-liệu-mẫu-có-sẵn)
  - [1.1. Hướng dẫn khởi động hệ thống 1-chạm (`start-all.bat`)](#11-hướng-dẫn-khởi-động-hệ-thống-1-chạm-start-allbat)
  - [1.2. Hướng dẫn tắt an toàn toàn bộ hệ thống (`stop-all.bat`)](#12-hướng-dẫn-tắt-an-toàn-toàn-bộ-hệ-thống-stop-allbat)
  - [1.3. Bảng tài khoản mẫu thật trong cơ sở dữ liệu (4 Vai trò RBAC)](#13-bảng-tài-khoản-mẫu-thật-trong-cơ-sở-dữ-liệu-4-vai-trò-rbac)
  - [1.4. Bảng mã giảm giá (Voucher) thật đang kích hoạt](#14-bảng-mã-giảm-giá-voucher-thật-đang-kích-hoạt)
  - [1.5. Danh mục các sự kiện mẫu tiêu biểu (GA & Seated 3D)](#15-danh-mục-các-sự-kiện-mẫu-tiêu-biểu-ga--seated-3d)
- [Phần 2: Hướng Dẫn Chi Tiết Cho Khách Hàng (Customer & Khách Vãng Lai)](#phần-2-hướng-dẫn-chi-tiết-cho-khách-hàng-customer--khách-vãng-lai)
  - [2.1. Đăng ký mới, Đăng nhập thường & Đăng nhập 1-chạm Google OAuth2](#21-đăng-ký-mới-đăng-nhập-thường--đăng-nhập-1-chạm-google-oauth2)
  - [2.2. Tìm kiếm thông minh, Lọc danh mục Ticketbox & Lọc giá / Hình thức vé](#22-tìm-kiếm-thông-minh-lọc-danh-mục-ticketbox--lọc-giá--hình-thức-vé)
  - [2.3. Xem chi tiết sự kiện, Đặt vé GA, Đặt ghế trên Sơ đồ 3D & Xem Seat View SVG](#23-xem-chi-tiết-sự-kiện-đặt-vé-ga-đặt-ghế-trên-sơ-đồ-3d--xem-seat-view-svg)
  - [2.4. Tính năng Best Seats Picker (Gợi ý cụm ghế liền kề) & Đăng ký Smart Waitlist](#24-tính-năng-best-seats-picker-gợi-ý-cụm-ghế-liền-kề--đăng-ký-smart-waitlist)
  - [2.5. Thanh toán đơn hàng trong 10 phút (Checkout): Áp Voucher, Xuất VAT & Hủy đơn](#25-thanh-toán-đơn-hàng-trong-10-phút-checkout-áp-voucher-xuất-vat--hủy-đơn)
  - [2.6. Quản lý Vé của tôi (`/ve-cua-toi`): QR HMAC-SHA256, Tải lịch .ics & Tiến trình hoàn tiền](#26-quản-lý-vé-của-tôi-ve-cua-toi-qr-hmac-sha256-tải-lịch-ics--tiến-trình-hoàn-tiền)
  - [2.7. Gửi tặng vé cho bạn bè (Peer-to-Peer Transfer) & Xác thực thu hồi QR cũ](#27-gửi-tặng-vé-cho-bạn-bè-peer-to-peer-transfer--xác-thực-thu-hồi-qr-cũ)
  - [2.8. Chợ vé chuyển nhượng chính chủ (Fan-to-Fan Resale Marketplace)](#28-chợ-vé-chuyển-nhượng-chính-chủ-fan-to-fan-resale-marketplace)
  - [2.9. Trò chuyện & Đặt vé tự động với Trợ lý ảo AI Ticketing Concierge](#29-trò-chuyện--đặt-vé-tự-động-với-trợ-lý-ảo-ai-ticketing-concierge)
  - [2.10. Quản lý tài khoản cá nhân 5 Tab (`/tai-khoan`)](#210-quản-lý-tài-khoản-cá-nhân-5-tab-tai-khoan)
- [Phần 3: Hướng Dẫn Chi Tiết Cho Nhà Tổ Chức (Organizer)](#phần-3-hướng-dẫn-chi-tiết-cho-nhà-tổ-chức-organizer)
  - [3.1. Đăng ký nâng cấp tài khoản Nhà tổ chức (Onboarding Flow)](#31-đăng-ký-nâng-cấp-tài-khoản-nhà-tổ-chức-onboarding-flow)
  - [3.2. Cổng Quản trị Organizer (`/organizer`): Tạo sự kiện với AI Copilot](#32-cổng-quản-trị-organizer-organizer-tạo-sự-kiện-với-ai-copilot)
  - [3.3. Cấu hình Hạng vé GA & Sinh nhanh Ma trận ghế Seated](#33-cấu-hình-hạng-vé-ga--sinh-nhanh-ma-trận-ghế-seated)
  - [3.4. Xuất bản mở bán sự kiện (Publish Event)](#34-xuất-bản-mở-bán-sự-kiện-publish-event)
  - [3.5. Tạo Mã giảm giá (Voucher) cho sự kiện](#35-tạo-mã-giảm-giá-voucher-cho-sự-kiện)
  - [3.6. Phân công Nhân viên Soát vé (Staff Assignments)](#36-phân-công-nhân-viên-soát-vé-staff-assignments)
  - [3.7. Xem Doanh thu Bán vé & Gửi yêu cầu Rút tiền (Payout)](#37-xem-doanh-thu-bán-vé--gửi-yêu-cầu-rút-tiền-payout)
- [Phần 4: Hướng Dẫn Chi Tiết Cho Nhân Viên Soát Vé (Staff)](#phần-4-hướng-dẫn-chi-tiết-cho-nhân-viên-soát-vé-staff)
  - [4.1. Đăng nhập Cổng soát vé (`/staff`) & Chọn sự kiện](#41-đăng-nhập-cổng-soát-vé-staff--chọn-sự-kiện)
  - [4.2. Quét vé hợp lệ thời gian thực (Màn hình XANH)](#42-quét-vé-hợp-lệ-thời-gian-thực-màn-hình-xanh)
  - [4.3. Quét vé trùng lặp hoặc vé đã thu hồi / chuyển nhượng (Màn hình ĐỎ)](#43-quét-vé-trùng-lặp-hoặc-vé-đã-thu-hồi--chuyển-nhượng-màn-hình-đỏ)
  - [4.4. Quy trình Soát vé Offline-First & Đồng bộ Batch-Sync khi có mạng lại](#44-quy-trình-soát-vé-offline-first--đồng-bộ-batch-sync-khi-có-mạng-lại)
- [Phần 5: Hướng Dẫn Chi Tiết Cho Quản Trị Viên Sàn (Admin)](#phần-5-hướng-dẫn-chi-tiết-cho-quản-trị-viên-sàn-admin)
  - [5.1. Tab 1: Tổng quan KPI, Doanh thu sàn & Xuất báo cáo CSV](#51-tab-1-tổng-quan-kpi-doanh-thu-sàn--xuất-báo-cáo-csv)
  - [5.2. Tab 2: Xử lý và Phê duyệt các Đơn hoàn tiền (`REFUND_REQUIRED`)](#52-tab-2-xử-lý-và-phê-duyệt-các-đơn-hoàn-tiền-refund_required)
  - [5.3. Tab 3: Quản lý Người dùng, Cấp quyền RBAC & Tính năng Chống tự khóa](#53-tab-3-quản-lý-người-dùng-cấp-quyền-rbac--tính-năng-chống-tự-khóa)
  - [5.4. Tab 4: Duyệt 1-chạm Hồ sơ Đối tác Nhà tổ chức (Convert Leads)](#54-tab-4-duyệt-1-chạm-hồ-sơ-đối-tác-nhà-tổ-chức-convert-leads)
  - [5.5. Tab 5: Chạy Đối soát Tài chính Tự động & Duyệt lệnh Rút tiền](#55-tab-5-chạy-đối-soát-tài-chính-tự-động--duyệt-lệnh-rút-tiền)
  - [5.6. Tab 6: AI Ops — Dự báo cháy vé & Phát hiện cụm đầu cơ (Scalping Alerts)](#56-tab-6-ai-ops--dự-báo-cháy-vé--phát-hiện-cụm-đầu-cơ-scalping-alerts)
  - [5.7. Tab 7: Khôi phục Sự cố, Circuit Breaker Cổng VNPAY & Replay Outbox/DLQ](#57-tab-7-khôi-phục-sự-cố-circuit-breaker-cổng-vnpay--replay-outboxdlq)
- [Phần 6: Bảng Xử Lý Nhanh Sự Cố Thường Gặp (Troubleshooting)](#phần-6-bảng-xử-lý-nhanh-sự-cố-thường-gặp-troubleshooting)

---

## PHẦN 1: CÁCH KHỞI ĐỘNG, TẮT HỆ THỐNG & BẢNG DỮ LIỆU MẪU CÓ SẴN

### 1.1. Hướng dẫn khởi động hệ thống 1-chạm (`start-all.bat`)
Hệ thống EventFlow Enterprise bao gồm 4 khối thành phần: Cơ sở dữ liệu PostgreSQL 17, Hàng đợi sự kiện Apache Kafka 4, Backend Spring Boot (port 8080) và Giao diện người dùng Vite React (port 5173). Toàn bộ quá trình khởi động đã được tự động hóa tối đa:

- **Bước 1:** Khởi động ứng dụng **Docker Desktop** trên máy tính của bạn và đợi thanh trạng thái chuyển sang màu xanh lá cây (`Docker Engine running`).
- **Bước 2:** Vào thư mục gốc của dự án `D:\event-ticketing-platform`.
- **Bước 3:** Nhấp đúp chuột vào file **`start-all.bat`**.
- **Bước 4:** Trình cài đặt tự động thực hiện tuần tự:
  1. Kiểm tra trạng thái Docker Desktop.
  2. Quét và giải phóng xung đột port 8080 và 5173 nếu có tiến trình cũ chiếm dụng.
  3. Khởi động 2 container Docker: `event-ticketing-local-postgres-1` (port 5432) và `event-ticketing-local-kafka-1` (port 9092).
  4. Tự động kiểm tra file `.jar` và mã nguồn backend để đóng gói Maven tự động nếu cần.
  5. Bật Backend trong cửa sổ riêng mang tên: **`EventFlow Backend (8080)`**.
  6. Bật Frontend trong cửa sổ riêng mang tên: **`EventFlow Frontend (5173)`**.
  7. Tự động mở trình duyệt mặc định truy cập thẳng vào: `http://127.0.0.1:5173`.
- **Quy tắc về cửa sổ:**
  - Cửa sổ chạy script `start-all.bat` ban đầu sau khi hiện thông báo thành công có thể đóng lại.
  - **LƯU Ý ĐẶC BIỆT:** Hai cửa sổ màu đen có tiêu đề **`EventFlow Backend (8080)`** và **`EventFlow Frontend (5173)`** **CHỈ ĐƯỢC PHÉP THU NHỎ (MINIMIZE)** xuống thanh Taskbar, **TUYỆT ĐỐI KHÔNG ĐƯỢC TẮT** (vì tắt sẽ làm dừng Backend API hoặc dừng Web).

### 1.2. Hướng dẫn tắt an toàn toàn bộ hệ thống (`stop-all.bat`)
Khi hoàn thành buổi demo hoặc kết thúc phiên làm việc:
- **Bước 1:** Vào thư mục gốc `D:\event-ticketing-platform`.
- **Bước 2:** Nhấp đúp chuột vào file **`stop-all.bat`**.
- **Bước 3:** Script sẽ tự động:
  1. Giải phóng và đóng hoàn toàn các tiến trình trên port 8080 và 5173.
  2. Tự động đóng sạch sẽ hai cửa sổ console Backend và Frontend.
  3. Dừng các container PostgreSQL 17 và Kafka bằng lệnh `docker compose stop` (bảo toàn 100% dữ liệu trong Docker Volume, không bị mất mát).
- **Bước 4:** Nhấn phím bất kỳ để thoát.

---

### 1.3. Bảng tài khoản mẫu thật trong cơ sở dữ liệu (4 Vai trò RBAC)
Hệ thống đã nạp sẵn các tài khoản mẫu tương ứng với 4 phân quyền trong mô hình RBAC chuẩn doanh nghiệp:

| Vai trò | Email đăng nhập | Mật khẩu | Quyền hạn truy cập | Ghi chú demo |
| :--- | :--- | :--- | :--- | :--- |
| **Quản trị viên (ADMIN)** | `admin@eventflow.vn` | `Admin@123` | Toàn quyền truy cập `/admin`, `/organizer`, `/staff`, duyệt Lead B2B, duyệt hoàn tiền, quản lý người dùng, chạy đối soát và cấu hình Circuit Breaker. | Có sẵn chip bấm điền nhanh trong modal đăng nhập. |
| **Nhà tổ chức (ORGANIZER)** | `customer@eventflow.vn` | `Customer@123` | Toàn quyền truy cập Cổng nhà tổ chức `/organizer`: Tạo sự kiện, AI Copilot, thêm hạng vé GA/Seated, mở bán sự kiện, tạo Voucher, gán Staff, xem doanh thu và rút tiền Payout. | Đã được duyệt tư cách Đối tác Tổ chức. |
| **Nhân viên soát vé (STAFF)** | `admin@eventflow.vn` | `Admin@123` | Toàn quyền truy cập Cổng soát vé `/staff`: Quét mã QR, kiểm tra vé hợp lệ, chặn vé trùng lặp, tải Offline Manifest và đồng bộ Batch-Sync. | Tài khoản Admin kế thừa quyền Staff để tiện demo. Ngoài ra có thể dùng Admin để cấp quyền STAFF cho bất kỳ tài khoản mới nào. |
| **Khách hàng (CUSTOMER)** | `nguyenvana@gmail.com` hoặc tài khoản mới tự đăng ký | Tự đặt khi đăng ký (hoặc `Customer@123`) | Đặt vé, chọn ghế 3D, thanh toán 10 phút, quản lý vé `/ve-cua-toi`, tặng vé bạn bè, bán vé Fan-to-Fan Resale, trò chuyện AI Concierge, quản lý 5 Tab hồ sơ cá nhân. | Có thể đăng ký tài khoản mới bất kỳ lúc nào chỉ mất 5 giây. |
| **Google OAuth2 (1-Chạm)** | `customer.google@eventflow.vn` | *Không cần mật khẩu* | Tài khoản mô phỏng Google One-Tap: Đăng nhập tức thì 1-chạm không cần gõ mật khẩu, tự động cấp JWT và Token Family. | Bấm nút "Tiếp tục với Google" trong modal đăng nhập. |

---

### 1.4. Bảng mã giảm giá (Voucher) thật đang kích hoạt
Dưới đây là 2 mã Voucher thực tế đã được nạp sẵn vào bảng `ticketing.vouchers`:

| Mã Voucher | Loại giảm giá | Giá trị giảm | Điều kiện áp dụng | Giảm tối đa | Lượt khả dụng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SUMMER2026`** | Theo phần trăm (`PERCENTAGE`) | **Giảm 10%** tổng tiền | Đơn hàng từ **100.000 đ** | Tối đa **100.000 đ** | 1.000 lượt |
| **`WELCOME50K`** | Số tiền cố định (`FIXED_VND`) | **Giảm 50.000 đ** trực tiếp | Đơn hàng từ **150.000 đ** | **50.000 đ** | 500 lượt |

---

### 1.5. Danh mục các sự kiện mẫu tiêu biểu (GA & Seated 3D)

| Tên sự kiện | Hình thức vé | Địa điểm tổ chức | Hạng vé & Giá niêm yết | Tính năng nổi bật |
| :--- | :--- | :--- | :--- | :--- |
| **Đêm Nhạc Giao Hưởng Ánh Trăng - Beethoven Symphony** | **`SEATED 3D`** | Nhà hát Lớn Hà Nội (1 Tràng Tiền) | *Ghế Thính Phòng*: **800.000 đ** | Sơ đồ ghế 3D, Seat View Isometric góc nhìn sân khấu, Best Seats Picker. |
| **Live Tour: Vũ - Bảo Tàng Của Nuối Tiếc** | **`SEATED 3D`** | Trung tâm Hội nghị Quốc gia, Hà Nội | *Ghế Khán Đài VIP*: **650.000 đ** | Sơ đồ ghế 3D, Seat View 3D, Chợ vé chuyển nhượng Resale chính chủ. |
| **Đêm nhạc ghế chỉ định** | **`SEATED 3D`** | Khán phòng Nhà hát | *Ghế tiêu chuẩn*: **200.000 đ** | Sự kiện ghế chỉ định mẫu, kiểm thử giải phóng ghế khi hủy đơn hàng. |
| **Live Concert: Những Thành Phố Mơ Màng 2026** | **`GA`** | Sân vận động Quân khu 7, TP. HCM | *Vé Phổ Thông (GA)*: **450.000 đ** | Chọn số lượng 1–6 vé, sức chứa lớn, thuật toán Zero Oversell chống bán lố. |
| **Vietnam Tech & AI Summit 2026** | **`GA`** | Trung tâm Hội nghị Quốc gia, Hà Nội | *Vé Tiêu Chuẩn (GA)*: **250.000 đ** | Hội nghị công nghệ, check-in mã QR đa cổng, hỗ trợ xuất hóa đơn VAT điện tử. |
| **Đại nhạc hội Mùa Hè 2026** | **`GA`** | Sân vận động Quốc gia Mỹ Đình | *Vé Phổ Thông GA*: **250.000 đ** | Sự kiện âm nhạc quy mô lớn, thử nghiệm Smart Waitlist khi hết vé. |

---

## PHẦN 2: HƯỚNG DẪN CHI TIẾT CHO KHÁCH HÀNG (CUSTOMER & KHÁCH VÃNG LAI)

### 2.1. Đăng ký mới, Đăng nhập thường & Đăng nhập 1-chạm Google OAuth2
- **Mục đích:** Khởi tạo danh tính người dùng bảo mật, nhận JWT Access Token và Token Family refresh cookie.
- **Thao tác Đăng ký:**
  1. Tại góc trên bên phải thanh Header, bấm nút **`Đăng ký`**.
  2. Điền thông tin:
     - Họ và tên: `Nguyễn Văn Demo`
     - Địa chỉ Email: `demo.customer2026@gmail.com`
     - Số điện thoại: `0912345678`
     - Mật khẩu: `Demo@123` (tối thiểu 6 ký tự).
  3. Bấm nút màu cam **`Đăng ký tài khoản`**.
  4. **Kết quả:** Modal đóng lại, góc phải Header xuất hiện ngay Avatar tròn, tên `Nguyễn Văn Demo` cùng huy hiệu xanh `Khách hàng`.
- **Thao tác Đăng nhập thường:**
  1. Bấm nút **`Đăng nhập`** trên Header.
  2. Bấm vào nút chip gợi ý có sẵn: **`👤 Khách hàng (customer@eventflow.vn)`** (form sẽ tự động điền email và mật khẩu `Customer@123`).
  3. Bấm **`Đăng nhập ngay`**.
  4. **Kết quả:** Đăng nhập thành công, hệ thống chào mừng tài khoản `Khách hàng demo`.
- **Thao tác Đăng nhập 1-chạm Google OAuth2:**
  1. Bấm nút **`Đăng nhập`** trên Header.
  2. Bấm nút **`Tiếp tục với Google (1-Chạm)`** (có logo Google 4 màu).
  3. Menu thả xuống hiện ra 3 tài khoản demo, bấm chọn **`Nguyễn Văn Google (customer.google@eventflow.vn)`**.
  4. **Kết quả:** Đăng nhập tức thì chỉ sau 0.5 giây, tự động liên kết danh tính mà không cần gõ bất kỳ ký tự mật khẩu nào.

---

### 2.2. Tìm kiếm thông minh, Lọc danh mục Ticketbox & Lọc giá / Hình thức vé
- **Mục đích:** Tìm kiếm chính xác sự kiện theo nhu cầu chỉ trong vài giây.
- **Thao tác Tìm kiếm thời gian thực:**
  1. Bấm vào thanh tìm kiếm bo tròn ở chính giữa Header: *"Bạn tìm gì hôm nay? Tên concert, nghệ sĩ, địa điểm..."*.
  2. Gõ từ khóa: `Vũ` hoặc `Beethoven` hoặc `Mỹ Đình`.
  3. **Kết quả:** Danh sách sự kiện bên dưới tự động lọc tức thì theo từng ký tự bạn gõ mà không cần nhấn phím Enter.
- **Thao tác Lọc danh mục Ticketbox ngang:**
  1. Ngay dưới Header là thanh danh mục Ticketbox nền tối: `Tất cả` · `🎸 Nhạc sống` · `🎭 Sân khấu & Nghệ thuật` · `⚽ Thể thao` · `💡 Hội thảo` · `🎟️ Vé GA` · `🪑 Ghế chỉ định (SEATED)` · `🏷️ Vé chuyển nhượng (Resale)`.
  2. Bấm chuột vào nút **`🪑 Ghế chỉ định (SEATED)`**.
  3. **Kết quả:** Toàn bộ các sự kiện vé đứng bị ẩn đi, màn hình hiển thị chính xác 3 sự kiện có sơ đồ ghế 3D (*Beethoven*, *Vũ - Bảo Tàng Của Nuối Tiếc*, *Đêm nhạc ghế chỉ định*).
  4. Bấm chuột vào nút **`🏷️ Vé chuyển nhượng (Resale)`** để cuộn thẳng xuống khu vực Chợ vé sang nhượng của người hâm mộ.
- **Thao tác Lọc khoảng giá:**
  1. Tại thanh công cụ bộ lọc phía trên danh sách sự kiện, bấm vào các nút: **`Dưới 500k`**, **`500k - 1.5tr`**, hoặc **`Trên 1.5tr`**.
  2. **Kết quả:** Danh sách cập nhật ngay lập tức các sự kiện có khoảng giá vé phù hợp.

---

### 2.3. Xem chi tiết sự kiện, Đặt vé GA, Đặt ghế trên Sơ đồ 3D & Xem Seat View SVG
- **Mục đích:** Trải nghiệm xem thông tin sự kiện chuyên nghiệp và chọn vị trí ghế ngồi trực quan.
- **Thao tác Mở trang chi tiết:**
  1. Trên thẻ sự kiện bất kỳ (ví dụ: *Live Tour: Vũ - Bảo Tàng Của Nuối Tiếc*), bấm vào nút **`Xem chi tiết & Đặt vé →`**.
  2. **Kết quả:** Màn hình chuyển sang giao diện Chi tiết sự kiện với Ticket Banner 2 phần (Trái: H1, Ngày giờ, Địa điểm, Nút mua vé; Phải: Poster 16:9 sắc nét).
- **Thao tác Đặt lịch nhắc nhở:**
  1. Bấm nút **`🔔 Nhắc tôi trước 15 phút`**.
  2. **Kết quả:** Nút đổi sang trạng thái màu xanh `Đã lưu nhắc nhở ✓`. Hệ thống sẽ gửi email và thông báo khi cổng bán vé sắp mở.
- **Thao tác Chọn vé GA (Vé đứng tự do):**
  1. Chọn sự kiện GA (ví dụ: *Vietnam Tech & AI Summit 2026*).
  2. Tại khung chọn vé, chọn hạng vé **`Vé Tiêu Chuẩn (GA)`**.
  3. Chọn số lượng vé: Bấm dấu **`+`** để tăng lên `2 vé` (tối đa 6 vé theo quy định chống đầu cơ).
  4. Cột tóm tắt đơn hàng bên phải (`.booking-panel`) tự động cập nhật: `2 x 250.000 đ = 500.000 đ`.
  5. Bấm nút màu cam **`Giữ vé & Thanh toán`** để chuyển sang trang Checkout.
- **Thao tác Chọn ghế trên Sơ đồ ghế 3D & Xem góc nhìn sân khấu (Seat View):**
  1. Chọn sự kiện SEATED (ví dụ: *Đêm Nhạc Giao Hưởng Ánh Trăng - Beethoven Symphony*).
  2. Cuộn xuống phần **`Sơ đồ ghế & Khán phòng 3D`**.
  3. Các ghế được phân biệt rõ ràng: Màu xanh lá cây là **Còn trống**, Màu cam là **Đang có người giữ**, Màu xám là **Đã bán**.
  4. Bấm chuột vào các ghế trống (ví dụ: `A1`, `A2`). Ghế được chọn sẽ phát sáng viền xanh neon.
  5. **Xem mô phỏng góc nhìn 3D sân khấu (Seat View SVG):** Rê chuột hoặc bấm vào icon con mắt **`👁️ Xem góc nhìn`** trên ghế. Một khung pop-up hiển thị hình ảnh đồ họa SVG Isometric góc nhìn thực tế từ vị trí ghế đó hướng lên sân khấu kèm thông số cự ly (ví dụ: *Cự ly: 12.5m - Góc nâng: 15° - Tầm nhìn xuất sắc*).
  6. Khung tóm tắt đơn hàng bên phải hiển thị danh sách ghế đã chọn: `Ghế: A1, A2 - Tổng tiền: 1.600.000 đ`.
  7. Bấm nút **`Giữ ghế đã chọn`** để vào luồng giao dịch an toàn ACID.

---

### 2.4. Tính năng Best Seats Picker (Gợi ý cụm ghế liền kề) & Đăng ký Smart Waitlist
- **Mục đích:** Tự động tìm vị trí ghế đẹp nhất và giữ chỗ dự phòng khi sự kiện hết vé.
- **Thao tác dùng Best Seats Picker:**
  1. Tại màn hình sơ đồ ghế của sự kiện Seated, phía trên sơ đồ có thanh công cụ **`⚡ Gợi ý ghế đẹp tự động (Best Available)`**.
  2. Chọn số lượng ghế cần mua cạnh nhau: chọn số `2`.
  3. Bấm nút **`🔍 Tìm 2 ghế liền kề tốt nhất`**.
  4. **Kết quả:** Thuật toán Contiguous Seat Allocation sẽ tự động phân tích ma trận khán phòng, tự động đánh dấu chọn 2 ghế liền nhau có điểm số tầm nhìn cao nhất và đưa vào đơn hàng.
- **Thao tác Đăng ký Smart Waitlist khi hết vé:**
  1. Khi một sự kiện hoặc hạng vé rơi vào trạng thái Hết vé (`Số lượng khả dụng: 0`).
  2. Khung **`Smart Waitlist (Hàng chờ thông minh)`** sẽ tự động kích hoạt bên dưới.
  3. Bấm nút **`🔔 Đăng ký vào hàng chờ nhận vé khi có người hủy`**.
  4. **Kết quả:** Hệ thống cấp mã số thứ tự hàng chờ FIFO. Ngay khi có bất kỳ khách hàng nào hết hạn thanh toán 10 phút hoặc tự hủy đơn hàng, hệ thống sẽ ưu tiên giữ vé và gửi thông báo độc quyền cho bạn trong 5 phút.

---

### 2.5. Thanh toán đơn hàng trong 10 phút (Checkout): Áp Voucher, Xuất VAT & Hủy đơn
- **Mục đích:** Khóa tài nguyên trong 600 giây (Zero Oversell Invariant), áp dụng khuyến mại và hoàn tất thủ tục thanh toán mô phỏng VNPAY.
- **Giao diện Màn hình Checkout:**
  - Phía trên cùng là **Bộ đếm ngược thời gian giữ chỗ**: `09:59` đếm lùi về 0.
  - Thông tin đơn hàng: Mã đơn dạng `#EF-XXXXXX` (ví dụ: `#EF-3E86C4`), tên sự kiện, danh sách ghế/vé.
- **Thao tác Áp dụng Mã giảm giá (Voucher):**
  1. Tại ô **`Mã giảm giá / Voucher`**, nhập: **`WELCOME50K`** (hoặc `SUMMER2026`).
  2. Bấm nút **`Áp dụng`**.
  3. **Kết quả:** Dòng chữ thông báo xanh hiện lên: *"Áp dụng voucher thành công: Giảm 50.000 đ"*. Dòng Tổng thanh toán tự động giảm trừ chính xác 50.000 đ.
- **Thao tác Xuất hóa đơn VAT điện tử:**
  1. Tích chọn vào ô: **`☑ Yêu cầu xuất hóa đơn điện tử (VAT)`**.
  2. Form thông tin doanh nghiệp hiện ra:
     - Nếu trong tài khoản đã lưu thông tin mặc định, hệ thống sẽ **tự động điền đầy đủ 100%**.
     - Nếu chưa có, điền: Mã số thuế: `0102030405`, Tên công ty: `Công ty TNHH Công Nghệ Mới`, Địa chỉ: `Tòa nhà Innovation, Cầu Giấy, Hà Nội`, Email nhận hóa đơn: `ketoan@congnghemoi.vn`.
- **Thao tác Thanh toán qua Cổng VNPAY Sandbox / Simulator:**
  1. Bấm nút **`💳 Thanh toán qua VNPAY Sandbox (Mô phỏng)`**.
  2. Chọn kịch bản kiểm thử: Bấm chọn **`Thành công ngay (SUCCESS)`**.
  3. Bấm **`Xác nhận thanh toán`**.
  4. **Kết quả:** Sau 1 giây xử lý giao dịch Transactional Outbox, màn hình chuyển sang trạng thái **`ĐÃ XÁC NHẬN (CONFIRMED)`**, xuất hiện pháo hoa chúc mừng và nút xem vé điện tử ngay tại chỗ. Nếu có tích chọn VAT, mã tra cứu hóa đơn điện tử sẽ được cấp tức thì.
- **Thao tác Thử nghiệm Hủy đơn hàng để kiểm chứng nhả ghế:**
  1. Ở một đơn hàng đang giữ chỗ (`PENDING_PAYMENT`), bấm nút màu xám **`Hủy đơn hàng này`**.
  2. Bấm xác nhận hủy trên hộp thoại trình duyệt.
  3. **Kết quả:** Đơn hàng lập tức chuyển sang `ĐÃ HỦY (CANCELLED)`. Quay lại trang sự kiện, các ghế vừa chọn lập tức chuyển từ màu cam trở lại màu xanh **Còn trống**, sẵn sàng cho người khác đặt.

---

### 2.6. Quản lý Vé của tôi (`/ve-cua-toi`): QR HMAC-SHA256, Tải lịch .ics & Tiến trình hoàn tiền
- **Mục đích:** Quản lý toàn bộ vé đã mua, xuất trình soát vé tại cửa và theo dõi các quyền lợi sau bán.
- **Thao tác Truy cập trang vé:**
  1. Trên Header, bấm nút **`🎟️ Vé của tôi`** (hoặc chọn mục Vé của tôi trong menu Avatar).
  2. **Kết quả:** Danh sách toàn bộ đơn hàng của bạn hiển thị dạng thẻ Boarding Pass hàng không cao cấp.
- **Thao tác Xem và kiểm tra Mã QR ký số:**
  1. Trên thẻ vé đã thanh toán, bấm nút **`Hiển thị mã vé QR`**.
  2. Mã QR vector hiển thị sắc nét kèm chuỗi ký số HMAC-SHA256. Đây là mã ký số bảo mật sử dụng khóa bí mật của máy chủ, kẻ gian chụp ảnh màn hình đem nhân bản hoặc tự chế mã QR giả mạo sẽ bị máy quét từ chối ngay lập tức.
- **Thao tác Tải file lịch sự kiện (`.ics`):**
  1. Bấm nút **`📅 Thêm vào Lịch (.ics)`**.
  2. **Kết quả:** Trình duyệt tải về file `event.ics` chuẩn RFC 5545, có thể mở bằng 1 click để đồng bộ tự động vào Google Calendar, Apple Calendar hoặc Microsoft Outlook.
- **Thao tác Xem hóa đơn VAT đã xuất:**
  1. Bấm nút **`🧾 Xem hóa đơn VAT`**.
  2. Một bảng hóa đơn tài chính chuẩn hiển thị: Mã tra cứu (ví dụ: `EINV-...`), Mã số thuế, Thuế suất 10%, Tiền thuế và Tổng tiền thanh toán.
- **Thao tác Theo dõi Thanh tiến trình hoàn tiền 3 bước:**
  1. Với các đơn hàng thanh toán muộn sau khi hết hạn 10 phút (`REFUND_REQUIRED`), thẻ đơn hàng sẽ hiển thị thanh tiến trình trực quan:
     - *Bước 1: Đã ghi nhận yêu cầu hoàn tiền (Hoàn tất)*
     - *Bước 2: Quản trị viên sàn xét duyệt đối soát (Đang xử lý)*
     - *Bước 3: Tiền hoàn về tài khoản ngân hàng / ví VNPAY gốc*.

---

### 2.7. Gửi tặng vé cho bạn bè (Peer-to-Peer Transfer) & Xác thực thu hồi QR cũ
- **Mục đích:** Chuyển quyền sở hữu vé cho người khác an toàn 100%, tự động vô hiệu hóa mã vé cũ để chống lừa đảo bán 1 vé cho nhiều người.
- **Thao tác Gửi tặng vé:**
  1. Tại trang `/ve-cua-toi`, tìm vé muốn tặng, bấm nút **`🎁 Gửi tặng vé cho bạn bè`**.
  2. Hộp thoại mở ra, nhập:
     - Email người nhận: `banbe@gmail.com`
     - Lời nhắn: *"Tặng bạn vé xem concert cuối tuần này nhé!"*.
  3. Bấm nút **`Tạo liên kết tặng vé`**.
  4. Hệ thống sinh ra một liên kết nhận vé độc quyền:  
     `http://127.0.0.1:5173/transfer/accept?token=TRF-...`
  5. Bấm nút **`Sao chép liên kết`** để copy đường dẫn.
- **Thao tác Nhận vé và Kiểm chứng thu hồi (Dùng Tab Ẩn danh):**
  1. Mở một cửa sổ trình duyệt ẩn danh mới (`Ctrl + Shift + N`).
  2. Dán liên kết vừa copy và nhấn Enter.
  3. Đăng nhập một tài khoản khác (hoặc bấm đăng nhập nhanh bằng Google).
  4. Màn hình hiển thị thẻ nhận vé trang trọng với thông tin người tặng và lời nhắn.
  5. Bấm nút màu xanh **`Xác nhận nhận vé này`**.
  6. **Kết quả kiểm chứng bảo mật:**
     - Tại tài khoản người nhận: Vé mới xuất hiện trong `/ve-cua-toi` với mã QR mới mang tên chính chủ người nhận.
     - Tại tài khoản người tặng ban đầu (F5 lại): Vé cũ lập tức bị đóng dấu badge đỏ **`REVOKED (ĐÃ THU HỒI)`**, mã QR cũ bị vô hiệu hóa vĩnh viễn. Nếu đem mã QR cũ đi quét tại cổng, máy soát vé sẽ báo đỏ từ chối ngay.

---

### 2.8. Chợ vé chuyển nhượng chính chủ (Fan-to-Fan Resale Marketplace)
- **Mục đích:** Môi trường chuyển nhượng vé minh bạch giữa các khán giả, chặn tuyệt đối nạn phe vé chợ đen bằng thuật toán khống chế giá bán trần (`asking_price <= original_price`).
- **Thao tác Đăng bán vé (Người bán):**
  1. Tại trang `/ve-cua-toi`, tìm vé muốn bán lại, bấm nút **`🏷️ Bán lại vé (Resale)`**.
  2. Modal đăng bán hiện ra, hiển thị rõ Giá gốc (ví dụ: `800.000 đ`).
  3. Nhập giá muốn bán: Nhập `750.000 đ`.
     - *Thử nghiệm quy tắc chống phe vé:* Nếu bạn cố tình nhập `900.000 đ` (cao hơn giá gốc), hệ thống sẽ báo lỗi đỏ chặn lại: *"Giá bán lại không được vượt quá giá gốc để bảo vệ người hâm mộ"*.
  4. Bấm **`Xác nhận niêm yết lên Chợ vé`**.
  5. **Kết quả:** Vé được niêm yết công khai trên Chợ vé Resale ngoài trang sự kiện.
- **Thao tác Mua lại vé Resale (Người mua khác):**
  1. Dùng một tài khoản khác truy cập trang Chi tiết sự kiện đó.
  2. Cuộn xuống khu vực **`Chợ vé chuyển nhượng chính chủ (Fan-to-Fan Resale)`**.
  3. Thẻ vé đang bán hiển thị: Mã ghế, Giá bán lại `750.000 đ`, Giá gốc `800.000 đ`, kèm badge xanh `Tiết kiệm 6%`.
  4. Người mua bấm nút **`Mua vé này ngay`** → Giữ chỗ độc quyền 10 phút → Thanh toán.
  5. **Kết quả:** Tiền bán vé được ghi nhận cho người bán, người mua nhận vé mới tinh chính chủ, và vé của người bán cũ được thu hồi nguyên tử.

---

### 2.9. Trò chuyện & Đặt vé tự động với Trợ lý ảo AI Ticketing Concierge
- **Mục đích:** Sử dụng trí tuệ nhân tạo hỗ trợ tìm kiếm vé thông minh qua ngôn ngữ tự nhiên tiếng Việt, tự động chọn ghế và tra cứu chính sách.
- **Thao tác Mở AI Concierge:**
  1. Ở góc dưới cùng bên phải màn hình luôn có nút tròn màu xanh nổi bật mang biểu tượng **`🤖 AI Concierge`**.
  2. Bấm vào nút tròn để mở khung chat trò chuyện.
- **Các câu lệnh mẫu để kiểm thử:**
  - **Câu lệnh 1 (Tìm cụm ghế liền kề & Đặt vé tự động):**  
    *Nhập:* `Tìm cho tôi 2 ghế liền nhau ở concert Beethoven`  
    *Kết quả AI:* Trợ lý AI sẽ gọi API phân tích ma trận khán phòng, tìm ra 2 ghế trống tốt nhất (ví dụ: `A1`, `A2`) và hiển thị thẻ hành động màu xanh: **`⚡ Giữ 2 ghế A1, A2 ngay`**. Bấm vào nút này, hệ thống sẽ đưa thẳng bạn vào màn hình thanh toán Checkout với 2 ghế đã được chọn sẵn!
  - **Câu lệnh 2 (Tra cứu sự kiện theo ca sĩ/nghệ sĩ):**  
    *Nhập:* `Có sự kiện nào của Vũ sắp diễn ra không?`  
    *Kết quả AI:* Giới thiệu sự kiện *Live Tour: Vũ - Bảo Tàng Của Nuối Tiếc*, thông báo địa điểm Trung tâm Hội nghị Quốc gia, giá từ 650.000 đ kèm đường dẫn xem chi tiết.
  - **Câu lệnh 3 (Hỏi đáp chính sách hoàn tiền):**  
    *Nhập:* `Chính sách hoàn tiền của sàn như thế nào?`  
    *Kết quả AI:* AI giải thích chi tiết: Giữ chỗ miễn phí trong 10 phút, nếu thanh toán muộn sau khi hết hạn sẽ được hoàn tiền 100%, hỗ trợ chuyển nhượng vé miễn phí cho bạn bè và bán lại trên sàn Fan-to-Fan.
  - **Câu lệnh 4 (Hỏi đáp xuất hóa đơn VAT):**  
    *Nhập:* `Làm sao để tôi lấy được hóa đơn đỏ VAT công ty?`  
    *Kết quả AI:* Hướng dẫn cách tích chọn yêu cầu xuất hóa đơn tại màn hình Checkout hoặc lưu thông tin sẵn trong tab Tài khoản.

---

### 2.10. Quản lý tài khoản cá nhân 5 Tab (`/tai-khoan`)
- **Mục đích:** Cung cấp trung tâm quản lý toàn diện thông tin cá nhân, bảo mật phiên và quyền hạn thành viên.
- **Thao tác Truy cập:** Bấm vào Avatar trên Header → chọn **`Hồ sơ cá nhân`** (hoặc truy cập đường dẫn `/tai-khoan`).
- **Nội dung và thao tác trên 5 Tab:**
  - **Tab 1: 👤 Hồ sơ cá nhân:**
    - Xem và cập nhật: Họ tên, Số điện thoại, Email và Avatar URL.
    - Sửa số điện thoại → bấm nút **`Lưu thay đổi`** → hiển thị thông báo xanh thành công.
  - **Tab 2: 🔒 Bảo mật & Phiên:**
    - Đổi mật khẩu: Nhập mật khẩu cũ, mật khẩu mới (tối thiểu 6 ký tự) → bấm **`Cập nhật mật khẩu`**.
    - **Quản lý phiên đăng nhập Token Family:** Bảng danh sách hiển thị toàn bộ các thiết bị/trình duyệt đang đăng nhập tài khoản của bạn kèm thời gian hoạt động gần nhất.
    - Bấm nút đỏ **`Đăng xuất khỏi tất cả các thiết bị khác`**: Toàn bộ các phiên đăng nhập trên các máy khác sẽ bị thu hồi token ngay lập tức từ xa.
  - **Tab 3: 🧾 Hóa đơn VAT:**
    - Lưu sẵn: Tên công ty, Mã số thuế, Địa chỉ công ty, Email kế toán nhận hóa đơn.
    - Bấm nút **`Lưu thông tin hóa đơn mặc định`**. Từ nay, mỗi khi bạn mua vé ở màn hình Checkout, hệ thống sẽ tự động điền sẵn thông tin này, bạn không cần phải gõ lại.
  - **Tab 4: ⏳ Chờ & Lịch nhắc:**
    - Danh sách các sự kiện bạn đang đăng ký Hàng chờ thông minh (Waitlist).
    - Danh sách các sự kiện bạn đã bấm Nhắc nhở trước 15 phút kèm nút Hủy nhắc nhở nếu không còn nhu cầu.
    - Lịch sử các vé bạn đã gửi tặng và các vé bạn đã nhận được từ bạn bè.
  - **Tab 5: 🎪 Đối tác Tổ chức:**
    - Hiển thị trực quan tư cách Nhà tổ chức của bạn: Thẻ xanh **`ĐỐI TÁC CHÍNH THỨC`** kèm tên đơn vị tổ chức và nút truy cập nhanh vào Cổng quản trị sự kiện `/organizer`.

---

## PHẦN 3: HƯỚNG DẪN CHI TIẾT CHO NHÀ TỔ CHỨC (ORGANIZER)

### 3.1. Đăng ký nâng cấp tài khoản Nhà tổ chức (Onboarding Flow)
- **Mục đích:** Quy trình tự phục vụ (Self-service) cho phép khách hàng nâng cấp lên đối tác phát hành vé chính thức trên sàn EventFlow.
- **Thao tác Thực hiện:**
  1. Đăng nhập bằng một tài khoản khách hàng thông thường (hoặc tài khoản vừa đăng ký mới).
  2. Trên thanh Header, bấm vào nút màu cam nổi bật: **`+ Tạo sự kiện`**.
  3. Vì tài khoản chưa có quyền Organizer, hệ thống tự động mở modal **`Đăng ký Trở thành Nhà tổ chức Sự kiện`**.
  4. Điền các trường thông tin đối tác:
     - Tên công ty / Tổ chức: `Công ty Truyền Thông & Giải Trí Sao Việt`
     - Số điện thoại liên hệ: `0988776655`
     - Mã số thuế: `0312345678`
     - Quy mô sự kiện dự kiến: Chọn `Đại nhạc hội (trên 10.000 khán giả)`.
     - Ghi chú: `Đơn vị tổ chức chuỗi concert mùa đông 2026`.
  5. Bấm nút **`Gửi hồ sơ đăng ký`**.
  6. **Kết quả:** Modal chuyển sang thông báo: *"Hồ sơ của bạn đã được gửi thành công và đang chờ Quản trị viên sàn phê duyệt"*.
  7. *(Xem tiếp Mục 5.4 để biết cách dùng tài khoản Admin duyệt hồ sơ này chỉ bằng 1 cú click chuột)*.

---

### 3.2. Cổng Quản trị Organizer (`/organizer`): Tạo sự kiện với AI Copilot
- **Mục đích:** Xây dựng thông tin sự kiện chuyên nghiệp với sự trợ giúp của AI trong 30 giây.
- **Thao tác Truy cập:**
  1. Đăng nhập tài khoản Organizer: `customer@eventflow.vn` / mật khẩu `Customer@123` (hoặc tài khoản vừa được Admin duyệt).
  2. Bấm vào nút cam **`+ Tạo sự kiện`** trên Header (hoặc truy cập `/organizer`).
  3. Màn hình Cổng quản trị Nhà tổ chức hiện ra với danh sách các sự kiện đang quản lý.
  4. Bấm nút xanh nổi bật: **`+ Tạo sự kiện mới`**.
- **Thao tác Dùng AI Organizer Copilot tự soạn nội dung:**
  1. Trên đỉnh form tạo sự kiện có khung màu tím **`✨ AI Organizer Copilot`**.
  2. Nhập gợi ý chủ đề: `Đêm nhạc Acoustic Mùa Thu Hà Nội với các ca sĩ trẻ Indie tại Nhà hát Lớn`.
  3. Bấm nút **`Tạo nội dung tự động bằng AI`**.
  4. **Kết quả kỳ diệu:** AI tự động điền hoàn chỉnh toàn bộ form:
     - Tên sự kiện: Chuẩn SEO, hấp dẫn.
     - Thể loại: Tự chọn `Đại nhạc hội & Concert` hoặc `Nhà hát & Nghệ thuật Hàn lâm`.
     - Địa điểm: Tự chọn `Nhà hát Lớn Hà Nội`.
     - Mô tả chi tiết: Bài viết giới thiệu lôi cuốn kèm Timeline Agenda 4 chặng chi tiết (Đón khách, Khai màn, Trình diễn chính, Giao lưu).
     - Ngày giờ mở bán và bắt đầu: Tự động tính toán hợp lý.
  5. Bấm nút **`Lưu sự kiện (Bản nháp DRAFT)`**.

---

### 3.3. Cấu hình Hạng vé GA & Sinh nhanh Ma trận ghế Seated
- **Mục đích:** Thiết lập sơ đồ khán phòng và giá bán cho từng phân khúc khán giả.
- **Thao tác Thêm hạng vé GA (Vé đứng):**
  1. Trong trang quản lý sự kiện vừa tạo, bấm nút **`+ Thêm hạng vé`**.
  2. Chọn Hình thức vé: **`Vé Tự Do (GA)`**.
  3. Tên hạng vé: `Vé Early Bird`.
  4. Giá vé (VND): `350000`.
  5. Số lượng mở bán (Capacity): `500`.
  6. Bấm **`Lưu hạng vé`**.
- **Thao tác Thêm hạng vé SEATED & Sinh ma trận ghế tự động:**
  1. Bấm nút **`+ Thêm hạng vé`**.
  2. Chọn Hình thức vé: **`Ghế Chỉ Định (SEATED)`**.
  3. Tên hạng vé: `Ghế VIP Khán Phòng`.
  4. Giá vé (VND): `750000`.
  5. **Sinh ma trận ghế tự động:**
     - Số hàng ghế: Nhập `5` (tương ứng hàng A, B, C, D, E).
     - Số ghế mỗi hàng: Nhập `10` (từ số 1 đến 10).
  6. Bấm nút **`Tạo sơ đồ ma trận ghế`**.
  7. **Kết quả:** Hệ thống lập tức khởi tạo chính xác 50 ghế ngồi (`A1..A10` đến `E1..E10`) trong cơ sở dữ liệu với trạng thái `AVAILABLE`.

---

### 3.4. Xuất bản mở bán sự kiện (Publish Event)
- **Mục đích:** Kích hoạt mở bán công khai sự kiện trên toàn hệ thống sàn giao dịch.
- **Thao tác Thực hiện:**
  1. Kiểm tra lại sự kiện: Đã có đủ thông tin thời gian, địa điểm và ít nhất 1 hạng vé hợp lệ.
  2. Bấm nút màu tím nổi bật: **`🚀 Xuất bản mở bán (Publish)`**.
  3. **Kết quả:** Trạng thái sự kiện chuyển ngay từ `DRAFT` sang **`PUBLISHED (ĐANG MỞ BÁN)`**.
  4. Mở tab mới truy cập Trang chủ `http://127.0.0.1:5173/`, bạn sẽ thấy ngay sự kiện mới xuất hiện trang trọng trên Lưới sự kiện nổi bật với đầy đủ poster, giá từ và nút đặt vé hoạt động bình thường!

---

### 3.5. Tạo Mã giảm giá (Voucher) cho sự kiện
- **Mục đích:** Tạo chiến dịch khuyến mại kích cầu bán vé trong các khung giờ vàng.
- **Thao tác Thực hiện:**
  1. Tại thanh menu bên trái của `/organizer`, bấm vào mục **`Mã giảm giá (Vouchers)`**.
  2. Bấm nút **`+ Tạo mã voucher mới`**.
  3. Điền thông tin:
     - Mã Voucher: `TRIAN2026` (viết hoa, không dấu).
     - Loại giảm giá: Chọn `Phần trăm (%)` hoặc `Số tiền cố định (VND)`.
     - Giá trị giảm: Nhập `15` (nếu là %) hoặc `50000` (nếu là VND).
     - Giá trị đơn hàng tối thiểu: `200000`.
     - Số lượt sử dụng tối đa: `100`.
     - Thời hạn hiệu lực: Chọn từ hôm nay đến cuối tháng.
  4. Bấm **`Tạo Voucher`**.
  5. **Kết quả:** Mã hiển thị trong danh sách và khách hàng có thể áp dụng ngay lập tức tại màn hình Checkout.

---

### 3.6. Phân công Nhân viên Soát vé (Staff Assignments)
- **Mục đích:** Cấp quyền kiểm soát vé tại cổng cho đội ngũ vận hành thực địa.
- **Thao tác Thực hiện:**
  1. Vào chi tiết sự kiện muốn phân công nhân viên.
  2. Chọn tab **`Nhân sự soát vé (Staff)`**.
  3. Tại ô tìm kiếm nhân viên, nhập email của nhân viên (ví dụ: `admin@eventflow.vn` hoặc email nhân viên đã có tài khoản trên hệ thống).
  4. Bấm nút **`Gán vào sự kiện`**.
  5. **Kết quả:** Tên nhân viên xuất hiện trong danh sách Được phân công. Nhân viên này khi đăng nhập vào `/staff` sẽ thấy sự kiện hiện lên trong danh sách soát vé.

---

### 3.7. Xem Doanh thu Bán vé & Gửi yêu cầu Rút tiền (Payout)
- **Mục đích:** Minh bạch dòng tiền bán vé và gửi lệnh rút tiền doanh thu về tài khoản ngân hàng của Nhà tổ chức.
- **Thao tác Thực hiện:**
  1. Tại menu `/organizer`, bấm vào mục **`Doanh thu & Rút tiền`**.
  2. Hệ thống hiển thị bảng phân tích tài chính tự động:
     - *Tổng doanh thu vé đã bán thành công*: Ví dụ `50.000.000 đ`.
     - *Tỷ lệ phí sàn EventFlow*: Tùy gói dịch vụ (ví dụ: 5%).
     - *Số tiền đã rút*: `0 đ`.
     - *Số dư khả dụng có thể rút ngay*: `47.500.000 đ`.
  3. Bấm nút **`Yêu cầu rút tiền (Request Payout)`**.
  4. Điền thông tin nhận tiền:
     - Số tiền yêu cầu rút: `20000000`.
     - Tên ngân hàng: `Vietcombank`.
     - Số tài khoản: `0123456789`.
     - Tên chủ tài khoản: `CONG TY TRUYEN THONG SAO VIET`.
  5. Bấm nút **`Gửi yêu cầu rút tiền`**.
  6. **Kết quả:** Lệnh rút tiền chuyển sang trạng thái `REQUESTED (ĐANG CHỜ DUYỆT)` để Admin sàn đối soát và phê duyệt.

---

## PHẦN 4: HƯỚNG DẪN CHI TIẾT CHO NHÂN VIÊN SOÁT VÉ (STAFF)

### 4.1. Đăng nhập Cổng soát vé (`/staff`) & Chọn sự kiện
- **Mục đích:** Truy cập giao diện làm việc tối ưu hóa tốc độ cao dành cho nhân viên trực tại cổng sự kiện.
- **Thao tác Thực hiện:**
  1. Đăng nhập bằng tài khoản: `admin@eventflow.vn` / mật khẩu `Admin@123` (tài khoản đã có sẵn quyền Staff).
  2. Trên thanh điều hướng hoặc truy cập trực tiếp đường dẫn: `http://127.0.0.1:5173/staff`.
  3. Tại menu thả xuống **`Chọn sự kiện soát vé`**, bấm chọn sự kiện muốn soát vé (ví dụ: *Đêm Nhạc Giao Hưởng Ánh Trăng - Beethoven Symphony*).
  4. **Kết quả:** Giao diện Trạm soát vé hiện ra với bảng thống kê thời gian thực: Tổng số vé phát hành, Số vé đã check-in, Tỷ lệ vào cổng, và 2 tab quét mã: **`Quét Camera QR`** và **`Nhập mã thủ công`**.

---

### 4.2. Quét vé hợp lệ thời gian thực (Màn hình XANH)
- **Mục đích:** Soát vé cho khách hàng vào cửa nhanh chóng (< 0.1 giây/khách).
- **Thao tác Thực hiện:**
  1. Chọn tab **`Nhập mã vé thủ công`** (hoặc dùng súng quét mã QR chiếu vào mã QR trên vé của khách).
  2. Lấy mã vé hợp lệ từ một đơn hàng đã thanh toán thành công (ví dụ: `TICK-XXXXXX`).
  3. Nhập mã vào ô và bấm nút màu xanh **`XÁC NHẬN SOÁT VÉ`** (hoặc nhấn phím Enter).
  4. **Kết quả quan sát trên màn hình:**
     - Màn hình đổi sang **MÀU XANH LÁ CÂY RỰC RỠ (SUCCESS)**.
     - Loa phát âm thanh **Ting ting (880Hz)** vui tai.
     - Hiển thị to rõ: Tên khách hàng, Tên hạng vé, Số ghế ngồi (ví dụ: `Ghế A1`), Cổng soát vé `MAIN_GATE` và mốc thời gian check-in.
     - Số đếm trên bảng thống kê tự động nhảy tăng thêm 1 vé.

---

### 4.3. Quét vé trùng lặp hoặc vé đã thu hồi / chuyển nhượng (Màn hình ĐỎ)
- **Mục đích:** Ngăn chặn tuyệt đối hành vi gian lận quay vòng vé hoặc dùng vé giả/vé đã bị hủy.
- **Thao tác Thử nghiệm quét lại vé vừa quét lần 2:**
  1. Giữ nguyên mã vé vừa check-in ở trên.
  2. Tiếp tục bấm nút **`XÁC NHẬN SOÁT VÉ`** lần thứ 2.
  3. **Kết quả:**
     - Màn hình lập tức đổi sang **MÀU ĐỎ CẢNH BÁO (ERROR)** chớp nháy.
     - Loa phát âm thanh cảnh báo **Buzz trầm (220Hz)**.
     - Thông báo lỗi to rõ: **`VÉ ĐÃ ĐƯỢC CHECK-IN TRƯỚC ĐÓ!`** kèm thông tin chính xác: *Đã vào cửa lúc 19:30:15 tại Cổng A bởi Nhân viên 1*.
- **Thao tác Thử nghiệm quét vé đã đem tặng bạn bè hoặc bán lại:**
  1. Nhập mã vé của một vé cũ đã được bấm tặng bạn bè (`REVOKED`).
  2. Bấm xác nhận soát vé.
  3. **Kết quả:** Màn hình đỏ cảnh báo: **`VÉ KHÔNG HỢP LỆ HOẶC ĐÃ BỊ THU HỒI (REVOKED)`**. Chặn đứng hành vi gian lận tại cửa.

---

### 4.4. Quy trình Soát vé Offline-First & Đồng bộ Batch-Sync khi có mạng lại
- **Mục đích:** Đảm bảo cổng soát vé vẫn hoạt động mượt mà 100% khi sự kiện đông người gây nghẽn mạng 4G/Wi-Fi tại sân vận động.
- **Bước 1: Tải dữ liệu ngoại tuyến (Offline Manifest) trước giờ mở cửa:**
  1. Khi đang có mạng internet, tại trang `/staff`, bấm nút **`📥 Tải danh sách Offline (Offline Manifest)`**.
  2. Hệ thống tải toàn bộ danh sách vé hợp lệ đã ký số bảo mật của sự kiện về lưu trữ cục bộ trong `localStorage` trình duyệt.
  3. Badge trạng thái đổi sang màu xanh: **`Đã sẵn sàng Offline: 100% vé đã đồng bộ`**.
- **Bước 2: Mô phỏng mất mạng và soát vé Offline:**
  1. Tích chọn vào nút gạt: **`Chế độ Ngoại Tuyến (Offline Mode)`** (hoặc ngắt kết nối Wi-Fi trên máy tính).
  2. Nhập mã vé của khách và bấm Soát vé.
  3. **Kết quả:** Màn hình vẫn hiện màu xanh **`CHECK-IN OFFLINE THÀNH CÔNG`** cực nhanh không có độ trễ mạng! Mã vé được đưa vào Hàng đợi chờ đồng bộ: `Hàng đợi Offline: 1 vé`.
- **Bước 3: Bật mạng lại và Đồng bộ hàng loạt (Batch-Sync):**
  1. Bỏ tích chọn Chế độ Ngoại Tuyến (hoặc kết nối lại Wi-Fi).
  2. Nút màu cam **`🔄 Đồng bộ hàng loạt (Batch-Sync)`** sáng lên kèm số lượng vé đang chờ.
  3. Bấm nút **`Đồng bộ hàng loạt`**.
  4. **Kết quả:** Toàn bộ lịch sử soát vé được đẩy lên máy chủ trong một gói tin duy nhất, cập nhật đồng bộ vào cơ sở dữ liệu PostgreSQL và hiển thị thông báo: *"Đồng bộ thành công 100% vé ngoại tuyến!"*.

---

## PHẦN 5: HƯỚNG DẪN CHI TIẾT CHO QUẢN TRỊ VIÊN SÀN (ADMIN)

### 5.1. Tab 1: Tổng quan KPI, Doanh thu sàn & Xuất báo cáo CSV
- **Mục đích:** Giám sát sức khỏe tài chính toàn sàn và xuất báo cáo kiểm toán doanh nghiệp.
- **Thao tác Thực hiện:**
  1. Đăng nhập tài khoản Admin: `admin@eventflow.vn` / mật khẩu `Admin@123`.
  2. Trên Header, bấm nút Avatar → chọn **`Quản trị sàn (ADMIN)`** (hoặc truy cập `/admin`).
  3. Bấm vào **Tab 1: Tổng quan (Reports)**:
     - 4 thẻ KPI lớn hiển thị: *Tổng số sự kiện*, *Tổng vé đã bán*, *Tổng doanh thu VND*, *Tỷ lệ lấp đầy bình quân*.
     - Bảng xếp hạng doanh thu theo từng sự kiện.
  4. Bấm nút màu xanh **`📥 Xuất báo cáo CSV an toàn`**.
  5. **Kết quả:** Trình duyệt tải ngay file `eventflow-sales-report.csv` chuẩn mã hóa UTF-8, có thể mở trực tiếp bằng Excel để xem chi tiết.

---

### 5.2. Tab 2: Xử lý và Phê duyệt các Đơn hoàn tiền (`REFUND_REQUIRED`)
- **Mục đích:** Xử lý các giao dịch bất thường (ví dụ: khách chuyển khoản VNPAY thành công đúng vào giây thứ 600 khi ghế đã bị Expiry Worker thu hồi).
- **Thao tác Thực hiện:**
  1. Bấm vào **Tab 2: Đơn hàng & Hoàn tiền (Bookings & Refunds)**.
  2. Bấm bộ lọc trạng thái: **`Chờ hoàn tiền (REFUND_REQUIRED)`**.
  3. Bảng hiển thị danh sách các đơn hàng cần hoàn tiền với mã booking rút gọn đẹp mắt dạng `#EF-XXXXXX` kèm nút sao chép `📋`.
  4. Tại dòng đơn hàng cần xử lý, bấm nút **`Duyệt hoàn tiền (Approve Refund)`**.
  5. Nhập ghi chú kiểm toán: `Đã chuyển khoản hoàn tiền ngân hàng thành công`.
  6. Bấm **`Xác nhận duyệt`**.
  7. **Kết quả:** Trạng thái đơn chuyển sang `REFUNDED (ĐÃ HOÀN TIỀN)`. Bên phía khách hàng, thanh tiến trình hoàn tiền nhảy sáng bước 3 hoàn tất.

---

### 5.3. Tab 3: Quản lý Người dùng, Cấp quyền RBAC & Tính năng Chống tự khóa
- **Mục đích:** Phân quyền quản trị và thực thi an ninh tài khoản người dùng trên toàn hệ thống.
- **Thao tác Cấp/Thu hồi vai trò RBAC:**
  1. Bấm vào **Tab 3: Người dùng (Users)**.
  2. Danh sách hiển thị toàn bộ tài khoản với đầy đủ Email, Tên hiển thị, Trạng thái hoạt động và Danh sách vai trò (`ADMIN`, `ORGANIZER`, `STAFF`, `CUSTOMER`).
  3. Tìm tài khoản bất kỳ (ví dụ: `nguyenvana@gmail.com`).
  4. Bấm nút **`+ STAFF`** để cấp quyền nhân viên soát vé cho người này.
  5. **Kết quả:** Huy hiệu `STAFF` xuất hiện ngay lập tức bên cạnh tên tài khoản.
- **Kiểm chứng tính năng An toàn: Chống Admin tự tước quyền hoặc tự khóa chính mình:**
  1. Tìm dòng tài khoản của chính bạn (`admin@eventflow.vn`).
  2. Quan sát nút hành động của dòng này:
     - Cạnh tên có gắn huy hiệu **`Bạn (Current)`**.
     - Nút **`- ADMIN`** và nút **`Khóa tài khoản`** bị **VÔ HIỆU HÓA HOÀN TOÀN (DISABLED)**, đổi sang màu xám mờ.
     - Rê chuột vào nút sẽ hiện tooltip giải thích rõ: *"Không thể tự tước quyền hoặc tự khóa tài khoản của chính mình để tránh mất quyền quản trị hệ thống"*.

---

### 5.4. Tab 4: Duyệt 1-chạm Hồ sơ Đối tác Nhà tổ chức (Convert Leads)
- **Mục đích:** Phê duyệt nhanh chóng các doanh nghiệp nộp hồ sơ Onboarding làm Nhà tổ chức sự kiện.
- **Thao tác Thực hiện:**
  1. Bấm vào **Tab 4: Khách hàng B2B (Leads)**.
  2. Danh sách các đơn vị đăng ký hiện ra (ví dụ: *Công ty Truyền Thông & Giải Trí Sao Việt* đã nộp ở Mục 3.1).
  3. Tại cột Thao tác, bấm nút màu tím nổi bật: **`⚡ Duyệt thành Nhà tổ chức`**.
  4. Hộp thoại xác nhận hiện ra, bấm **`Đồng ý chuyển đổi`**.
  5. **Kết quả:**
     - Trạng thái lead chuyển thành `CONVERTED (ĐÃ DUYỆT)`.
     - Cột người duyệt ghi nhận rõ ràng: `Quản trị viên (ADMIN)`.
     - Tài khoản khách hàng đó lập tức được nâng cấp quyền `ORGANIZER` trong PostgreSQL. Khi người đó F5 trình duyệt là có thể vào thẳng `/organizer` để đăng bán vé ngay!

---

### 5.5. Tab 5: Chạy Đối soát Tài chính Tự động & Duyệt lệnh Rút tiền
- **Mục đích:** Khớp nối giao dịch sổ cái thanh toán và giải ngân tiền cho Nhà tổ chức sự kiện.
- **Thao tác Chạy Đối soát Tự động:**
  1. Bấm vào **Tab 5: Đối soát & Rút tiền (Reconciliation & Payouts)**.
  2. Bấm nút **`🚀 Chạy đối soát tài chính hôm nay`**.
  3. Hệ thống chạy thuật toán quét tự động giữa 3 bảng: Sổ cái Đơn hàng (`bookings`), Lịch sử Thanh toán (`payments`) và Ghi nhận Cổng thanh toán (`payment_gateway_attempts`).
  4. **Kết quả:** Sinh ra báo cáo đối soát chi tiết: Khớp 100% không lệch đồng nào (`DISCREPANCIES = 0`).
- **Thao tác Duyệt lệnh Rút tiền (Payout):**
  1. Cuộn xuống phần **`Danh sách yêu cầu rút tiền của Nhà tổ chức`**.
  2. Thấy yêu cầu rút 20.000.000 đ của *Công ty Sao Việt* ở trạng thái `REQUESTED`.
  3. Bấm nút màu xanh **`Phê duyệt (Approve)`** → Chuyển sang `APPROVED`.
  4. Sau khi chuyển tiền ngân hàng thực tế xong, bấm nút **`Đã chuyển tiền (Mark Paid)`**.
  5. **Kết quả:** Lệnh đổi sang `PAID`, số dư khả dụng của Nhà tổ chức được khấu trừ chuẩn xác.

---

### 5.6. Tab 6: AI Ops — Dự báo cháy vé & Phát hiện cụm đầu cơ (Scalping Alerts)
- **Mục đích:** Ứng dụng trí tuệ nhân tạo giám sát an ninh thị trường vé và dự báo tốc độ bán vé.
- **Thao tác Xem Dự báo Cháy vé (Sell-Out Forecast):**
  1. Bấm vào **Tab 6: AI Ops & An ninh sàn**.
  2. Chọn sự kiện cần xem dự báo (ví dụ: *Live Tour: Vũ - Bảo Tàng Của Nuối Tiếc*).
  3. Biểu đồ AI Ops hiển thị:
     - Tốc độ bán vé bình quân (vé/giờ).
     - Thời điểm dự kiến cháy sạch vé (Sell-out ETA).
     - Khuyến nghị điều chỉnh giá hoặc mở thêm đợt vé mới từ mô hình Machine Learning.
- **Thao tác Xử lý Cảnh báo Đầu cơ (Scalping Anomaly Detection):**
  1. Bảng bên dưới hiển thị các **Cảnh báo bất thường (Scalping Alerts)** do AI tự động phát hiện (ví dụ: Tài khoản có hành vi dùng bot găm giữ hàng chục vé liên tục từ một dải IP đáng ngờ).
  2. Xem mức độ nghiêm trọng: `HIGH (NGHIÊM TRỌNG)`.
  3. Bấm nút đỏ 1-chạm: **`🔒 Khóa tài khoản phe vé ngay`**.
  4. **Kết quả:** Tài khoản đó lập tức bị chuyển sang trạng thái `LOCKED`, toàn bộ các vé đang giữ bị giải phóng về kho vé cho khán giả thật, và toàn bộ phiên đăng nhập của kẻ đầu cơ bị vô hiệu hóa tức thì.

---

### 5.7. Tab 7: Khôi phục Sự cố, Circuit Breaker Cổng VNPAY & Replay Outbox/DLQ
- **Mục đích:** Thao diễn tính năng tự phục hồi lỗi phân tán (Fault Tolerance) chuẩn kiến trúc doanh nghiệp.
- **Thao tác Thử nghiệm Bật Circuit Breaker (Cắt cầu dao bảo vệ cổng VNPAY):**
  1. Bấm vào **Tab 7: Khôi phục & Circuit Breaker**.
  2. Nhìn thấy trạng thái cầu dao VNPAY hiện tại: **`CLOSED (HOẠT ĐỘNG BÌNH THƯỜNG)`** màu xanh.
  3. Bấm nút màu đỏ: **`⚠️ Cắt cầu dao (Trip Circuit Breaker)`**. Trạng thái lập tức nhảy sang **`OPEN (ĐANG CẮT CẦU DAO BẢO VỆ)`** màu đỏ.
  4. **Kiểm chứng phía người dùng:** Mở tab khách hàng vào màn hình Checkout, hệ thống hiển thị ngay thông báo bảo trì trang trọng: *"Cổng thanh toán VNPAY đang tạm bảo trì để nâng cấp định kỳ. Đơn hàng và vị trí ghế của bạn vẫn được giữ nguyên an toàn"*.
  5. **Bật lại cầu dao:** Quay lại Tab 7 của Admin, bấm nút **`🔄 Đặt lại cầu dao (Reset Circuit Breaker)`**. Cầu dao chuyển về `CLOSED`, khách hàng có thể tiếp tục thanh toán bình thường.
- **Thao tác Replay sự kiện Outbox / Dead Letter Queue (DLQ):**
  1. Xem danh sách các sự kiện Kafka Outbox và hàng đợi lỗi Dead Letter Queue.
  2. Nếu có sự kiện bị lỗi do mất kết nối mạng tạm thời, bấm nút **`🔁 Replay sự kiện`**.
  3. Hệ thống sẽ phát lại sự kiện vào Kafka với cùng `eventId` ban đầu. Bộ tiêu thụ Inbox Consumer sẽ tự động dedup chống trùng lặp, đảm bảo tính nhất quán dữ liệu tuyệt đối (At-least-once + Deduplication).

---

## PHẦN 6: BẢNG XỬ LÝ NHANH SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

| Tình huống / Thông báo lỗi | Nguyên nhân thường gặp | Cách xử lý nhanh trong 10 giây |
| :--- | :--- | :--- |
| **Báo lỗi `NETWORK_ERROR` hoặc "Không nhận được phản hồi từ máy chủ"** | Vô tình đóng mất cửa sổ màu đen **`EventFlow Backend (8080)`** hoặc Backend chưa khởi động xong. | 1. Nhấp đúp vào file `start-all.bat` để script tự động khởi động lại Backend.<br>2. Lưu ý: Chỉ thu nhỏ cửa sổ Backend xuống Taskbar, tuyệt đối không bấm nút X đóng cửa sổ. |
| **Chạy `start-all.bat` báo lỗi `Docker Desktop chưa được bật`** | Docker Desktop trên máy tính chưa được khởi động hoặc đang ở trạng thái dừng. | 1. Mở Start Menu → tìm và mở ứng dụng **Docker Desktop**.<br>2. Đợi góc dưới trái Docker hiện biểu tượng cá voi màu xanh lá cây.<br>3. Chạy lại `start-all.bat`. |
| **Muốn test luồng 2 người cùng lúc (Tặng vé / Bán lại Resale / Tranh chấp giữ ghế)** | Cùng một cửa sổ trình duyệt chỉ lưu một phiên đăng nhập trong LocalStorage. | 1. Mở **Cửa sổ thường**: Đăng nhập tài khoản Người bán / Người tặng.<br>2. Bấm phím **`Ctrl + Shift + N`** mở **Cửa sổ ẩn danh**: Đăng nhập tài khoản Người mua / Người nhận vé.<br>3. Hai cửa sổ hoạt động hoàn toàn độc lập với nhau. |
| **Không thấy các sự kiện Ghế chỉ định (`SEATED`) ngoài Trang chủ** | Bộ lọc phân loại đang được chọn ở mục khác hoặc chưa cuộn hết trang. | 1. Trên thanh danh mục ngang Ticketbox ngay dưới Header, bấm vào nút **`🪑 Ghế chỉ định (SEATED)`**.<br>2. Hoặc gõ từ khóa `Beethoven` hoặc `Vũ` vào thanh tìm kiếm. |
| **Quét vé tại Cổng Staff báo lỗi `UNAUTHORIZED` (Không có quyền)** | Tài khoản đang đăng nhập chỉ là khách hàng thường (`CUSTOMER`), chưa được cấp quyền Staff. | 1. Đăng xuất và đăng nhập bằng tài khoản Quản trị: `admin@eventflow.vn` / `Admin@123`.<br>2. Hoặc dùng tài khoản Admin vào Tab Người dùng để bấm **`+ STAFF`** cho tài khoản của bạn. |
| **Sau khi Admin duyệt hồ sơ Nhà tổ chức, vào `/organizer` vẫn báo chưa có quyền** | Trình duyệt còn lưu token JWT cũ chưa được làm mới quyền hạn mới. | 1. Nhấn phím **`F5`** để tải lại trang (bộ lọc token của hệ thống sẽ tự động gọi Refresh Token để nạp vai trò `ORGANIZER` mới vào phiên).<br>2. Hoặc Đăng xuất ra và Đăng nhập lại. |

---

*Tài liệu được biên soạn đồng bộ chính xác 100% với phiên bản EventFlow Enterprise 2026 (Flyway V29, 358 Backend Tests PASS, 84 Frontend Tests PASS, 47 OpenAPI Operations).*
