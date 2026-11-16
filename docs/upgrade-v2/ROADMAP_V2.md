# ROADMAP V2 — Lộ trình Full-stack Product Completion

> Đây là lộ trình nâng cấp sau baseline v0.4. Không thay đổi lịch sử T1–T10.  
> T10.4 vẫn là task baseline đang mở trong snapshot cũ. V2 bắt đầu bằng T11 sau khi chủ dự án đưa bộ tài liệu này vào repository và AI xác minh working tree.

## 1. Quy tắc quản lý tiến độ

- Không coi tài liệu V2 là code đã triển khai.
- Giữ T1–T10 và evidence cũ nguyên vẹn.
- T11.1 phải kiểm tra HEAD, branch, dirty files, migrations, route, test và runtime trước khi cập nhật gap matrix.
- Task chỉ Done khi source/API/SQL/test/evidence/PROGRESS đều phù hợp.
- Tối đa một task chính đang triển khai; không để AI sửa nhiều module lớn trong một diff.
- Mọi schema change dùng Flyway migration mới V10+; không sửa migration V1–V9 đã chạy.
- Không xóa demo fixtures hoặc volume để làm test xanh.

## 2. Thứ tự ưu tiên sản phẩm

**P0 — Phải có để gọi là Full-stack V2:**
1. Tài khoản email/password và Google Sign-In hoạt động.
2. Bốn role thực cùng server-side RBAC và event scope.
3. Organizer quản lý event của mình; Admin quản lý toàn nền tảng.
4. Customer booking GA/SEATED gắn với account thật; đơn hàng và vé đọc từ SQL.
5. Một gateway sandbox tích hợp end-to-end, callback xác minh và idempotent; late payment có refund obligation.
6. Staff check-in cho event được giao, không check-in trùng.
7. Customer/admin/organizer/staff UI responsive, có visual QA.
8. Regression suite chứng minh invariants cũ không bị phá.

**P1 — Nên có ngay sau P0:** email verification/reset, notifications/email receipt, QR PDF ticket, refund approval cho event cancellation, audit log rộng, dashboard/report CSV, venue/category/media management nâng cao, FAQ/support ticket.

**P2 — Ngoài lần hoàn thành V2 đầu:** đa gateway, production live money, multi-organizer settlement/payout, coupon/loyalty/reviews, waitlist, dynamic pricing, native mobile app, Internet-scale queue.

## 3. Roadmap và task

### Giai đoạn 0 — Re-baseline và chốt thiết kế (T11)

| Task | Nội dung | Done khi |
|---|---|---|
| T11.1 | Audit source hiện tại so với docs v0.4 và V2; xác định API, DB, auth, payment, UI thực tế | Hoàn tất: [SOURCE_REBASELINE.md](SOURCE_REBASELINE.md), [GAP_MATRIX.md](GAP_MATRIX.md) |
| T11.2 | Chốt thiết kế kiến trúc V2: Session/token, Google OIDC, 4-role RBAC, payment gateway port, data model V11+, API contract và test strategy | Hoàn tất 7 tài liệu thiết kế ([ARCHITECTURE_V2.md](ARCHITECTURE_V2.md), [DATA_MODEL_V2.md](DATA_MODEL_V2.md), [API_CONTRACT_V2.md](API_CONTRACT_V2.md), [SECURITY_MODEL_V2.md](SECURITY_MODEL_V2.md), [PAYMENT_GATEWAY_DECISION.md](PAYMENT_GATEWAY_DECISION.md), [TEST_STRATEGY_V2.md](TEST_STRATEGY_V2.md), [T11.2_DECISIONS.md](T11.2_DECISIONS.md)); chờ Owner phê duyệt |
| T11.3 | Thiết kế migration V10+ và backward compatibility với fixtures/mock profile | Migration plan, rollback/backup, seed migration tests; chưa chạy trên demo data |
| T11.4 | Chốt provider gateway đầu tiên và xác nhận credential/sandbox/callback reachability | Provider selected, sandbox merchant configured outside Git; return/IPN URL plan |

**Gate G0:** Không bắt đầu code AUTH/PAYMENT nếu API, session strategy, role scope, provider và callback semantics chưa được thiết kế.

### Giai đoạn 1 — Identity và Authentication (T12)

