# UPGRADE REQUIREMENTS V2 — Functional, Data, API, Security & Acceptance

> Tài liệu yêu cầu triển khai. Các API/schema dưới đây là yêu cầu/đề xuất, chưa phải hợp đồng đã triển khai. AI phải audit source code và tạo ADR/OpenAPI/migration phù hợp trước khi code.

## 1. Baseline đã đọc từ tài liệu gốc

Theo PRD v0.4, DATA_MODEL, API_CONTRACT/OpenAPI, ADR0001–0008, TASKS/PROGRESS/HANDOFF trong ZIP:

- Stack: Java 25 / Spring Boot 4.1.2, React 19 / TypeScript 7 / Vite 8, PostgreSQL 17.11, Kafka 4.2.2, Docker Compose; không Redis.
- Backend là modular monolith; API và worker chung backend.
- Data model ghi 18 bảng nghiệp vụ qua V1–V9.
- OpenAPI baseline có 18 operations.
- Identity hiện ghi `users(id, demo_role, display_name)` và `CUSTOMER/ADMIN`, mock bearer token chỉ local/test.
- Payment hiện mô phỏng; `payments` ledger ghi `scenario`, `request_id`, `recorded_at`, `processed_at`, không phải provider gateway ledger.
- Booking/ticket/inventory/concurrency/outbox/inbox/recovery là phần baseline cần bảo vệ.
- T7.2 UI customer và T7.3 dashboard admin được ghi Done theo DOM/API tests, nhưng DESIGN/PROGRESS nêu chưa có visual QA đầy đủ trên Chrome.
- T10.3 report Done; T10.4 trong TASKS còn Todo tại snapshot.

**Không có source code trong ZIP này.** Mọi gap implementation phải xác minh tại repository trong T11.1.

## 2. Functional requirements

### 2.1 Authentication & account

| ID | Requirement | Acceptance |
|---|---|---|
| AUTH-001 | Đăng ký email/password | Email normalize, unique case-insensitive; validate password; hash mạnh; tạo user + CUSTOMER trong 1 transaction |
| AUTH-002 | Đăng nhập email/password | Generic failure message; không tiết lộ email tồn tại; rate limit/cooldown |
| AUTH-003 | Đăng xuất | Revoke refresh/session; lần refresh tiếp theo bị từ chối |
| AUTH-004 | Refresh session/token | Rotation, reuse detection, revoke token family khi refresh token cũ bị tái sử dụng |
| AUTH-005 | Google OIDC login | Backend authorization-code flow, state/nonce, validate signature/issuer/audience/expiry/nonce/email_verified |
| AUTH-006 | Tạo account Google lần đầu | Tạo local user CUSTOMER và external identity dựa trên stable provider subject (`sub`) |
| AUTH-007 | Link Google | Chỉ khi user đã authenticated, re-auth gần đây, xác minh identity; không tự link chỉ vì email trùng |
| AUTH-008 | Unlink Google | Chặn bỏ phương thức đăng nhập cuối cùng nếu user chưa có password/identity khác |
| AUTH-009 | Hồ sơ | Read/update profile owner-only; admin có quyền theo policy |
| AUTH-010 | Đổi mật khẩu | Yêu cầu mật khẩu hiện tại/re-auth; invalidate/revoke sessions theo policy |
| AUTH-011 | Quên/đặt lại mật khẩu | Token ngẫu nhiên, hash trong DB, one-time, thời hạn ngắn, generic response |
| AUTH-012 | Email verification | Token one-time; trạng thái verified; Google email_verified xử lý theo OIDC claim đã kiểm chứng |
| AUTH-013 | Account state | ACTIVE/LOCKED/DISABLED; disabled không login/refresh |
| AUTH-014 | Bootstrap admin | Không public registration; bootstrap secret một lần/seed an toàn, audit và rotate |
| AUTH-015 | Test-only mock identity | Chỉ profile test/local có guard; không được bật cùng production-like auth |

### 2.2 Four-role RBAC

