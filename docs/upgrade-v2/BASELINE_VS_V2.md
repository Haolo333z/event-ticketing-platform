# BASELINE V0.4 VS TARGET V2 — Đối chiếu chức năng

> Căn cứ: bộ `docs.zip` nhận ngày 2026-11-16. Các trạng thái dưới đây được đọc từ tài liệu/API contract/evidence trong ZIP, chưa phải xác minh trực tiếp source code mới nhất.

## 1. Năng lực baseline được ghi nhận

| Module | Baseline được tài liệu ghi nhận | Bằng chứng tài liệu | Kết luận khi lập V2 |
|---|---|---|---|
| Stack | Java 25, Spring Boot 4.1.2, React 19, TypeScript 7, Vite 8, PostgreSQL 17.11, Kafka 4.2.2, Docker Compose | ADR0001, ARCHITECTURE, HANDOFF | Giữ stack; audit source ở T11.1 |
| Database | 18 bảng, Flyway V1–V9 | DATA_MODEL, HANDOFF, PROGRESS | Giữ migration cũ; V2 dùng V10+ |
| Catalog public | GET events, detail, availability | OpenAPI 18 operations, API_CONTRACT | Nâng UX/search/filter; kiểm tra public fixture segregation |
| Booking | create/get/list mine/cancel | OpenAPI, PRD, DATA_MODEL | Giữ booking engine; đổi identity principal sang account thật |
| Vé | GET tickets, ticket rows phát khi CONFIRMED | API_CONTRACT, DATA_MODEL, ADR0006 | Nâng ticket wallet/QR/PDF/check-in |
| GA/SEATED | Inventory, locking, all-or-nothing, hold, idempotency | PRD, ARCHITECTURE, DATA_MODEL, TEST_PLAN/evidence | Bất biến phải bảo toàn |
| Payment | `simulate-payment` SUCCESS/FAILURE/DELAY_TIMEOUT local/test | PRD, API_CONTRACT, ADR0006 | Bổ sung gateway provider; simulator chỉ giữ cho test/fault injection |
| Identity | Mock bearer token seeded CUSTOMER/ADMIN trong local/test | PRD §3, ARCH §3, ADR0008 | Chưa đủ cho public signup/login/social; thay bằng account/auth thật |
| Roles | CUSTOMER/ADMIN demo | DATA_MODEL users.demo_role, ARCH §3 | Mở rộng bốn role và scope-based RBAC |
| Admin | Event create/list/patch, booking list, inventory, outbox/recovery | OpenAPI, ADR0008, DESIGN | Mở rộng quản trị users/roles/organizers/venues/payments/refunds/reports |
| Organizer | Không có role/route được mô tả trong baseline API | OpenAPI endpoint list | Bổ sung organizer profile, ownership, scoped dashboard |
| Staff/check-in | Không có role/check-in route được mô tả | OpenAPI endpoint list, ADR0006 nói QR production/check-in ngoài MVP | Bổ sung event assignment, QR validation, atomic check-in |
| Google login | Không có trong baseline | PRD/ARCH mock auth | Bổ sung Google OIDC |
| Real gateway | Nằm ngoài MVP | PRD §3, ADR0006, HANDOFF | Bổ sung one-provider sandbox integration |
| Refund | Payment compensation simulated/PENDING model; no real provider refund | DATA_MODEL, ADR0006 | Bổ sung provider refund attempts/reconciliation |
| Notifications | Không có API notification được liệt kê | OpenAPI endpoint list | P1 |
| Reports | Actuator metrics/recovery metrics có; dashboard báo cáo thương mại chưa đủ | ARCH §11, HANDOFF | Bổ sung SQL-backed admin/organizer reports |
| Visual QA | DOM/API tests được ghi; Chrome visual QA còn thiếu | DESIGN, PROGRESS | Visual QA desktop/mobile bắt buộc V2 |

## 2. API baseline 18 operations được liệt kê trong OpenAPI

**Public/customer**
- `GET /api/events`
- `GET /api/events/{id}`
- `GET /api/events/{id}/availability`
- `POST /api/bookings`
- `GET /api/bookings/{id}`
- `GET /api/me/bookings`
- `POST /api/bookings/{id}/cancel`
- `POST /api/bookings/{id}/simulate-payment`
- `GET /api/bookings/{id}/tickets`

**Admin**
- `POST /api/admin/events`
- `GET /api/admin/events`
- `PATCH /api/admin/events/{id}`
- `GET /api/admin/bookings`
- `GET /api/admin/operations/outbox`
- `POST /api/admin/operations/events/{eventId}/replay`
- `GET /api/admin/events/{id}/inventory`
- `GET /api/admin/operations/recovery`
- `GET /api/admin/operations/events/{id}`

Tổng: 18 operations theo `docs/contracts/openapi.json` trong ZIP.

## 3. Các nhóm mới thực sự thuộc V2

1. Real account registration/password login/logout/refresh/revocation.
2. Google OIDC sign-in + explicit account linking.
3. CUSTOMER/STAFF/ORGANIZER/ADMIN role grants and event scopes.
4. Organizer ownership, event staff assignments, venue/category/media management.
5. Staff ticket scan/check-in.
6. External gateway attempt, signed callback/IPN, reconciliation and refund.
7. Customer profile, order/ticket wallet and notification history enhancements.
8. Admin user/role management, gateway/refund dashboards, SQL reports and audit.
9. Visual QA and complete browser journeys for all four roles.

## 4. Current baseline completion must not be rewritten

PROGRESS records T1.1–T7.4, T8.1–T8.4, T9.1–T9.4, T10.1–T10.3 Done (44/45 tasks) and T10.4 Todo in the provided snapshot. T10.4 remains separate baseline closure work. V2 tasks start at T11 after source re-baseline. Do not convert old DONE tasks to Todo and do not report V2 as completed because baseline tests passed.