| Task | Nội dung | Done khi |
|---|---|---|
| T12.1 | Additive user/account schema, normalized unique email, user status, role tables, external identities, refresh/session store | Flyway V10+ chạy DB sạch và DB nâng cấp; unique/FK/check/index đạt |
| T12.2 | Email/password registration, login, logout, refresh, profile, password change | SQL persistence, password hashing, rotation/revoke, negative tests |
| T12.3 | Google OIDC login | Backend authorization code flow; state/nonce/issuer/audience/signature/expiry verified; test sandbox account |
| T12.4 | Account linking/unlinking + reset/verification | Explicit link, no silent merge, one-time token, expiry/revoke tests |
| T12.5 | Four-role RBAC enforcement | CUSTOMER/STAFF/ORGANIZER/ADMIN; no self-escalation; API/service tests 401/403/scope |

**Gate G1:** Tài khoản mới qua email và Google đều được lưu cùng một identity model; ADMIN/ORGANIZER/STAFF không thể tự cấp quyền.

### Giai đoạn 2 — Organizer, Staff và Event Catalog (T13)

| Task | Nội dung | Done khi |
|---|---|---|
| T13.1 | Organizer profile và event ownership | Organizer A không đọc/sửa event Organizer B; ownership lưu SQL |
| T13.2 | Event categories, venues, event media metadata | CRUD/validation; migrations; ảnh lưu file/object storage, metadata SQL |
| T13.3 | Organizer event lifecycle | Draft/edit/publish/close/cancel theo policy, public catalog chỉ thấy published |
| T13.4 | Ticket type / GA / seat configuration | API + UI; giữ nguyên capacity/seat invariants và giá snapshot |
| T13.5 | Staff role assignment và event membership | Staff chỉ thấy event assigned; organizer/admin có quyền assign theo scope |

**Gate G2:** Public catalog không lộ fixture/internal event; organizer boundary đã được test.

### Giai đoạn 3 — Customer Commerce Experience (T14)

| Task | Nội dung | Done khi |
|---|---|---|
| T14.1 | Homepage, discovery, search/filter/detail | Responsive pages dùng public APIs thật; loading/error/empty/pagination |
| T14.2 | Booking flow liên kết account thật | Booking `account_id` lấy từ principal, owner isolation, GA/SEATED regression |
| T14.3 | My bookings / ticket wallet / history | Chỉ own data; history/state/price snapshot từ SQL; ticket chỉ sau CONFIRMED |
| T14.4 | QR ticket payload và PDF/print view | QR opaque; backend validation; PDF đúng data; không chứa PII nhạy cảm |

**Gate G3:** Customer tự đăng ký, login, browse, booking và xem lại order/ticket sau restart.

### Giai đoạn 4 — Payment Gateway (T15)

| Task | Nội dung | Done khi |
|---|---|---|
| T15.1 | Provider adapter port + payment attempt schema | Domain không phụ thuộc SDK/provider; attempts unique, amount snapshot, secrets config |
| T15.2 | Sandbox checkout initiation | Backend tạo merchant ref/amount/signature; browser redirect tới sandbox |
| T15.3 | Return + server callback/IPN | Signature, merchant, ref, amount, currency, result verified; callback idempotent |
| T15.4 | Query/reconciliation and booking arbitration | Unknown status được query; duplicate/out-of-order/late payment safe; Kafka outage recovery |
| T15.5 | Refund orchestration | Late success creates unique refund obligation; sandbox refund retry/idempotency/audit |

**Gate G4:** Payment sandbox success leads to CONFIRMED/tickets; failure leaves no sold inventory; late paid after committed expiry never reclaims ticket and creates refund workflow.

### Giai đoạn 5 — Staff Check-in và vận hành sự kiện (T16)

| Task | Nội dung | Done khi |
|---|---|---|
| T16.1 | Staff event dashboard | Staff chỉ xem event được giao |
| T16.2 | QR/manual ticket validation | Check owner/event/confirmed/not used |
| T16.3 | Atomic check-in | Concurrent duplicate scan yields one check-in; duplicate response deterministic |
| T16.4 | Attendee list and audit | PII minimized; audit actor/time/event/ticket; export restricted |

