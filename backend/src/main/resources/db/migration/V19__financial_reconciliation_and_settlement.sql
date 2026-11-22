-- Flyway V19: Doi soat tai chinh tu dong & Circuit Breaker cho cong thanh toan
-- (Financial Reconciliation, Settlement Audits & Gateway Resilience)

CREATE TABLE IF NOT EXISTS ticketing.reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_date DATE NOT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'VNPAY',
    total_transactions INTEGER NOT NULL DEFAULT 0 CHECK (total_transactions >= 0),
    matched_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_count >= 0),
    discrepancy_count INTEGER NOT NULL DEFAULT 0 CHECK (discrepancy_count >= 0),
    total_gateway_amount_vnd BIGINT NOT NULL DEFAULT 0 CHECK (total_gateway_amount_vnd >= 0),
    total_system_amount_vnd BIGINT NOT NULL DEFAULT 0 CHECK (total_system_amount_vnd >= 0),
    difference_amount_vnd BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'COMPLETED', 'DISCREPANCY_FOUND', 'FAILED')),
    triggered_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM_CRON',
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at TIMESTAMPTZ,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_runs_date ON ticketing.reconciliation_runs(reconciliation_date DESC);
CREATE INDEX IF NOT EXISTS idx_reconciliation_runs_status ON ticketing.reconciliation_runs(status);

CREATE TABLE IF NOT EXISTS ticketing.reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES ticketing.reconciliation_runs(id) ON DELETE CASCADE,
    merchant_ref VARCHAR(64),
    provider_tx_ref VARCHAR(128),
    booking_id UUID REFERENCES ticketing.bookings(id) ON DELETE SET NULL,
    discrepancy_type VARCHAR(32) NOT NULL CHECK (discrepancy_type IN ('MATCHED', 'AMOUNT_MISMATCH', 'MISSING_IN_GATEWAY', 'MISSING_IN_DB')),
    system_amount_vnd BIGINT NOT NULL DEFAULT 0,
    gateway_amount_vnd BIGINT NOT NULL DEFAULT 0,
    difference_vnd BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'IGNORED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_reconciliation_disc_run ON ticketing.reconciliation_discrepancies(run_id);
CREATE INDEX IF NOT EXISTS idx_reconciliation_disc_type ON ticketing.reconciliation_discrepancies(discrepancy_type);
CREATE INDEX IF NOT EXISTS idx_reconciliation_disc_status ON ticketing.reconciliation_discrepancies(status);
