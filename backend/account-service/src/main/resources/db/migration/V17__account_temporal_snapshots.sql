CREATE TABLE account_temporal_snapshots (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES accounts(id),
  snapshot_at TIMESTAMPTZ NOT NULL,
  ledger_balance NUMERIC(19,2) NOT NULL,
  active_hold_amount NUMERIC(19,2) NOT NULL,
  last_entry_at TIMESTAMPTZ,
  schema_version INTEGER NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_account_temporal_snapshot UNIQUE (account_id, snapshot_at),
  CONSTRAINT ck_account_temporal_snapshot_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_account_temporal_snapshot_lookup
  ON account_temporal_snapshots (account_id, snapshot_at DESC);
