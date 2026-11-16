# ADR-0009 — Four-role RBAC and event-scoped authorization

- **Status:** Accepted for V2 scope; implementation design must be verified in T11.1/T11.2.
- **Date:** 2026-11-16.
- **Context:** Baseline v0.4 documents only CUSTOMER/ADMIN demo personas and mock bearer auth. Owner selected four account roles: CUSTOMER, STAFF, ORGANIZER, ADMIN.

## Decision

1. Use canonical role codes `CUSTOMER`, `STAFF`, `ORGANIZER`, `ADMIN`.
2. Support multiple role grants per user, stored relationally (`roles`, `user_roles`) rather than trusting a role sent by browser.
3. New email or Google registration receives CUSTOMER by default.
4. STAFF and ORGANIZER grants require an authorized administrator workflow. ADMIN is bootstrap/administratively granted only; no public self-selection.
5. ORGANIZER permissions are scoped to events owned/assigned to that organizer.
6. STAFF permissions are scoped to explicit event assignments.
7. ADMIN has global platform scope but privileged actions remain audited.
8. All authorization is enforced server-side in API/service layer. Hiding a button or route is not authorization.

## Role capability baseline

| Capability | CUSTOMER | STAFF | ORGANIZER | ADMIN |
|---|---:|---:|---:|---:|
| Browse public catalog | Yes | Yes | Yes | Yes |
| Manage own profile | Yes | Yes | Yes | Yes |
| Create/manage own bookings | Yes | No | Optional customer grant | Optional customer grant |
| View own tickets | Yes | No | Optional customer grant | Optional customer grant |
| Check in tickets | No | Assigned events only | No by default | Global override/audit |
| Create/edit/publish event | No | No | Owned/assigned events | Global |
| Manage event inventory/ticket types | No | No | Owned/assigned events | Global |
| View event bookings/attendees | Own booking only | Assigned event minimal view | Owned event | Global |
| Manage user roles | No | No | No | Yes |
| Manage gateway/refunds globally | No | No | Request/submit scoped request only | Yes |
| Outbox/recovery operations | No | No | No | Yes, audited |

The exact organizer role may be allowed to have CUSTOMER capabilities through multiple role grants. Do not implicitly allow organizer to check in their own attendees unless a separate permission is explicitly designed.

## Data model direction

- Add `roles` with unique role code.
- Add `user_roles` with FK user/role, granted_by, granted_at, revoked_at, reason.
- Add `organizer_profiles` or equivalent organizer ownership relation.
- Add `event_staff_assignments` with unique active assignment per `(event_id,user_id)` and audit metadata.
- Add event owner/organizer relation through a migration only after reviewing the current `events` schema.
- Optionally use `role_permissions` only if role permission customization is required; do not create a generic policy engine prematurely.

## Security invariants

- A customer cannot send `role=ADMIN`, `organizerId`, `ownerId` or event assignment to gain permissions.
- Organizer A cannot read/write Organizer B's events, bookings, attendee data or reports.
- Staff cannot view or check in tickets for an event without active assignment.
- Role grants/revokes and assignments have actor and reason audit.
- All object-level lookups must include authorized scope; do not fetch globally and rely on UI filtering.

## Consequences

- Existing `demo_role` enum and seeded persona tokens need an additive migration/compatibility plan. Keep mock auth only in local/test while migrating tests; never mix mock auth with production-like profiles.
- Every admin/organizer/staff endpoint needs 401/403 and cross-scope tests.
- User may hold more than one role; UI role switcher is presentation only and backend still verifies each request.