| ID | Requirement | Acceptance |
|---|---|---|
| RBAC-001 | CUSTOMER default | Mọi email/Google registration mới được gán CUSTOMER; client không tự chọn role |
| RBAC-002 | STAFF role | Chỉ admin cấp; quyền check-in/read data cho event được assign |
| RBAC-003 | ORGANIZER role | Chỉ admin cấp; quyền quản lý event thuộc organizer/assignment |
| RBAC-004 | ADMIN role | Chỉ bootstrap/authorized admin; global scope |
| RBAC-005 | Multiple role grants | Một account có thể có nhiều role; role không phụ thuộc Google provider |
| RBAC-006 | Event scope | Organizer chỉ access owned/assigned events; Staff chỉ access assigned events |
| RBAC-007 | Server-side enforcement | Mọi admin/organizer/staff command kiểm tra role + scope trong service/API; frontend hiding không phải security |
| RBAC-008 | Role audit | Mọi grant/revoke, assignment, privilege change lưu actor/target/scope/reason/time |
| RBAC-009 | Privilege escalation protection | Không nhận role, organizerId, ownerId, event scope từ client như nguồn tin cậy |
| RBAC-010 | Cross-tenant denial | Organizer A không thể đọc/sửa event, attendee, booking, reports của Organizer B |

### 2.3 Customer catalog

| ID | Requirement | Acceptance |
|---|---|---|
| CAT-001 | Public catalog | Chỉ published/visible event |
| CAT-002 | Event detail | Title, description, date/time, venue, organizer public name, poster, ticket types, sale window |
| CAT-003 | Search | Server-side keyword search, input validation |
| CAT-004 | Filter | Category/date/location/price/status khi dữ liệu tồn tại |
| CAT-005 | Sort/pagination | Server-side stable ordering, bounded page size |
| CAT-006 | Availability | Đọc snapshot; chỉ booking transaction mới quyết định reserve |
| CAT-007 | Internal fixture segregation | Test fixture không xuất hiện public; không lọc substring/tên |
| CAT-008 | Event status | DRAFT/PUBLISHED/CLOSED/CANCELLED hoặc mapping tương thích với state hiện tại; phải ADR nếu status hiện có khác |

### 2.4 Organizer event management

| ID | Requirement | Acceptance |
|---|---|---|
| ORG-001 | Organizer dashboard | Chỉ hiển thị event thuộc quyền |
| ORG-002 | Event CRUD | Create/edit/draft/publish/close theo policy và audit |
| ORG-003 | Venue/category | Quản lý hoặc chọn venue/category hợp lệ |
| ORG-004 | Media | Upload allowlist MIME, size limit, server-generated filename, metadata SQL; file bytes ở filesystem/object storage |
| ORG-005 | Ticket types | Tạo/sửa giá, mode, capacity, sale windows theo invariant hiện có |
| ORG-006 | Inventory | Hiện capacity/held/sold/available; không cho sửa xuống dưới held+sold |
| ORG-007 | Reports | Chỉ doanh số/booking của event thuộc quyền; numbers from SQL |
| ORG-008 | Staff assignment | Organizer chỉ assign/remove STAFF trong event thuộc quyền; action audited |
| ORG-009 | Publish gate | Không publish event thiếu trường bắt buộc, thời gian sai hoặc ticket config không hợp lệ |

### 2.5 Booking, tickets and check-in

| ID | Requirement | Acceptance |
|---|---|---|
| BKG-001 | Real customer principal | Booking account_id lấy từ authenticated principal; không dùng demo persona cho public user flow |
| BKG-002 | Preserve GA & SEATED | Existing concurrency/idempotency/all-or-nothing invariants pass unchanged |
| BKG-003 | My bookings | Account chỉ đọc booking thuộc mình; Admin global; Organizer scoped read-only theo policy |
| BKG-004 | Booking timeline | History from DB, không tự suy state trên client |
| TKT-001 | Ticket issue | Chỉ CONFIRMED; unique ticket code/ordinal giữ nguyên |
| TKT-002 | QR ticket | QR chứa opaque signed/random reference; không nhúng PII/payment data; backend xác thực khi scan |
| TKT-003 | Ticket wallet/PDF | User tải vé thuộc account của mình; status rõ; ticket lookup không leak |
| CHK-001 | Staff assignment | STAFF chỉ scan/check-in event được assign |
| CHK-002 | Ticket verification | Validate ticket exists, event matches, booking confirmed, ticket not checked-in/cancelled |
| CHK-003 | Atomic check-in | Unique check-in per ticket; race duplicate scan yields one success and deterministic already-used response |
| CHK-004 | Audit | Store ticket, event, staff actor, timestamp, outcome, request/correlation ID; minimize device/PII |
| CHK-005 | Correction | Undo/correct check-in only by authorized ADMIN with reason/audit; no staff self-undo |

