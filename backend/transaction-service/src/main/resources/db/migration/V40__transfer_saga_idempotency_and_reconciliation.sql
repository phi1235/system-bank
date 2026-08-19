-- Flyway migration script V40: Transfer Saga, Idempotency Claims, Outbox Concurrency, and Reconciliation

-- 1. Idempotency Claims (Pure Replay Cache & Concurrency Protection)
CREATE TABLE IF NOT EXISTS idempotency_claims (
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    response_status_code INT,
    response_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_claims PRIMARY KEY (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at 
    ON idempotency_claims(expires_at);

-- 2. Sandbox Top-up Quotas (Anti-Money Creation Gate with Atomic UPSERT)
CREATE TABLE IF NOT EXISTS sandbox_topup_quotas (
    user_id UUID NOT NULL,
    topup_date DATE NOT NULL,
    accumulated_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_sandbox_topup_quotas PRIMARY KEY (user_id, topup_date)
);

-- 3. Manual Review Audit Logs (Compliance Audit Trail for Manual Interventions)
CREATE TABLE IF NOT EXISTS manual_review_audit_logs (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    admin_user_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_manual_review_audit_transfer 
    ON manual_review_audit_logs(transfer_id);

-- 4. Extend transfer_orders for Core Banking Saga & Reconciliation
ALTER TABLE transfer_orders
    ADD COLUMN IF NOT EXISTS inquiry_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS bank_bin VARCHAR(16),
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS fee_amount NUMERIC(15, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_debit NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS napas_rrn VARCHAR(64),
    ADD COLUMN IF NOT EXISTS napas_trace_no VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reconciliation_attempts INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_reconciliation_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_transfer_recon_scheduler
    ON transfer_orders(status, next_reconciliation_at)
    WHERE status IN ('UNKNOWN', 'SUBMITTED', 'PROCESSING');

-- 5. Extend outbox_events for Horizontal Scalability, Crash Recovery & Dead-Letter
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS max_attempts INT DEFAULT 10,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_outbox_claim_fetch
    ON outbox_events(status, claim_expires_at, next_attempt_at)
    WHERE status IN ('PENDING', 'PROCESSING');
