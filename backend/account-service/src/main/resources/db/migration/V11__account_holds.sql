CREATE TABLE account_holds (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES accounts(id),
  transaction_id UUID NOT NULL,
  command_id VARCHAR(160) NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  captured_journal_id UUID REFERENCES ledger_journals(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_account_hold_command UNIQUE (account_id, command_id),
  CONSTRAINT ck_account_hold_amount CHECK (amount > 0),
  CONSTRAINT ck_account_hold_status CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX idx_account_hold_active_expiry
  ON account_holds (expires_at, account_id)
  WHERE status = 'ACTIVE';

CREATE INDEX idx_account_hold_transaction
  ON account_holds (transaction_id, created_at DESC);
