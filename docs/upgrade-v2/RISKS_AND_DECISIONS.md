# EVENTFLOW V2 — PHÂN TÍCH SO SÁNH PHƯƠNG ÁN, MA TRẬN RỦI RO & BẢN GHI QUYẾT ĐỊNH (RISKS & DECISIONS)
## COMPARATIVE TECHNICAL EVALUATION, RISK REGISTRY & ARCHITECTURAL DECISION RECORDS (ADR)

> **Dự án:** EventFlow — High-Concurrency Commercial Event Ticketing Platform  
> **Chuyên gia phụ trách:** Principal Software Architect, Security Architect, Database Architect, Product Manager  
> **Phiên bản:** V2.0-PROPOSAL · **Mã tài liệu:** `RISK-DEC-REC-V2-01`  
> **Trạng thái:** Bản đề xuất kỹ thuật (Trình Chủ dự án và Tech Lead thẩm định & phê duyệt)

---

## 1. NGUYÊN TẮC RA QUYẾT ĐỊNH KỸ THUẬT (DECISION FRAMEWORK)

Mọi đề xuất kiến trúc trong EventFlow V2 phải vượt qua bài kiểm tra "3 Phù Hợp":
1. **Phù hợp với Đồ án Cá nhân Cao cấp:** Giữ được chiều sâu kỹ thuật backend (EDA, Kafka, Transactional Outbox, Row-lock ACID, Idempotency), không lạm dụng SaaS bên ngoài làm mất giá trị kỹ thuật tự phát triển, nhưng vẫn bảo đảm tính hoàn chỉnh của một sản phẩm thương mại thực tế.
2. **Phù hợp với Hạ tầng Hiện hữu:** Vận hành tối ưu trên Java 25, Spring Boot 4.1.2, React 19, TypeScript, PostgreSQL 17 và Apache Kafka 4.2 trong môi trường Docker Compose gọn nhẹ.
3. **Phù hợp với An toàn Nghiệp vụ:** Bảo toàn 100% các thành quả V1 (Zero Oversell, Flyway V1-V10 bất biến, Simulator cho CI), không đưa ra các quyết định làm tăng rủi ro bảo mật hoặc chi phí vận hành ngoài tầm kiểm soát.

---

## 2. PHÂN TÍCH SO SÁNH CHI TIẾT CÁC PHƯƠNG ÁN KỸ THUẬT LỚN

---

### Quyết định 1: Kiến Trúc Xác Thực & Quản Lý Phiên Làm Việc (Authentication & Session)

| Tiêu chí | Phương án 1A: JWT Hoàn Toàn Stateless (Chỉ dùng Access Token dài hạn) | Phương án 1B: Stateful Session (Lưu Session ID trong Redis/DB) | Phương án 1C (ĐỀ XUẤT): Hybrid JWT Access Token + HttpOnly Refresh Token Family |
|---|---|---|---|
| **Mô tả** | Client lưu JWT (thời hạn 24h-7 ngày) trong LocalStorage, gửi qua header `Authorization`. | Server lưu session trong Redis/PostgreSQL; client chỉ giữ Session Cookie mờ. | Access Token ngắn hạn (15 phút) lưu trong bộ nhớ; Refresh Token dài hạn (7 ngày) lưu HttpOnly Cookie, băm SHA-256 trong PostgreSQL với cơ chế Token Family luân chuyển. |
| **Ưu điểm** | Đơn giản, không tốn tài nguyên DB để kiểm tra phiên. | Dễ dàng thu hồi phiên ngay lập tức; an toàn trước XSS. | Kết hợp tốc độ của stateless JWT và khả năng thu hồi phiên tức thì; chống đánh cắp token qua XSS; phát hiện tấn công tái sử dụng (Reuse Detection). |
| **Nhược điểm** | Rủi ro rò rỉ token qua XSS cực cao; không thể thu hồi token khi bị lộ hoặc khi đổi mật khẩu. | Phụ thuộc Redis hoặc tăng tải I/O lên PostgreSQL cho mọi HTTP request. | Cần cài đặt bảng `refresh_tokens` và logic xoay vòng chuỗi token. |
| **Độ phức tạp** | Rất thấp (1/5) | Trung bình (3/5) | Trung bình - Khá (3.5/5) |
| **Chi phí** | 0 VNĐ | Tốn thêm RAM cho Redis nếu dùng | 0 VNĐ (Tận dụng PostgreSQL 17 có sẵn) |
| **Rủi ro** | Rủi ro bảo mật rất cao (Không đạt chuẩn an toàn thương mại). | Rủi ro Single Point of Failure nếu Redis sập. | Rủi ro cấu hình sai Cookie SameSite trên môi trường khác domain. |
| **Khả năng triển khai** | Dễ dàng | Trung bình | Khả thi cao với Spring Security |
| **Độ phù hợp đồ án** | Thấp (Kém chuyên nghiệp) | Trung bình | **Rất cao (Thể hiện tư duy Senior Security)** |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 1C**.  
> **Lý do:** Đây là chuẩn công nghiệp hiện đại (OAuth 2.0 BCP). Khách hàng không bị đăng xuất liên tục nhưng hacker không thể đánh cắp refresh token từ JavaScript context.

