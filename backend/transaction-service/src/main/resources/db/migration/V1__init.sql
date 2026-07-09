CREATE TABLE transfer_orders (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR(100) NOT NULL,
  user_id UUID NOT NULL,
  from_account_id UUID NOT NULL,
  to_account_id UUID,
  to_account_number VARCHAR(20) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  description VARCHAR(255),
  request_fingerprint VARCHAR(128) NOT NULL,
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(255),
  debit_entry_ref VARCHAR(64),
  credit_entry_ref VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_transfer_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_transfer_user ON transfer_orders(user_id);
CREATE INDEX idx_transfer_status ON transfer_orders(status);
CREATE INDEX idx_transfer_created ON transfer_orders(created_at);

CREATE TABLE saga_step_logs (
  id UUID PRIMARY KEY,
  transfer_id UUID NOT NULL REFERENCES transfer_orders(id),
  step VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_saga_transfer ON saga_step_logs(transfer_id);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(50) NOT NULL,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  payload TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published_at) WHERE published_at IS NULL;

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  actor_user_id UUID,
  action VARCHAR(50) NOT NULL,
  resource_type VARCHAR(50),
  resource_id VARCHAR(64),
  ip VARCHAR(64),
  metadata TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
