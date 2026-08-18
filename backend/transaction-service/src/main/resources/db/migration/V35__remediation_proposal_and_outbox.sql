-- Flyway migration script V35: Remediation Proposals & Outbox Events for transaction-service

DROP TABLE IF EXISTS remediation_proposals CASCADE;
DROP TABLE IF EXISTS remediation_outbox_events CASCADE;
DROP TABLE IF EXISTS remediation_inbox_events CASCADE;

CREATE TABLE remediation_proposals (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    investigation_cycle INT NOT NULL DEFAULT 1,
    source_transaction_id UUID,
    target_account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    reason VARCHAR(2000) NOT NULL,
    proposal_payload_hash VARCHAR(64),
    execution_reference_id VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    proposed_by UUID NOT NULL,
    checker_id UUID,
    submitted_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    rejection_reason VARCHAR(1000),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_proposal_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_proposal_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'EXECUTION_PENDING',
        'EXECUTING', 'POSTED', 'VERIFIED', 'REJECTED',
        'EXECUTION_FAILED', 'VERIFICATION_FAILED', 'CANCELLED'
    ))
);

CREATE INDEX idx_remediation_proposal_case_cycle ON remediation_proposals(case_id, investigation_cycle);
CREATE INDEX idx_remediation_proposal_status ON remediation_proposals(status);

CREATE TABLE remediation_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    claimed_by VARCHAR(100),
    claimed_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'PROCESSED', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX idx_remediation_outbox_fetch ON remediation_outbox_events(status, next_attempt_at, lease_until);

CREATE TABLE remediation_inbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