---

### Quyết định 2: Tích Hợp Đăng Nhập Google (Google OIDC Integration)

| Tiêu chí | Phương án 2A: Frontend Client-Side Only (Google Identity Services JS) | Phương án 2B (ĐỀ XUẤT): Backend Authorization Code Flow với PKCE | Phương án 2C: Sử dụng Dịch vụ Ủy quyền SaaS (Auth0 / Firebase Auth) |
|---|---|---|---|
| **Mô tả** | Frontend dùng thư viện JS của Google lấy `id_token`, rồi gửi token này lên Backend để tin cậy. | Frontend chuyển hướng sang Google OAuth URL; Google callback về Backend; Backend trao đổi code lấy token qua kênh bảo mật và xác minh chữ ký Google JWKS. | Tích hợp Auth0/Firebase làm bên thứ ba trung gian quản lý toàn bộ đăng nhập. |
| **Ưu điểm** | Frontend làm rất nhanh, backend chỉ cần 1 endpoint decode token. | Chuẩn bảo mật OAuth 2.1; giấu kín Client Secret phía Backend; bảo vệ CSRF bằng `state` parameter; kiểm soát hoàn toàn dữ liệu tài khoản. | Cực kỳ nhanh, có sẵn mọi nhà cung cấp (Google, Apple, Facebook). |
| **Nhược điểm** | Dễ bị giả mạo client; phụ thuộc chặt vào thư viện JS bên thứ ba của Google trên trình duyệt. | Phải quản lý endpoint callback và xử lý redirect giữa backend và frontend. | Tốn phí khi vượt quota; phụ thuộc hoàn toàn vào bên thứ ba; giảm giá trị tự phát triển của đồ án. |
| **Độ phức tạp** | Thấp (2/5) | Trung bình (3/5) | Rất thấp (1.5/5) |
| **Chi phí** | 0 VNĐ | 0 VNĐ | Có thể phát sinh phí hàng tháng |
| **Rủi ro** | Rủi ro bypass validation nếu backend kiểm tra token lỏng lẻo. | Rủi ro callback URL không trùng khớp giữa Google Cloud Console và server. | Rủi ro phụ thuộc vendor (Vendor Lock-in). |
| **Khả năng triển khai** | Rất dễ | Khả thi cao trong Spring Boot | Rất dễ |
| **Độ phù hợp đồ án** | Trung bình | **Rất cao (Chứng minh năng lực Full-stack Security)** | Thấp (Mất chất kỹ thuật đồ án) |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 2B**.  
> **Lý do:** Giữ toàn bộ quyền sở hữu dữ liệu tài khoản trong PostgreSQL 17, tuân thủ đúng chuẩn OAuth 2.1 Backend-mediated Flow mà không tốn chi phí thuê SaaS ngoài.

---

### Quyết định 3: Phân Quyền Bốn Vai Trò & Ranh Giới Sự Kiện (RBAC & Event Scope)

