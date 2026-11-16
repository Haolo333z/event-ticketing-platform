# PROMPT — Tiếp nhận và triển khai EventFlow Full-stack Product V2

Bạn là Principal Software Architect, Senior Full-stack Engineer, Security Engineer và Tech Lead tiếp quản repository `event-ticketing-platform`.

## Mục tiêu đã chốt bởi chủ dự án

Đọc `docs/upgrade-v2/README.md`, `PRODUCT_SCOPE_V2.md`, `UPGRADE_REQUIREMENTS_V2.md`, `ROADMAP_V2.md` và ba ADR-0009/0010/0011.

Chủ dự án đã chọn:
- Bốn role: CUSTOMER, STAFF, ORGANIZER, ADMIN.
- Authentication: email/password + Google Sign-In (OIDC).
- Thanh toán: tích hợp một external payment gateway, sandbox trước; provider cụ thể chưa chốt, VNPAY là khuyến nghị đầu tiên chứ không được tự coi là đã có credentials.
- PostgreSQL là nguồn dữ liệu nghiệp vụ; giữ stack Java/Spring Boot + React/TypeScript + PostgreSQL + Kafka/EDA hiện có.

## Bước 1 — Re-baseline bắt buộc

Trước mọi sửa đổi:
1. Đọc `AGENTS.md`, `docs/RULES.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/DATA_MODEL.md`, `docs/API_CONTRACT.md`, `docs/TASKS.md`, `docs/PROGRESS.md`, `docs/HANDOFF.md`, các ADR/evidence liên quan và toàn bộ tài liệu V2.
2. Kiểm tra `git status --short --branch`, `git rev-parse HEAD`, `git log -5 --oneline`, `git diff`, `git diff --cached`.
3. Xác minh source code thực tế: backend/frontend, Flyway V1–V9, OpenAPI, auth filter/principal, users schema, mock token, payment simulator, bookings, tickets, frontend routes, tests.
4. Không dựa vào ảnh hoặc số task trong chat nếu repository đang có trạng thái mới hơn.
5. Không chạy test ghi dữ liệu lên volume demo cần bảo toàn; tạo project/DB/port riêng theo runbook.

## Bước 2 — Lập GAP MATRIX

Với từng yêu cầu trong UPGRADE_REQUIREMENTS_V2.md, gắn một trong bốn trạng thái:
- IMPLEMENTED_AND_VERIFIED
- IMPLEMENTED_BUT_NOT_VERIFIED
- PARTIALLY_IMPLEMENTED
- NOT_IMPLEMENTED

Mỗi dòng phải có bằng chứng path/file, API, DB table/migration, test và kết quả thực tế. Tách rõ baseline cũ đã có (GA/SEATED, concurrency, Outbox/Inbox, expiry, recovery, dashboard demo) khỏi V2 mới (real registration, Google OIDC, 4 roles, provider gateway, staff check-in).

## Bước 3 — Chốt thiết kế trước code

- Thiết kế role matrix + event scope; không cho user tự chọn role cao hơn.
- Chốt token/session model dựa trên source hiện tại. Google ID token không được dùng như EventFlow API token.
- Thiết kế additive Flyway V10+; không sửa V1–V9 đã chạy.
- Thiết kế payment gateway adapter, payment attempts, callback dedup, reconciliation và refund; không nhét provider status vào `payments.scenario`.
- Giữ booking state machine và inventory invariants; late success sau EXPIRED committed không được chiếm lại inventory hay issue ticket.
- Cập nhật OpenAPI và data model trước khi controller/service phụ thuộc vào chúng.
- Provider chưa được chủ dự án xác nhận; không viết provider-specific implementation hoặc yêu cầu secrets. Đề xuất VNPAY sandbox và ghi rõ điều kiện credentials/IPN HTTPS.

## Bước 4 — Chỉ thực hiện T11.1 trong lượt này

Tạo/cập nhật tài liệu sau khi audit:
- `docs/upgrade-v2/GAP_MATRIX.md`
- `docs/upgrade-v2/SOURCE_REBASELINE.md`
- Cập nhật link trong README/PRD/TASKS/PROGRESS nếu cần, nhưng không sửa đè lịch sử.

Không triển khai auth, Google, gateway, schema hoặc UI trong T11.1. Kết thúc bằng báo cáo:
1. HEAD/branch/working tree.
2. Các module thực tế trong source.
3. Những gì baseline thực sự có bằng source/test.
4. Gap matrix cho V2.
5. Các quyết định kỹ thuật còn mở.
6. File đã thay đổi.
7. Lệnh/test đã chạy và kết quả thật.
8. Đề xuất task T11.2.

## Ràng buộc bảo vệ

- Không tự commit/push/tag/release.
- Không xóa demo data, fixtures, evidence, inbox, outbox, idempotency keys hoặc Docker volumes.
- Không reset database, không `git clean`, không `git reset --hard`.
- Không thêm .NET, Redis hoặc microservices.
- Không thay đổi state machine, transaction, locking hoặc event schema khi chưa có ADR và regression evidence.
- Không hardcode credentials, OAuth client secret, gateway secret, tunnel token hoặc `.env`.
- Không fake API, giá, trạng thái payment, dashboard numbers hoặc screenshots.
- Không đánh dấu task DONE khi thiếu UI/API/SQL/test/evidence phù hợp.
