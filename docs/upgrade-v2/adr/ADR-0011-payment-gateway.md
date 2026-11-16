# ADR-0011 — External payment gateway through provider adapter

- **Status:** Accepted for scope; provider vendor selection pending owner confirmation and sandbox credential availability.
- **Date:** 2026-11-16.
- **Context:** Baseline v0.4 explicitly excludes real gateway/refunds and uses a local/test payment simulator. Owner selected gateway integration for V2.

## Decision

- V2 integrates one external payment gateway, end-to-end in its sandbox/test environment first.
- Use an internal `PaymentGatewayPort`/adapter boundary. Booking domain and state machine must not depend on vendor SDK or vendor field names.
- VNPAY is the recommended first provider candidate for a Vietnam-based VND project. It is not considered owner-approved until sandbox account/credentials are confirmed. MoMo is an alternative but supporting two providers simultaneously is outside V2 first release.
- Production/live money remains disabled until merchant approval, production credentials, HTTPS deployment, operational checklist and owner authorization are available.
- Keep current simulator only as test/local fault-injection adapter; do not expose it as the primary customer payment method in production-like flow.

## Payment flow requirements

1. Customer requests payment for an eligible PENDING_PAYMENT booking.
2. Backend creates a local `payment_attempt` from server-owned booking amount/currency; client cannot provide trusted amount or merchant ref.
3. Backend creates provider request/checkout redirect and signs it server-side.
4. Customer completes payment at provider.
5. Browser Return URL displays a pending/verified result view only; the browser redirect does not itself confirm a booking.
6. Provider server callback/IPN is signature-validated and reconciled against merchant ID, local transaction reference, amount, currency, provider transaction status and booking.
7. Callback is persisted idempotently; domain state transition and outbox happen transactionally.
8. Query/reconciliation resolves provider timeout/unknown status.
9. If booking expiry/release committed before verified payment success is processed, never reclaim inventory or issue ticket; create one refund obligation and refund through provider if available.
10. Refund attempts are idempotent, auditable and reconciled with provider response.

## Data direction

Do not overload baseline `payments.scenario` rows as real provider attempts. Add separate payment attempt/provider transaction/callback/refund records via new migration V10+ after schema audit. Store secret-free metadata and hashes; do not store raw card/OTP or unredacted signed callback secrets. Keep a unique provider merchant reference and unique provider notification/transaction identity sufficient for deduplication.

Suggested additive records (names can change after schema review):

- `payment_attempts`: booking, provider, merchant_reference, provider_transaction_no, amount, currency, local/provider status, created/updated/paid timestamps.
- `payment_notifications`: provider, notification/event key, payment attempt, body hash, received/processed timestamp, outcome; unique provider + stable notification key.
- `refund_attempts`: original attempt/payment, unique merchant refund reference, amount, reason, status, requested_by, provider refund reference, timestamps.

Preserve existing booking state transitions unless a new ADR justifies change. Preserve invariant: after EXPIRED committed and inventory released, a late provider success cannot become CONFIRMED and cannot issue ticket; it must enter refund/compensation path.

## Provider-specific acceptance requirements

For VNPAY candidate:
- Use VNPAY sandbox credentials and sandbox checkout URL, not production keys.
- Generate `vnp_SecureHash` on backend; validate hash on return/IPN before trusting payload.
- Validate `vnp_TmnCode`, `vnp_TxnRef`, `vnp_Amount`, response/transaction status and mapping to a local attempt.
- Treat Return URL as user-facing; IPN as server-to-server update. Official FAQ distinguishes the two and notes QueryDR and Refund APIs.
- VNPAY docs state IPN URL needs SSL; local end-to-end test requires a publicly reachable HTTPS callback (staging or controlled tunnel). Never commit tunnel credentials.

If MoMo is selected instead, use its Test environment first, validate the IPN signature and cross-check PartnerCode/OrderId/Amount; response timing and callback contract follow its current documentation.

## Open decision

Before provider-specific implementation, the owner must confirm:
1. Which merchant provider is available (recommended VNPAY, alternative MoMo).
2. Sandbox registration/merchant credentials are available.
3. Public HTTPS callback approach for integration test.

No credentials are requested in chat or committed to repository. Configure through ignored `.env`/environment/secret store.

## Verification

Test valid/invalid signature, amount mismatch, merchant mismatch, wrong order reference, duplicate callback, callback out of order, browser return without IPN, IPN without browser return, provider timeout/query, paid-after-expiry, refund duplicate/failure/retry, DB rollback, Kafka outage and restart recovery.