| Tiêu chí | Phương án 3A: Cột `role` đơn lẻ trong bảng `users` | Phương án 3B (ĐỀ XUẤT): Bảng quan hệ `roles` + `user_roles` + Event Scope Service | Phương án 3C: Khung phân quyền phức tạp ABAC / OPA (Open Policy Agent) |
|---|---|---|---|
| **Mô tả** | Bổ sung cột `role VARCHAR(32)` trong bảng `users`, kiểm tra quyền chỉ bằng chuỗi role. | Bảng chuẩn hóa `roles` và `user_roles`; 1 user có thể có nhiều role; kết hợp `@PreAuthorize` và Service kiểm tra quyền sở hữu sự kiện (`EventSecurityService`). | Tích hợp một Policy Engine độc lập để đánh giá quyền theo ngữ cảnh thời gian, địa điểm, thuộc tính. |
| **Ưu điểm** | Dễ làm, truy vấn 1 bảng duy nhất. | Linh hoạt, cho phép 1 tài khoản vừa là Khách hàng vừa là Nhà tổ chức; cô lập chặt chẽ dữ liệu giữa các Organizer và Staff; tương thích ngược hoàn hảo. | Kiểm soát cực kỳ chi tiết đến từng thuộc tính của request. |
| **Nhược điểm** | 1 người dùng không thể có 2 vai trò; không giải quyết được ranh giới sự kiện (Organizer A sửa sự kiện của Organizer B). | Cần join bảng khi truy vấn quyền; cần viết custom security expression trong Spring. | Quá mức phức tạp (Over-engineering), cồng kềnh cho đồ án và hệ thống hiện tại. |
| **Độ phức tạp** | Rất thấp (1/5) | Trung bình (3/5) | Rất cao (5/5) |
| **Chi phí** | 0 VNĐ | 0 VNĐ | Chi phí bảo trì cao |
| **Rủi ro** | Lỗ hổng IDOR (Insecure Direct Object Reference) cực kỳ nghiêm trọng. | Rủi ro cấu hình thiếu annotation bảo vệ method. | Rủi ro độ trễ đánh giá policy làm chậm API. |
| **Khả năng triển khai** | Rất dễ | Khả thi cao | Rất khó |
| **Độ phù hợp đồ án** | Kém | **Rất cao (Cân bằng xuất sắc giữa tính bảo mật và thực tế)** | Quá phức tạp |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 3B**.  
> **Lý do:** Vừa bảo đảm phân quyền vai trò (Role-based) toàn hệ thống, vừa giải quyết triệt để rủi ro Organizer A xâm phạm sự kiện của Organizer B thông qua kiểm tra Event Scope.

---

### Quyết định 4: Lựa Chọn Cổng Thanh Toán Đối Tác (Payment Gateway Selection)

> [!IMPORTANT]
> **Quy tắc tư vấn:** Theo mục 6 của chỉ thị, đội ngũ chuyên gia KHÔNG tự ý chốt nhà cung cấp cuối cùng khi chưa được Chủ dự án duyệt. Dưới đây là bảng phân tích chi tiết để Chủ dự án lựa chọn:

| Tiêu chí | Lựa chọn 4.1: VNPAY Sandbox (ĐỀ XUẤT) | Lựa chọn 4.2: PayOS Sandbox | Lựa chọn 4.3: MoMo Sandbox |
|---|---|---|---|
| **Mô tả** | Cổng thanh toán quốc gia phổ biến nhất tại Việt Nam (ATM, QR ngân hàng, Visa/Master). | Cổng thanh toán mở tạo QR thanh toán qua VietQR Open Banking. | Ví điện tử cá nhân phổ biến nhất Việt Nam. |
| **Ưu điểm** | Độ nhận diện thương hiệu cao nhất; tài liệu API Sandbox đầy đủ, có môi trường test giả lập đa dạng ngân hàng. | API hiện đại, webhook dễ debug, giao diện lập trình viên thân thiện; miễn phí nhiều tính năng. | Trải nghiệm thanh toán trên di động bằng ứng dụng MoMo rất mượt mà. |
| **Nhược điểm** | Hệ thống test thỉnh thoảng bảo trì; thuật toán sinh checksum HMAC-SHA512 yêu cầu sắp xếp tham số cực kỳ chuẩn xác. | Ít phổ biến hơn VNPAY trong các sự kiện hòa nhạc/hội nghị quy mô lớn; tập trung vào chuyển khoản quét mã VietQR. | Quy trình đăng ký tài khoản doanh nghiệp phức tạp hơn; kiểm tra chặt chẽ IP tĩnh của webhook. |
| **Độ phức tạp** | Trung bình (3/5) | Thấp - Trung bình (2.5/5) | Trung bình (3/5) |
| **Chi phí Sandbox** | Miễn phí | Miễn phí | Miễn phí |
| **Kiến trúc tích hợp** | Ports & Adapters (`PaymentGatewayPort` + `VnPayGatewayAdapter` + giữ nguyên `SimulatorAdapter` cho CI). |
| **Độ phù hợp đồ án** | **Rất cao (Chuẩn doanh nghiệp thương mại)** | Rất cao (Thân thiện dev) | Cao |