### 2.6 External payment gateway

| ID | Requirement | Acceptance |
|---|---|---|
| PAY-001 | Payment provider port | Domain service depends on internal port; provider-specific code is adapter |
| PAY-002 | Create attempt | Backend creates attempt from booking snapshot; amount/currency/order ref never accepted as trusted client values |
| PAY-003 | Checkout redirect | Server creates provider URL/request and signs server-side; secrets never leave backend |
| PAY-004 | Sandbox first | Integration works against provider test/sandbox credentials; no real money during dev/demo |
| PAY-005 | Return endpoint | Return URL only shows “đang xác minh/đã nhận kết quả”; never confirms booking solely from browser redirect |
| PAY-006 | IPN/webhook | Verify provider signature/checksum first; validate merchant, order reference, amount, currency, status and mapping to payment attempt |
| PAY-007 | Callback idempotency | Unique provider event/transaction reference; same callback no duplicate payment, ticket, inventory or outbox effect |
| PAY-008 | Out-of-order callbacks | Deterministic transition policy; terminal booking states cannot be reversed by late/duplicate callback |
| PAY-009 | Reconciliation | Query provider for ambiguous/unknown results; reconcile with persisted local attempt |
| PAY-010 | Late payment | Once booking expiry/release is committed, late success must not reclaim inventory; create refund obligation |
| PAY-011 | Refund | Gateway refund operation is idempotent, audited, tied to original payment and provider reference |
| PAY-012 | Admin payment view | Show local attempt/refund state and sanitized provider metadata; never show secrets/card data |
| PAY-013 | Simulator retained | Simulator only test/local fault adapter; do not mix simulator scenarios with provider statuses |
| PAY-014 | Amount checks | Compare exact server amount, VND currency, provider TxnRef/OrderId, merchant account and transaction status |
| PAY-015 | Callback reachability | Sandbox callback endpoint publicly reachable over provider-required HTTPS during end-to-end test; local tunnel only for development and never store tunnel secrets |

**Payment state arbitration:** Preserve current booking state machine unless an explicit ADR proves an additive change is needed. `PENDING_PAYMENT → CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED`; only `EXPIRED → REFUND_REQUIRED`. Never re-confirm after an expiry transaction has committed and inventory may have been reallocated. A verified gateway success in that case creates one refund obligation; it does not create tickets.

### 2.7 Admin and platform management

| ID | Requirement | Acceptance |
|---|---|---|
| ADM-001 | User search/list/detail | Paginated, filterable, PII-minimized |
| ADM-002 | Role management | Grant/revoke customer/staff/organizer/admin according to protected workflow; every change audited |
| ADM-003 | Invitations | Invite organizer/staff with one-time token/expiry; cannot elevate to ADMIN |
| ADM-004 | Event/venue/category management | Global scope and safe constraints |
| ADM-005 | Booking/payment/refund management | View state/history; admin action only by explicit commands with audit |
| ADM-006 | Gateway config status | Show configured/not configured, provider, mode; never render secret values |
| ADM-007 | Dashboard | Aggregates from SQL, clearly define paid/revenue metrics and timezone |
| ADM-008 | Reports export | CSV/XLSX export with scope checks and safe CSV escaping |
| ADM-009 | Audit log | Searchable immutable append-only logical record for privileged operations |
| ADM-010 | Recovery operations | Preserve existing outbox replay limits, audit and inbox dedup rules |

### 2.8 Notifications/support (P1)

- In-app event-driven notifications for registration, booking, payment, ticket issuance, upcoming event and event changes.
- Email for verification, reset password, booking/payment/ticket receipts; local MailHog/Mailpit equivalent is allowed for dev.
- Notification read state persisted in SQL.
- FAQ/contact/support ticket and admin processing; do not add a chatbot unless separately approved.

## 3. Proposed additive data model

Do not directly apply these tables without reviewing current schema and naming conventions. Keep V1–V9 immutable; use Flyway V10+ only.