**Gate G5:** Staff không thể scan event không được assign; mỗi ticket check-in tối đa một lần.

### Giai đoạn 6 — Admin, reporting và communication (T17)

| Task | Nội dung | Done khi |
|---|---|---|
| T17.1 | Admin user/role/invitation management | Admin global; role change audit; cannot reveal secrets |
| T17.2 | Admin booking/payment/refund dashboard | Data from SQL/API; no mock revenue; refund commands audited |
| T17.3 | Organizer reports + admin reports | Scoped aggregation, timezone clear, CSV safe |
| T17.4 | Notifications/email receipts | Outbox-driven, idempotent, opt-in preferences, local mail catcher |
| T17.5 | FAQ/support tickets and event content | Customer support flow persists in SQL; owner/admin scope |

**Gate G6:** Các vai trò vận hành được bằng UI thật; metrics/reports có nguồn rõ ràng.

### Giai đoạn 7 — Product UI/UX và Visual QA (T18)

| Task | Nội dung | Done khi |
|---|---|---|
| T18.1 | EventFlow design system | Tokens, typography, controls, navigation, responsive grid |
| T18.2 | Public homepage/discovery/detail | Visual QA Chrome desktop/mobile; không có UUID/fixture/internal labels |
| T18.3 | Auth, booking, checkout, ticket wallet | End-to-end UX; form validation, loading/error/empty/forbidden states |
| T18.4 | Organizer/Staff/Admin dashboards | Role-specific navigation, scoped data, keyboard/mobile support |
| T18.5 | Accessibility and visual regression | Focus, labels, contrast, keyboard, screenshot evidence |

**Gate G7:** 1440×900 desktop, 1366×768 laptop, 390×844 mobile reviewed; core flows operable without developer explanation.

### Giai đoạn 8 — Security, regression và release (T19)

| Task | Nội dung | Done khi |
|---|---|---|
| T19.1 | Full regression | backend/frontend/contract/integration/concurrency/Kafka/recovery suites pass |
| T19.2 | Auth/payment security checks | OAuth attacks, session rotation, callback forgery, role escalation, rate limit tests |
| T19.3 | Migration/backup/restore rehearsal | clean install + upgrade existing V1–V9 DB; restore evidence |
| T19.4 | Staging/demo deployment | HTTPS callbacks reachable, secrets external, health/metrics, rollback documented |
| T19.5 | Final evidence/report/update docs | README/PRD/ARCH/DATA_MODEL/OpenAPI/TASKS/PROGRESS/HANDOFF align with actual source |

**Gate G8:** Release candidate passes all P0 criteria, no critical security issue, same source SHA for evidence/build/release bundle.

## 4. Dependency overview

`T11 re-baseline → T12 identity/RBAC → T13 organizer/catalog/staff scopes → T14 customer journey → T15 gateway → T16 check-in → T17 admin/reporting/notifications → T18 UI visual QA → T19 final QA/release`.

Có thể thiết kế UI design system song song sau T11, nhưng không hoàn thiện giao diện booking trước khi API/auth/data contract của luồng đó ổn định. Staff UI phụ thuộc T13.5 + T14.4 + T16.

## 5. Scope control

- P0 phải hoàn thiện end-to-end; P1 chỉ bắt đầu khi P0 core đã ổn định; P2 không được làm trước khi release gate.
- Mỗi task chia nhỏ thành purpose, preconditions, touched files, API/schema impact, implementation steps, tests, DoD và Git checkpoint.
- Không tự commit/push; chủ dự án xác nhận checkpoint.
- Không chạy migration hoặc payment callback test trên volume demo cần bảo toàn; dùng DB/project/ports riêng.
- Nếu provider sandbox/Google OAuth credentials chưa có, code adapter/config và contract tests có thể làm trước; không tuyên bố end-to-end external integration PASS.

## 6. Điều kiện hoàn thành V2

Toàn bộ P0 đạt; P1 được ghi rõ nếu chưa làm. Một user mới đăng ký/Google sign-in, mua vé qua gateway sandbox, nhận ticket; staff assigned check-in; organizer quản lý event của mình; admin quản trị platform. SQL persist sau restart. Backend bảo toàn no-oversell, idempotency, payment callback/refund consistency, no duplicate ticket/check-in. UI có visual QA thực.