> **Khuyến nghị của Đội ngũ Kỹ thuật:** Đề xuất ưu tiên **Lựa chọn 4.1 (VNPAY Sandbox)** hoặc **Lựa chọn 4.2 (PayOS Sandbox)**. Cả hai đều được trừu tượng hóa qua giao diện `PaymentGatewayPort`, do đó chuyển đổi giữa hai bên chỉ mất chưa đầy 1 ngày làm việc mà không làm thay đổi logic lõi của hệ thống đơn hàng!

---

### Quyết định 5: Lưu Trữ Ảnh Poster & Tư Liệu Sự Kiện (File & Media Storage)

| Tiêu chí | Phương án 5A: Lưu Base64 trực tiếp vào Database | Phương án 5B (ĐỀ XUẤT): Local Server Storage kèm Nginx/Spring Resource Mapping | Phương án 5C: Cloud Object Storage (AWS S3 / Cloudflare R2) |
|---|---|---|---|
| **Mô tả** | Mã hóa chuỗi base64 của file ảnh và lưu vào cột `TEXT` trong PostgreSQL. | Lưu file ảnh vào thư mục được mount trên máy chủ (`/var/eventflow/uploads`); database chỉ lưu đường dẫn URL metadata. | Tải file trực tiếp lên AWS S3 hoặc Cloudflare R2 qua Pre-signed URL. |
| **Ưu điểm** | Không cần quản lý thư mục file, backup database là có tất cả ảnh. | Đơn giản, tốc độ phục vụ cực nhanh, chi phí 0 VNĐ, dễ dàng chạy bằng Docker Volume trên máy local. | Khả năng mở rộng vô hạn, có CDN toàn cầu, không tốn dung lượng ổ đĩa máy chủ. |
| **Nhược điểm** | Làm phình to dung lượng database nghiêm trọng; làm chậm backup, restore và I/O của PostgreSQL. | Cần quản lý quyền truy cập thư mục và volume mount trong Docker Compose; khó mở rộng nếu chạy nhiều cụm server không chung ổ đĩa. | Cần tạo tài khoản thẻ tín dụng quốc tế; chi phí hàng tháng; cấu hình IAM/CORS phức tạp; khó chạy offline/test tự động. |
| **Độ phức tạp** | Thấp (1/5) | Thấp - Trung bình (2/5) | Trung bình - Khá (3.5/5) |
| **Chi phí** | Tốn chi phí lưu trữ DB | 0 VNĐ | Phát sinh chi phí đám mây |
| **Độ phù hợp đồ án** | Cực kỳ tệ (Chống chỉ định) | **Rất cao (Thực tế, gọn nhẹ, tự túc 100%)** | Trung bình (Tốn kém không cần thiết) |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 5B** cho giai đoạn hiện tại.  
> **Lý do:** Tách biệt hoàn toàn file nhị phân khỏi PostgreSQL 17 (bảo vệ hiệu năng DB), mount volume Docker an toàn, không làm phát sinh chi phí AWS/Cloud cho sinh viên/chủ dự án.

---

### Quyết định 6: Gửi Email Thông Báo & Vé Điện Tử (Email & Notifications)

