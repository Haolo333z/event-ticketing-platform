# EventFlow Full-stack Product V2 — Bộ tài liệu nâng cấp

**Ngày chốt yêu cầu:** 2026-11-16  
**Nguồn baseline:** `docs.zip` do chủ dự án cung cấp, gồm PRD v0.4, ARCHITECTURE, DESIGN, DATA_MODEL, API_CONTRACT/OpenAPI, TASKS, PROGRESS, HANDOFF, ADR0001–ADR0008 và evidence đến T10.3.  
**Trạng thái:** Phạm vi V2 được chốt theo lựa chọn của chủ dự án; đây là tài liệu yêu cầu/kế hoạch, chưa phải bằng chứng tính năng đã triển khai.

## 1. Cấu hình đã chốt

1. **Bốn vai trò:** `CUSTOMER`, `STAFF`, `ORGANIZER`, `ADMIN`.
2. **Xác thực:** đăng ký/đăng nhập email + mật khẩu và đăng nhập Google (OpenID Connect). “Social” được hiểu theo lựa chọn trước đó là bổ sung Google Sign-In, không thay thế tài khoản email/mật khẩu.
3. **Thanh toán:** tích hợp một cổng thanh toán bên ngoài thật ở môi trường sandbox trước; không chỉ dùng simulator làm luồng thanh toán dành cho khách hàng. Chưa chốt nhà cung cấp cụ thể. VNPAY là lựa chọn khuyến nghị đầu tiên cho thị trường Việt Nam, nhưng phải xác nhận tài khoản sandbox và thông tin merchant trước khi viết adapter.
4. **Database:** PostgreSQL tiếp tục là nguồn dữ liệu nghiệp vụ chính. Tất cả account, role, event ownership, event staff assignment, booking, payment attempts, callback receipts, refunds, tickets và check-in phải được lưu bền vững bằng SQL.
5. **Kiến trúc:** giữ modular monolith Java/Spring Boot + React/TypeScript + PostgreSQL + Kafka/EDA. Không chuyển .NET, không tự thêm Redis, không viết lại booking engine.

## 2. Phân biệt baseline và V2

Tài liệu baseline v0.4 ghi nhận 44/45 task Done và T10.4 còn Todo tại thời điểm snapshot. Baseline có 18 bảng, Flyway V1–V9 và OpenAPI 18 operation. Authentication hiện là mock bearer token local/test cho hai persona demo `CUSTOMER/ADMIN`; payment hiện là simulator local/test. UI customer/admin đã có nhưng chưa được visual QA trực tiếp trên Chrome theo các ghi chú trong DESIGN/PROGRESS.

V2 bổ sung các yêu cầu mới: bốn role thật; email/password + Google OIDC; phân quyền theo phạm vi event; organizer/staff workflow; một gateway sandbox; callback idempotent; refund/reconciliation; vé và check-in thực tế; customer account journeys; UI sản phẩm hoàn chỉnh.

**Không được đổi trạng thái T1–T10, không được đánh dấu T10.4 Done bằng tài liệu V2.** T10.4 vẫn là phần đóng gói baseline v0.4 cho đến khi có bằng chứng riêng. V2 bắt đầu bằng re-baseline source code và tạo các task tiếp nối (T11+).

## 3. Cách đưa tài liệu vào repository

Giải nén thư mục `docs/upgrade-v2/` vào repository hiện tại. Không ghi đè PRD v0.4, TASKS, PROGRESS, HANDOFF, DATA_MODEL, OpenAPI hoặc các ADR cũ.

Sau khi AI trong VS Code kiểm tra source code:

- Thêm liên kết từ `docs/PRD.md` đến `docs/upgrade-v2/PRODUCT_SCOPE_V2.md`.
- Thêm các task T11+ vào cuối `docs/TASKS.md` hoặc tạo `docs/TASKS_V2.md` rồi liên kết rõ ràng; giữ nguyên task lịch sử.
- Thêm trạng thái “V2 planned / not implemented” vào `docs/PROGRESS.md`.
- Giữ `docs/HANDOFF.md` như snapshot lịch sử và tạo/cập nhật handoff mới sau khi bắt đầu T11.
- Chỉ chấp nhận schema/API/ADR mới sau review và kiểm tra tương thích với source hiện tại.

## 4. Thứ tự đọc

1. `SOURCE_REBASELINE.md` — Báo cáo kiểm toán kỹ thuật thực tế repository (Git, Flyway V1-V10, 19 bảng, Backend/Frontend).
2. `GAP_MATRIX.md` — Ma trận đối soát 79 yêu cầu V2 theo 4 trạng thái chuẩn (Đã hiệu chỉnh T11.2).
3. `BASELINE_VS_V2.md` — Chức năng baseline được ghi nhận và gap V2.
4. `PRODUCT_SCOPE_V2.md` — Phạm vi sản phẩm và quyền từng vai trò.
5. `UPGRADE_REQUIREMENTS_V2.md` — Yêu cầu chức năng, dữ liệu, API, bảo mật và nghiệm thu.
6. `ROADMAP_V2.md` — Giai đoạn và task phụ thuộc (T11 -> T19).
7. **Bộ tài liệu thiết kế kiến trúc V2 (T11.2 Deliverables):**
   - `ARCHITECTURE_V2.md` — Thiết kế kiến trúc tổng thể, C4 diagrams, 9 modules lõi và các bất biến được bảo toàn.
   - `DATA_MODEL_V2.md` — Mô hình dữ liệu ERD V2, đặc tả 15 bảng Flyway V11+ chi tiết.
   - `API_CONTRACT_V2.md` — Danh mục và đặc tả toàn bộ API theo chuẩn OpenAPI 3.1.
   - `SECURITY_MODEL_V2.md` — Mô hình phân quyền 4 vai trò, cơ chế Token Family và bảo mật Google OIDC.
   - `PAYMENT_GATEWAY_DECISION.md` — Đối soát nhà cung cấp cổng thanh toán và nguyên tắc an toàn tài chính.
   - `TEST_STRATEGY_V2.md` — Phân tích 121 skipped tests và chiến lược kiểm thử đa tầng.
   - `T11.2_DECISIONS.md` — Bản ghi các quyết định kiến trúc trọng yếu (DEC-001 đến DEC-010).
8. `adr/` — Các quyết định kiến trúc nền tảng (ADR-0009 đến ADR-0011).
9. `../../prompts/IMPLEMENT_UPGRADE_V2.md` — Prompt để AI tiếp nhận và bắt đầu triển khai.

## 5. Quy tắc trung thực

Các trạng thái “hiện có” trong bộ tài liệu này là trạng thái được ghi nhận trong tài liệu baseline ZIP, chưa phải kiểm tra trực tiếp source code hiện tại. T11.1 bắt buộc xác minh `git status`, HEAD, source code, migration, OpenAPI, test và runtime trước khi xác nhận gap nào còn tồn tại.
