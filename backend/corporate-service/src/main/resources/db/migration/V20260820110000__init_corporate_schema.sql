-- V1: Corporate Multi-Tier Approval Matrix & Batch Payout Schema

CREATE TABLE IF NOT EXISTS corporations (
    id UUID PRIMARY KEY,
    tax_id VARCHAR(50) NOT NULL UNIQUE,
    company_name VARCHAR(255) NOT NULL,
    short_name VARCHAR(100),
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    contact_email VARCHAR(160),
    contact_phone VARCHAR(50),
    address VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_corporations_tax_id ON corporations(tax_id);
CREATE INDEX idx_corporations_status ON corporations(status);

CREATE TABLE IF NOT EXISTS corporate_memberships (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL REFERENCES corporations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_corporate_user UNIQUE (corporate_id, user_id)
);
CREATE INDEX idx_corporate_memberships_user ON corporate_memberships(user_id, status);
CREATE INDEX idx_corporate_memberships_corp ON corporate_memberships(corporate_id, status);

CREATE TABLE IF NOT EXISTS corporate_member_roles (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES corporate_memberships(id) ON DELETE CASCADE,
    role_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_membership_role UNIQUE (membership_id, role_name)
);
CREATE INDEX idx_member_roles_membership ON corporate_member_roles(membership_id);

CREATE TABLE IF NOT EXISTS corporate_accounts (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL REFERENCES corporations(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    account_name VARCHAR(160),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    daily_payout_limit NUMERIC(19,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_corporate_account UNIQUE (corporate_id, account_id)
);
CREATE INDEX idx_corporate_accounts_corp ON corporate_accounts(corporate_id, status);

CREATE TABLE IF NOT EXISTS approval_policies (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL REFERENCES corporations(id) ON DELETE CASCADE,
    policy_name VARCHAR(160) NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    allow_self_approval BOOLEAN NOT NULL DEFAULT FALSE,
    require_role_separation BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_corporate_policy_version UNIQUE (corporate_id, version_number)
);
CREATE INDEX idx_approval_policies_corp_status ON approval_policies(corporate_id, status);

CREATE TABLE IF NOT EXISTS approval_tiers (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES approval_policies(id) ON DELETE CASCADE,
    tier_name VARCHAR(100) NOT NULL,
    min_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    max_amount NUMERIC(19,2),
    priority_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_tiers_policy ON approval_tiers(policy_id, priority_order);

CREATE TABLE IF NOT EXISTS approval_step_templates (
    id UUID PRIMARY KEY,
    tier_id UUID NOT NULL REFERENCES approval_tiers(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    required_role VARCHAR(50) NOT NULL,
    min_approvals INT NOT NULL DEFAULT 1,
    auth_method VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    deadline_hours INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_step_templates_tier ON approval_step_templates(tier_id, step_order);

CREATE TABLE IF NOT EXISTS payout_batches (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL REFERENCES corporations(id),
    source_account_id UUID NOT NULL,
    source_account_number VARCHAR(20) NOT NULL,
    batch_name VARCHAR(200) NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    valid_items INT NOT NULL DEFAULT 0,
    invalid_items INT NOT NULL DEFAULT 0,
    processed_items INT NOT NULL DEFAULT 0,
    successful_items INT NOT NULL DEFAULT 0,
    failed_items INT NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_fee NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    file_sha256 VARCHAR(64) NOT NULL,
    original_file_key VARCHAR(255),
    error_report_file_key VARCHAR(255),
    policy_id UUID REFERENCES approval_policies(id),
    policy_version INT,
    policy_snapshot TEXT,
    canonical_payload_hash VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    hold_id UUID,
    created_by UUID NOT NULL,
    submitted_by UUID,
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_corporate_file_dedupe UNIQUE (corporate_id, source_account_id, file_sha256)
);
CREATE INDEX idx_payout_batches_corp_status ON payout_batches(corporate_id, status);
CREATE INDEX idx_payout_batches_status ON payout_batches(status);

CREATE TABLE IF NOT EXISTS payout_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES payout_batches(id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    employee_code VARCHAR(100),
    beneficiary_name VARCHAR(160) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    bank_code VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    description VARCHAR(255),
    employee_email VARCHAR(160),
    payroll_period VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'IMPORTED',
    validation_error VARCHAR(500),
    transaction_id UUID,
    idempotency_key VARCHAR(100),
    execution_version INT NOT NULL DEFAULT 1,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    claimed_by VARCHAR(100),
    claimed_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    receipt_artifact_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_batch_row UNIQUE (batch_id, row_number),
    CONSTRAINT uq_item_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_payout_items_fetch ON payout_items(batch_id, status, next_retry_at, lease_until);
CREATE INDEX idx_payout_items_batch ON payout_items(batch_id, row_number);

CREATE TABLE IF NOT EXISTS approval_instances (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL UNIQUE REFERENCES payout_batches(id) ON DELETE CASCADE,
    tier_id UUID,
    policy_version INT NOT NULL,
    total_steps INT NOT NULL,
    current_step INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS approval_tasks (
    id UUID PRIMARY KEY,
    instance_id UUID NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES payout_batches(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    required_role VARCHAR(50) NOT NULL,
    min_approvals INT NOT NULL DEFAULT 1,
    current_approvals INT NOT NULL DEFAULT 0,
    auth_method VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    deadline TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_instance_step UNIQUE (instance_id, step_order)
);
CREATE INDEX idx_approval_tasks_inbox ON approval_tasks(required_role, status);
CREATE INDEX idx_approval_tasks_batch ON approval_tasks(batch_id);

CREATE TABLE IF NOT EXISTS approval_actions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES approval_tasks(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES payout_batches(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(30) NOT NULL,
    comments VARCHAR(500),
    challenge_id UUID,
    signature_reference VARCHAR(255),
    action_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    CONSTRAINT uq_task_actor UNIQUE (task_id, actor_id)
);
CREATE INDEX idx_approval_actions_batch ON approval_actions(batch_id);

CREATE TABLE IF NOT EXISTS signing_challenges (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES approval_tasks(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES payout_batches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    challenge_type VARCHAR(30) NOT NULL,
    nonce VARCHAR(64) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_signing_challenges_nonce ON signing_challenges(nonce);

CREATE TABLE IF NOT EXISTS receipt_artifacts (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL REFERENCES corporations(id),
    batch_id UUID NOT NULL REFERENCES payout_batches(id) ON DELETE CASCADE,
    item_id UUID REFERENCES payout_items(id) ON DELETE CASCADE,
    artifact_type VARCHAR(30) NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_receipt_artifacts_batch ON receipt_artifacts(batch_id);
CREATE INDEX idx_receipt_artifacts_item ON receipt_artifacts(item_id);

CREATE TABLE IF NOT EXISTS corporate_audit_logs (
    id UUID PRIMARY KEY,
    corporate_id UUID NOT NULL,
    user_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_corporate_audit_corp ON corporate_audit_logs(corporate_id, created_at DESC);

CREATE TABLE IF NOT EXISTS corporate_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_by VARCHAR(100),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ck_corporate_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'PROCESSED', 'FAILED', 'DEAD_LETTER'))
);
CREATE INDEX idx_corporate_outbox_fetch ON corporate_outbox_events(status, next_attempt_at, lease_until);