| Tiêu chí | Phương án 6A: Gửi đồng bộ trực tiếp trong Controller (Sync JavaMailSender) | Phương án 6B (ĐỀ XUẤT): Bất đồng bộ qua Transactional Outbox + Kafka Consumer | Phương án 6C: Sử dụng Dịch vụ SaaS (SendGrid / Resend API) |
|---|---|---|---|
| **Mô tả** | Khi đặt vé xong, gọi thẳng thư viện JavaMail để gửi email trước khi trả lời HTTP client. | Sự kiện `BookingConfirmedEvent` được ghi vào bảng `outbox_events` trong cùng ACID transaction; Kafka Consumer tiếp nhận sự kiện và gửi email bất đồng bộ qua SMTP. | Gọi API REST của SendGrid/Resend gửi mail từ controller. |
| **Ưu điểm** | Code rất ngắn, chỉ vài dòng. | Tốc độ phản hồi API tức thì (<50ms); không làm chậm khách đặt vé; nếu máy chủ SMTP lỗi, Kafka có cơ chế retry tự động, không làm mất email vé. | Template email đẹp, tỷ lệ vào Inbox cao, có dashboard thống kê tỉ lệ mở. |
| **Nhược điểm** | Nếu máy chủ mail chập chờn, API đặt vé bị treo 5-10s hoặc timeout; khách hàng tưởng đơn hàng lỗi và bấm lại nhiều lần. | Cần viết thêm Kafka consumer listener cho email. | Tốn chi phí sau khi hết gói free; phụ thuộc API key ngoài. |
| **Độ phức tạp** | Rất thấp (1/5) | Trung bình (3/5) | Thấp - Trung bình (2/5) |
| **Chi phí** | 0 VNĐ (Dùng Gmail SMTP free) | 0 VNĐ | Phát sinh phí khi vượt quota |
| **Độ phù hợp đồ án** | Kém (Thiếu tư duy hiệu năng) | **Rất cao (Thể hiện trọn vẹn sức mạnh Event-Driven Architecture)** | Trung bình |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 6B**.  
> **Lý do:** Tận dụng tối đa hạ tầng Apache Kafka và Transactional Outbox đã được xây dựng công phu ở baseline V1, bảo đảm trải nghiệm đặt vé của khách hàng luôn dưới 50ms!

---

### Quyết định 7: Chiến Lược Tiến Hóa Cơ Sở Dữ Liệu (Database Evolution)

| Tiêu chí | Phương án 7A: Sửa trực tiếp các migration cũ V1-V10 | Phương án 7B (ĐỀ XUẤT): Flyway Additive Migrations V11+ | Phương án 7C: Tách Database riêng biệt theo Microservices |
|---|---|---|---|
| **Mô tả** | Mở các file `V1__...sql` đến `V10__...sql` để bổ sung cột và bảng mới. | Khóa bất biến V1-V10; toàn bộ bảng và quan hệ mới được tạo bằng các file `V11__...sql` trở đi; chỉ dùng phép toán additive. | Tách PostgreSQL thành 4 DB riêng: Auth DB, Catalog DB, Booking DB, Payment DB. |
| **Ưu điểm** | File migration trông gọn gàng, ít file. | An toàn tuyệt đối 100%; không làm gãy các môi trường đang chạy; rollback dễ dàng; giữ nguyên tính tương thích ngược với fixtures. | Các team độc lập có thể sửa DB riêng mà không cần chờ nhau. |
| **Nhược điểm** | Phá vỡ toàn bộ checksum Flyway; làm hỏng database của môi trường dev/staging; làm gãy toàn bộ bài test hồi quy. | Cần cẩn trọng khi thiết kế khóa ngoại giữa các bảng mới và bảng cũ. | Mất khả năng ACID transaction nguyên tử; cần triển khai Saga 2PC cực kỳ phức tạp; chi phí vận hành khổng lồ. |
| **Độ phức tạp** | Tưởng chừng thấp nhưng rủi ro cực cao | Trung bình (2.5/5) | Cực kỳ phức tạp (5/5) |
| **Rủi ro** | **Phá vỡ quy tắc bất biến của dự án!** | Rất thấp | Rất cao |
| **Độ phù hợp đồ án** | **BỊ CẤM** | **Tuyệt đối phù hợp và bắt buộc** | Không phù hợp với Modular Monolith |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 7B**. Tuân thủ nghiêm ngặt quy tắc bất biến của dự án.

---

### Quyết định 8: Kiến Trúc Giao Diện Người Dùng (Frontend Architecture)