| Table / change | Purpose | Key constraints |
|---|---|---|
| `users` additive columns | normalized email, password_hash nullable for Google-first users, status, verified_at, auth_version | unique normalized email; no plaintext password |
| `roles` | canonical role codes | unique code: CUSTOMER/STAFF/ORGANIZER/ADMIN |
| `user_roles` | multiple role grants | PK(user_id, role_id), granted_by, granted_at, revoked_at, reason |
| `external_identities` | Google identity link | unique(provider, provider_subject); user FK; verified email metadata; never key by email alone |
| `refresh_tokens` or sessions | first-party session refresh/revocation | store hash only, expiry, family, replaced/revoked timestamps |
| `password_reset_tokens` | one-time password reset | store hash, expiry, consumed_at |
| `email_verification_tokens` | one-time verification | store hash, expiry, consumed_at |
| `organizer_profiles` | organizer business/public profile | unique slug; owner user FK |
| `event_staff_assignments` | staff scoped to events | unique(event_id,user_id), active range, assigned_by |
| `events` additive owner/venue/category fields | organizer ownership and discovery | owner FK; public visibility/status constraints |
| `venues` / `event_categories` | normalized catalog | FK and indexes; only if current schema has no equivalent |
| `event_media` | poster/gallery metadata | stored key/path, MIME, bytes, checksum; binary stored outside SQL |
| `payment_attempts` | gateway attempts | unique(provider, merchant_reference), booking FK, amount/currency snapshot, provider status |
| `payment_notifications` | verified callback receipts | unique(provider, provider_event/reference), body hash, receive/process status |
| `refund_attempts` | gateway refund operations | unique merchant refund reference, original attempt FK, status, amount |
| `ticket_check_ins` | staff check-in | unique(ticket_id), event_id, staff_user_id, checked_in_at |
| `notifications` | in-app notification | recipient FK, type, payload-safe data, read_at |
| `audit_logs` | privileged action history | actor, action, target, scope, reason, occurred_at, correlation_id |
| `support_tickets` (P1) | support workflow | requester, status, category, created/updated |

Do not add all proposed tables at once. Phase tasks create only the tables required by the current feature and migration gate.

## 4. Proposed API surface (not yet approved OpenAPI)

Existing baseline operations must be retained where compatible. New proposed routes:

