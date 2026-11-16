# PROMPT — HƯỚNG DẪN THỰC THI TASK T15: CỔNG THANH TOÁN SANDBOX BÊN NGOÀI (EXTERNAL PAYMENT GATEWAY ADAPTER)

Bạn là Senior Full-stack Engineer & Security Specialist tiếp quản repository `event-ticketing-platform` để triển khai Task T15 (Phase 4 — Cổng Thanh Toán Sandbox Bên Ngoài & Xử Lý Giao Dịch Phức Tạp) theo đúng Lộ trình Nâng cấp EventFlow V2.

## 1. MỤC TIÊU CỦA TASK T15
Xây dựng phân hệ thanh toán hoàn chỉnh kết nối Cổng thanh toán Sandbox bên ngoài (Payment Gateway Port & Adapter) thay thế mô phỏng nội bộ:
1. Thiết kế `PaymentGatewayPort` và Adapter tích hợp Cổng thanh toán Sandbox (ví dụ VNPAY / SePay / Mock Sandbox Gateway):
   - Tạo URL thanh toán checkout kèm chữ ký số HMAC-SHA512 checksum.
   - Bảng cơ sở dữ liệu `ticketing.payment_attempts`, `ticketing.payment_notifications`, `ticketing.refund_attempts`.
2. Xử lý IPN Webhook Server-to-Server Callback:
   - Endpoint `POST /api/payments/callback` tiếp nhận IPN an toàn, xác minh chữ ký HMAC-SHA512.
   - Cơ chế Idempotent xử lý callback trùng lặp hoặc đến muộn.
   - Chuyển tiếp trạng thái booking: `CONFIRMED` khi thanh toán thành công, `PAYMENT_FAILED` khi giao dịch thất bại.
3. Xử lý Trọng tài Thanh toán Muộn (Late Payment Arbitration):
   - Khi thanh toán thành công nhưng booking đã bị `EXPIRED` (hết hạn giữ vé), hệ thống chuyển đơn hàng sang `REFUND_REQUIRED` và tự động ghi nhận nghĩa vụ hoàn tiền `payment_compensations`.
4. Quy trình Hoàn tiền (Refund Orchestration):
   - Cho phép Quản trị viên (ADMIN) hoặc hệ thống kích hoạt yêu cầu hoàn tiền cho các giao dịch đủ điều kiện.

## 2. QUY TẮC BẮT BUỘC TRƯỚC KHI BẮT ĐẦU
1. Kiểm tra trạng thái Git:
   - Chạy lệnh: `git status --short --branch`
   - Đọc các file hướng dẫn: `AGENTS.md`, `docs/RULES.md`, `docs/HANDOFF.md`, `docs/TASKS.md`, `docs/PROGRESS.md`.
2. Tuyệt đối KHÔNG commit hoặc push Git; giữ nguyên working tree để Tech Lead kiểm tra.
3. Chạy toàn bộ test suite để đảm bảo hệ thống luôn ở trạng thái 100% PASS.
