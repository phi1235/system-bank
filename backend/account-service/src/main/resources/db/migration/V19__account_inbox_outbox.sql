-- Flyway migration script V19: Account Inbox & Outbox Events for account-service

CREATE TABLE IF NOT EXISTS account_inbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_outbox_events (
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
    CONSTRAINT ck_account_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'PROCESSED', 'FAILED', 'DEAD_LETTER'))
);

CREATE INDEX idx_account_outbox_fetch ON account_outbox_events(status, next_attempt_at, lease_until);