| Tiêu chí | Phương án 8A: SPA dồn nén toàn bộ tính năng trên 1 màn hình đơn lẻ | Phương án 8B (ĐỀ XUẤT): Multi-Page HTML5 History Router + Modular Page Architecture | Phương án 8C: Đập đi viết lại bằng Next.js / Nuxt Full SSR |
|---|---|---|---|
| **Mô tả** | Toàn bộ các chức năng (Mua vé, Giỏ hàng, Admin, Báo cáo) nhồi chung vào 1 file view duy nhất, ẩn hiện bằng cờ state. | Tách biệt các trang thành các module độc lập (`/`, `/dat-ve`, `/giai-phap`, `/bang-gia`, `/ve-cua-toi`, `/organizer`, `/staff`, `/admin`); quản lý qua Client-side History Router; hỗ trợ mở tab mới và URL bookmarking. | Bỏ source Vite/TypeScript hiện tại, cài đặt lại từ đầu một framework SSR như Next.js. |
| **Ưu điểm** | Nhanh chóng cho bài test demo ban đầu. | Trải nghiệm duyệt web doanh nghiệp chuyên nghiệp (chuẩn Sao Kim Branding); mở nhiều tab làm việc song song; dễ bảo trì; giữ nguyên Vite/TypeScript cực nhanh. | Tối ưu SEO mạnh mẽ từ máy chủ. |
| **Nhược điểm** | Trải nghiệm tù túng, khách hàng không thể mở tab mới; khó mở rộng; giao diện chật chội; không đạt chuẩn thương mại. | Cần quản lý đồng bộ trạng thái đăng nhập xuyên suốt các trang. | Lãng phí toàn bộ mã nguồn frontend hiện có; tốn thêm 3-4 tuần cấu hình lại; quá tải công việc. |
| **Độ phức tạp** | Thấp (1.5/5) | Trung bình (2.5/5) | Rất cao (4.5/5) |
| **Độ phù hợp đồ án** | Kém (Trông như bài tập sơ sài) | **Rất cao (Đạt chuẩn website thương mại cao cấp)** | Thấp (Rủi ro vỡ tiến độ) |

> **Khuyến nghị tốt nhất:** Chọn **Phương án 8B**. Đã được chứng minh bằng các trang đa tab mẫu được người dùng đánh giá cao.

---

## 3. BẢNG MA TRẬN RỦI RO & BIỆN PHÁP KIỂM SOÁT (RISK REGISTRY)

| Mã Rủi Ro | Phân Loại | Mô Tả Rủi Ro Kỹ Thuật | Khả Năng (1-5) | Tác Động (1-5) | Điểm Rủi Ro | Biện Pháp Phòng Ngừa & Kiểm Soát (Mitigation & Contingency) |
|---|---|---|---|---|---|---|
| **R-01** | Nghiệp vụ | **Bán lố vé khi lượng truy cập tăng vọt (Oversell under High Concurrency)** | 2 | 5 | 10 | **Phòng ngừa:** Giữ vững row-level lock `SELECT FOR UPDATE` trên PostgreSQL trong cùng transaction giữ vé; sắp xếp ID ghế tăng dần chống Deadlock; không dùng in-memory cache thay cho DB locking. |
| **R-02** | Bảo mật | **Leo quyền giữa các vai trò hoặc Organizer A sửa sự kiện của Organizer B (IDOR / Broken Access Control)** | 2 | 5 | 10 | **Phòng ngừa:** Bật Spring Method Security `@PreAuthorize`; kiểm tra Event Scope hai tầng (Role check + Ownership DB check); không tin cậy dữ liệu ID gửi từ frontend. |
| **R-03** | Bảo mật | **Đánh cắp Refresh Token qua lỗ hổng XSS (Token Theft)** | 2 | 4 | 8 | **Phòng ngừa:** Refresh Token lưu trữ trong Cookie `HttpOnly; Secure; SameSite=Strict`; băm SHA-256 trong DB; kích hoạt cơ chế Token Family Reuse Detection (thu hồi cả chuỗi nếu token bị dùng lại). |
| **R-04** | Thanh toán | **Tấn công giả mạo kết quả thanh toán Webhook IPN (Fake IPN / Man-in-the-Middle)** | 2 | 5 | 10 | **Phòng ngừa:** Bắt buộc xác minh chữ ký số HMAC-SHA512 với secret key; kiểm tra đối soát số tiền và mã merchant ref với DB trước khi xác nhận đơn hàng; IPN xử lý idempotent 100%. |
| **R-05** | Thanh toán | **Khách thanh toán sau khi đơn hàng đã hết hạn giữ chỗ (Late Payment Overcommitted)** | 3 | 4 | 12 | **Phòng ngừa:** Nếu đơn hàng đã quá hạn và vé đã bán cho người khác: Chuyển trạng thái sang `REFUND_REQUIRED`, tự động tạo nghĩa vụ hoàn tiền; tuyệt đối không cướp vé của khách hàng mới. |
| **R-06** | Vận hành | **Quét trùng mã vé tại cổng sự kiện (Double Check-in Fraud)** | 3 | 4 | 12 | **Phòng ngừa:** Đặt ràng buộc cứng `UNIQUE(ticket_id)` trên bảng `ticketing.ticket_check_ins`; database sẽ từ chối ngay lập tức lần quét thứ hai và trả về HTTP 409 Conflict với thông tin thời gian quét lần đầu. |
| **R-07** | Hạ tầng | **Kafka Broker sập hoặc gián đoạn kết nối (Kafka Outage)** | 2 | 4 | 8 | **Phòng ngừa:** Sử dụng Transactional Outbox Pattern; API đặt vé và thanh toán ghi outbox event vào PostgreSQL trong cùng ACID transaction; tiến trình Outbox Poller sẽ tự động phát lại khi Kafka hồi phục. |
| **R-08** | Dữ liệu | **Làm hỏng dữ liệu hoặc checksum Flyway khi chạy migration mới (Migration Drift)** | 1 | 5 | 5 | **Phòng ngừa:** Toàn bộ migration V1-V10 là bất biến; chỉ tạo migration V11+; kiểm thử migration trên bản sao database trước khi áp dụng. |
| **R-09** | Tích hợp | **Cổng thanh toán Sandbox bên ngoài bị lỗi mạng hoặc bảo trì (Gateway Timeout)** | 3 | 3 | 9 | **Phòng ngừa:** Duy trì song song Payment Simulator nội bộ trong profile test và local dev; client có cơ chế polling trạng thái thanh toán tự động. |
| **R-10** | Trải nghiệm | **Trình duyệt di động của nhân viên không cho phép mở camera quét QR (Camera Permission Denied)** | 3 | 3 | 9 | **Phòng ngừa:** Thiết kế sẵn ô nhập mã vé thủ công (Manual Code Entry) trên giao diện Staff để không làm tắc nghẽn luồng khách tại cổng. |

