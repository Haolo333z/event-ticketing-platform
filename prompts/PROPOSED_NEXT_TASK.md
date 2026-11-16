# PROMPT — HƯỚNG DẪN THỰC THI TASK T19: KIỂM THỬ HỒI QUY TOÀN DIỆN, BẢO MẬT & SỬA LỖI CI ĐỂ HOÀN TẤT V2 (PHASE 8 - RELEASE PHASE)

Bạn là **Principal Software Architect, Lead Security Engineer & Release Manager** tiếp quản repository `event-ticketing-platform` để triển khai **Task T19 (Phase 8 — Kiểm Thử Hồi Quy Toàn Diện, Bảo Mật, Sửa Lỗi CI & Đóng Gói Phát Hành V2)** theo đúng Lộ trình Nâng cấp EventFlow V2.

---

## 1. MỤC TIÊU CỦA TASK T19

Hoàn tất giai đoạn cuối cùng và đóng gói phát hành EventFlow V2:

1. **Khắc Phục 100% Các Lỗi Đang Bị Đỏ Trên GitHub Actions CI (`.github/workflows/ci.yml`):**
   - **Sửa lỗi Job `secrets` (Gitleaks):**
     - Tạo file `.gitleaksignore` tại thư mục gốc repository để whitelist chuỗi mock JWT secret dev `"EventFlow-Secure-JWT-Token-Secret-Key-2026-Super-Secret-Key-256bit-Enterprise"` (hoặc hash/path tương ứng) để Gitleaks không báo lỗi giả.
   - **Sửa lỗi Job `backend` (Live UI & DOM Integration Tests):**
     - Cập nhật các kịch bản kiểm thử tích hợp cũ trong `frontend/tests/live-ui.ts`, `frontend/tests/live-seats.ts`, `frontend/tests/live-payments.ts`, `frontend/tests/live-admin.ts`, `frontend/tests/live-recovery.ts` và `tools/contracts/verify-live.mjs`:
     - Bổ sung header xác thực `Authorization: Bearer demo-customer-token` / `demo-admin-token` tương thích với cơ chế 4 vai trò của V2 để các request tạo sự kiện, đặt vé không bị chặn 401/403.
     - Đảm bảo CI chạy trên GitHub Actions chuyển sang **MÀU XANH LÁ CÂY 100%**.

2. **Chạy Toàn Bộ Bộ Kiểm Thử Hồi Quy (Full Regression Suite) (T19.1):**
   - Backend Unit & Integration Tests: Kiểm tra toàn bộ 234+ tests (Zero Oversell, Outbox/Inbox Kafka, Token Family Session, RBAC 4 vai trò, Staff Check-in Concurrency, Admin Platform).
   - Frontend Unit Tests: Kiểm tra 71+ tests (`src/app/*.test.ts`, `src/api/*.test.ts`).
   - OpenAPI Contract Verification: Kiểm tra 69/69 cases hợp đồng OpenAPI 3.1.
   - Concurrency & Load Verification: Kiểm tra khả năng chịu tải đồng thời trên PostgreSQL 17 thật.

3. **Kiểm Tra Bảo Mật Chuyên Sâu (Security Hardening) (T19.2):**
   - Chống Privilege Escalation & Self-Lockout (Admin demote/lock self).
   - Chống IDOR (Insecure Direct Object Reference) trên vé, đơn hàng, phạm vi sự kiện Staff (`assertStaffAccess`).
   - Chống làm giả chữ ký số QR Code HMAC-SHA256 và chữ ký số VNPAY HMAC-SHA512.
   - Chống tấn công tái sử dụng Token Family (Refresh Token Reuse Detection).
   - Chống tấn công CSV Formula Injection trên xuất báo cáo doanh thu Excel.
   - Rà soát bí mật (Secret Scanning): Cam kết không có credential, password, token hay private key nhạy cảm nào bị đưa vào Git repo.

4. **Tập Dượt Migration & Toàn Vẹn Cơ Sở Dữ Liệu (T19.3):**
   - Kiểm tra tính tương thích của chuỗi 15 Flyway migrations (`V1` -> `V15`).
   - Xác nhận cấu trúc schema `ticketing` trên PostgreSQL 17 với đủ 35 bảng nghiệp vụ.

5. **Đóng Gói Phát Hành & Báo Cáo Đồ Án V2 (T19.4 & T19.5):**
   - Cập nhật tài liệu kỹ thuật: `ARCHITECTURE.md`, `DATA_MODEL.md`, `API_CONTRACT.md`, `README.md`.
   - Đánh dấu hoàn thành toàn bộ mục T19.1 đến T19.5 trong `docs/TASKS.md` và `docs/PROGRESS.md`.
   - Sẵn sàng gắn Git Tag `v2.0.0-release` để chính thức bàn giao dự án.

---

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI THỰC THI

1. **Kiểm tra trạng thái Git:**
   - Chạy lệnh: `git status --short --branch`, `git rev-parse HEAD`.
   - Giữ working tree sạch sẽ, không tự ý commit/push trừ khi có lệnh từ chủ dự án.
2. **Đọc tài liệu dự án:**
   - Đọc kỹ `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
3. **Bảo toàn bất biến cốt lõi:**
   - Giữ nguyên cam kết 0% bán lố vé (Zero Oversell Invariant), Transactional Outbox, và PostgreSQL ACID locks.