**Authentication**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/change-password`
- OAuth redirect/callback through Spring Security OAuth2 Login (for example `/oauth2/authorization/google` and `/login/oauth2/code/google`), exact callback must match Google Console config.
- `POST /api/auth/link/google`, `DELETE /api/auth/link/google` if explicit linking is enabled.

**Organizer**
- `GET/POST /api/organizer/events`
- `GET/PATCH /api/organizer/events/{eventId}`
- publish/close commands on event resource or explicitly named action routes
- `GET /api/organizer/events/{eventId}/bookings`
- `GET /api/organizer/events/{eventId}/reports`
- `POST/DELETE /api/organizer/events/{eventId}/staff/{userId}`

**Staff**
- `GET /api/staff/events`
- `GET /api/staff/events/{eventId}/attendees`
- `POST /api/staff/events/{eventId}/check-ins`
- `GET /api/staff/events/{eventId}/check-ins`

**Admin**
- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- role/invitation commands under `/api/admin/users/{userId}/roles` or invitation resources
- admin-wide payment/refund/report endpoints as approved in OpenAPI.

**Payment**
- `POST /api/bookings/{bookingId}/payment-attempts` → server returns provider checkout URL and local attempt ID.
- Provider-specific return and IPN/callback endpoints (method/payload per selected provider).
- `GET /api/payment-attempts/{attemptId}`.
- `POST /api/admin/payment-attempts/{attemptId}/refunds` or equivalent authorized refund command.

Exact routes, schemas, HTTP codes, idempotency rules and error codes must be agreed in updated OpenAPI before implementation. Do not blindly add this list as controllers.

## 5. Security requirements

- Password hash via reviewed password encoder; never reversible encryption/plaintext.
- Google OIDC: Authorization Code server-side flow, state and nonce validation, issuer/audience/signature/expiry checks; request only `openid email profile` unless further consent is justified.
- Google identity key is provider + stable `sub`; email is profile/verified attribute, not the immutable provider subject.
- Do not automatically merge two accounts based solely on equal email. Explicit link flow requires authenticated local account and recent re-auth.
- Google ID/access token is not the EventFlow API token. Issue/maintain first-party principal and session/token policy after validating identity.
- Recommended SPA session approach must be finalized in ADR: short-lived first-party access token kept in memory plus rotating opaque refresh token in Secure/HttpOnly/SameSite cookie with CSRF protection; alternatively backend session cookie if architecture review proves simpler. Never store long-lived secrets/tokens in localStorage.
- Exact CORS origins only; if credentialed cookies are used, explicitly enable credentials and CSRF protection; no wildcard origin.
- Rate limit/lockout for login, registration, password reset, OAuth initiation and callback abuse.
- Authorization checks in service/API layer; UI route guards are convenience only.
- Secrets through environment/secret manager; `.env` ignored; no credentials in logs/evidence/Git.
- Callback signature/checksum validation before trusting payment result; compare provider merchant, order reference, amount, currency and status with SQL attempt.
- Never trust browser return page to confirm payment.
- Never store card number, CVV, OTP or unnecessary provider tokens.
- Staff attendee views must minimize PII; audit exports and admin actions.

## 6. Non-functional requirements

- ACID transactions for account, booking, inventory, payment state, ticket and check-in transitions.
- Keep GA `held + sold <= capacity`; a seat can have at most one active owner; multi-seat reservation atomic.
- Payment and callbacks idempotent under duplicate/out-of-order delivery.
- No duplicate ticket issuance, refund or check-in under retries/concurrency.
- All list APIs paginated with bounded page size and deterministic sort.
- Validate input server-side; consistent error envelope and correlation ID.
- UI loading, empty, error, forbidden, expired, pending, success states.
- Accessibility: keyboard, focus, labels, contrast, screen-reader descriptions for seat/check-in states.
- Responsive desktop/tablet/mobile; no required function inaccessible on mobile where role needs it (especially staff check-in).
- Observability must not leak tokens, payment secrets, raw signed callback or excessive PII.
- Backup/restore and migration runbook before any non-demo deployment.

## 7. Test & acceptance matrix

**Identity:** duplicate email, wrong password, disabled account, password hash, reset token one-time/expiry, Google state/nonce/audience/signature/issuer/expiry failures, email conflict, explicit linking, refresh rotation/reuse, logout revoke.

**RBAC:** 401 unauthenticated; 403 wrong role; cross-organizer read/write denied; staff assigned/unassigned event; admin global; cannot self-assign higher role; all sensitive role changes audited.

**Catalog:** draft hidden; published visible; fixture hidden; organizer ownership; invalid date/sale windows; price/capacity safe update; image upload content/size validation.

**Booking:** existing GA/SEATED suites remain; customer principal maps to SQL account; booking cannot be read by another customer; no oversell; all-or-nothing multi-seat; retry/idempotency; expiry.

**Gateway:** valid/invalid signature, amount mismatch, currency mismatch, wrong merchant/order ref, duplicate callback, out-of-order callback, browser return before IPN, IPN after browser closed, provider timeout, ambiguous result query, late success after expiry, refund duplicate/retry/failure, callback processing crash/restart.

**Tickets/check-in:** no ticket before CONFIRMED; QR cannot reveal PII; wrong-event scan; expired/cancelled ticket; assigned staff success; unassigned staff 403; simultaneous duplicate scan creates one check-in.

**Admin/reporting:** SQL-backed counts; timezone/date filters; export scope; CSV injection-safe values; audit log append-only behavior.

**Regression:** existing backend/frontend/contract/concurrency/Kafka/recovery tests stay green or changed expectations are explained in ADR and evidence. Re-run the suites affected by changed code; do not claim old evidence proves new code.

## 8. Definition of Done per feature

A feature is DONE only when all applicable layers exist and are verified:
1. UI/route with loading/error/empty/forbidden states.
2. API endpoint and OpenAPI contract.
3. Backend domain/service and authorization.
4. SQL migration/index/constraint and repository integration.
5. Unit + integration + security tests.
6. End-to-end test using real PostgreSQL (and provider sandbox where relevant).
7. Evidence and PROGRESS update.
8. Visual QA screenshot for UI features.

A visual mock, disabled button, seeded fixture or localStorage-only flow is not a completed feature.
