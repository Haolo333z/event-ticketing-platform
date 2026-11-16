-- V13: Cổng thanh toán bên ngoài (Payment Gateway Attempts, IPN Notifications, Refund Attempts)

CREATE TABLE ticketing.payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES ticketing.bookings(id) ON DELETE RESTRICT,
    provider VARCHAR(32) NOT NULL,
    merchant_ref VARCHAR(64) NOT NULL UNIQUE,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL DEFAULT 'INITIATED' CHECK(status IN ('INITIATED', 'REDIRECTED', 'SUCCESS', 'FAILED', 'EXPIRED', 'REFUNDED')),
    gateway_url TEXT,
    provider_tx_ref VARCHAR(128),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_payment_attempts_booking ON ticketing.payment_attempts(booking_id);
CREATE INDEX idx_payment_attempts_merchant_ref ON ticketing.payment_attempts(merchant_ref);

CREATE TABLE ticketing.payment_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID REFERENCES ticketing.payment_attempts(id) ON DELETE SET NULL,
    provider VARCHAR(32) NOT NULL,
    provider_tx_ref VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL CHECK(event_type IN ('PAYMENT_SUCCESS', 'PAYMENT_FAILURE', 'REFUND_NOTIFY')),
    raw_payload JSONB NOT NULL,
    signature TEXT NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_payment_ipn_tx UNIQUE (provider, provider_tx_ref, event_type)
);

CREATE INDEX idx_payment_notif_lookup ON ticketing.payment_notifications(provider, provider_tx_ref);

CREATE TABLE ticketing.refund_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES ticketing.bookings(id) ON DELETE RESTRICT,
    payment_attempt_id UUID REFERENCES ticketing.payment_attempts(id) ON DELETE SET NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    reason VARCHAR(255) NOT NULL,
    provider_refund_ref VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING', 'APPROVED', 'PROCESSING', 'SUCCESS', 'MANUAL_REVIEW')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    settled_at TIMESTAMPTZ
);

CREATE INDEX idx_refund_attempts_booking ON ticketing.refund_attempts(booking_id);