---

## 4. TỔNG HỢP CÁC QUYẾT ĐỊNH CẦN CHỦ DỰ ÁN & TECH LEAD DUYỆT (DECISIONS AWAITING APPROVAL)

Trước khi chuyển sang Giai đoạn 1 (Task T12), kính đề nghị Chủ dự án và Tech Lead xem xét và chính thức phê duyệt các điểm chốt chiến lược sau:

1. **Phê duyệt lựa chọn Cổng thanh toán Sandbox:**
   - [ ] **Lựa chọn 1 (Khuyến nghị của Đội ngũ):** Tích hợp cổng **VNPAY Sandbox** (HMAC-SHA512, redirect web, IPN webhook).
   - [ ] **Lựa chọn 2:** Tích hợp cổng **PayOS Sandbox** (VietQR Open Banking, webhook hiện đại).
   - *(Cả hai đều giữ nguyên Payment Simulator cho automated test suite).*
2. **Phê duyệt Cơ chế Quản lý Phiên làm việc (Session Management):**
   - [ ] **Đồng ý:** Triển khai cơ chế Hybrid (JWT Access Token trong Memory + Refresh Token trong HttpOnly Cookie có băm SHA-256 và Reuse Detection trong PostgreSQL 17).
3. **Phê duyệt Phương án Lưu trữ Hình ảnh Sự kiện:**
   - [ ] **Đồng ý:** Lưu trữ trên máy chủ/Docker Volume nội bộ (Local Disk Storage) kèm đường dẫn metadata trong PostgreSQL 17; chưa cần thuê AWS S3 để tiết kiệm chi phí.
4. **Phê duyệt Lộ trình Triển khai 8 Giai đoạn (T11 ➔ T19):**
   - [ ] **Đồng ý:** Phê duyệt lộ trình tuần tự có kiểm soát Cổng (Quality Gates G0 ➔ G8); cam kết không code Giai đoạn sau khi Giai đoạn trước chưa đạt yêu cầu.

---

*Hết tài liệu RISKS_AND_DECISIONS.md*
