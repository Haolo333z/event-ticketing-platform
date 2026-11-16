# ADR-0010 — Email/password accounts plus Google OpenID Connect login

- **Status:** Accepted for V2 scope; token/session mechanism must be finalized after source review in T11.2.
- **Date:** 2026-11-16.
- **Context:** Owner selected “social” from the prior option explicitly described as email + password + Google login. Baseline has only mock bearer tokens for seeded CUSTOMER/ADMIN personas.

## Decision

V2 supports both:

1. Native EventFlow email/password registration and login.
2. Google Sign-In using OpenID Connect (OIDC), with the backend acting as OAuth2/OIDC client.

Google identity is used to authenticate the person; EventFlow remains the source of application account, roles and permissions.

## Google login flow requirements

- Use Authorization Code flow handled by backend/Spring Security OAuth2 Client; do not implement a browser-only implicit flow for the application login.
- Request only `openid`, `email`, `profile` scopes unless another scope has a documented business need.
- Validate `state`, `nonce`, token signature, issuer, audience, expiry and required verified-email claims.
- Use Google stable subject (`sub`) with provider name as identity key. Email is mutable profile/verified attribute and must not be used as the only provider identity key.
- Store external provider identity in `external_identities(provider, provider_subject, user_id, email_snapshot, email_verified, created_at, last_login_at)` with unique `(provider, provider_subject)`.
- First Google sign-in creates a local CUSTOMER account unless an explicit safe account-link flow exists.
- Do not silently merge an existing email/password account solely because Google returns the same email. Require user to authenticate to the existing account and explicitly link Google, or use a verified recovery flow.
- Google ID token and access token are never accepted as EventFlow API access tokens. After identity validation, issue EventFlow's own session/access mechanism and resolve roles from the local database.
- Client ID/secret and redirect URI are environment-specific. Secret never goes to frontend build variables or Git.

## Native password requirements

- Password stored only as strong one-way password hash via Spring Security PasswordEncoder (Argon2id or BCrypt after dependency/runtime review).
- Login errors are generic to avoid account enumeration.
- Rate limit/lockout and reset token one-time/expiry rules.
- Logout/revoke and refresh/session rotation semantics must be implemented and tested.

## Session/token decision gate

T11.2 must inspect current bearer-token API and choose one consistent first-party model:

- Preferred candidate: short-lived EventFlow access JWT kept in memory by SPA + opaque rotating refresh token stored hashed in SQL and delivered in Secure/HttpOnly/SameSite cookie with CSRF protection; OR
- backend-managed session cookie if the modular monolith/Vite proxy arrangement is simpler and safer.

Do not store long-lived bearer/refresh token in localStorage. If cookie credentials are used, update exact CORS origins and CSRF policy; never use wildcard CORS.

## Compatibility

- Existing mock bearer tokens can remain in a strictly guarded local/test profile while integration tests migrate.
- Production-like profiles must fail startup if mock authentication is enabled.
- Existing booking principal abstraction should continue to return a stable local user/account ID and roles regardless of whether authentication came from password or Google.

## Verification

Test invalid state/nonce, wrong issuer/audience, bad signature, expired token, duplicate Google subject, unverified email, email collision, account linking and unlinking, disabled account, refresh rotation/reuse, logout/revocation, role grants and admin/customer separation.
