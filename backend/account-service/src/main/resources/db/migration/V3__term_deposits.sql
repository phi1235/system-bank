-- Online term deposits (so tiet kiem). Money moves only via ledger entries on the source
-- payment account: open = DEBIT ref DEP-{id}; close/maturity = CREDIT ref DEP-{id}-close|-mature.
-- Same-service money movement → local transaction (saga is for cross-service transfers only).

CREATE TABLE deposit_products (
  code VARCHAR(20) PRIMARY KEY,
  tenor_months INT NOT NULL CHECK (tenor_months > 0),
  rate_bps INT NOT NULL CHECK (rate_bps >= 0),
  -- Early-settlement (demand) rate applied when closing before maturity.
  early_rate_bps INT NOT NULL CHECK (early_rate_bps >= 0),
  min_amount NUMERIC(19,2) NOT NULL CHECK (min_amount > 0),
  active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO deposit_products (code, tenor_months, rate_bps, early_rate_bps, min_amount) VALUES
  ('TD1M',   1, 300, 50, 1000000),
  ('TD3M',   3, 380, 50, 1000000),
  ('TD6M',   6, 460, 50, 1000000),
  ('TD12M', 12, 530, 50, 1000000);

CREATE TABLE term_deposits (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  source_account_id UUID NOT NULL REFERENCES accounts(id),
  product_code VARCHAR(20) NOT NULL REFERENCES deposit_products(code),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  -- Rate snapshots: product rates may change later; the contract keeps its own.
  rate_bps INT NOT NULL,
  early_rate_bps INT NOT NULL,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  maturity_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,              -- OPEN | MATURED | CLOSED_EARLY
  accrued_interest NUMERIC(19,2) NOT NULL DEFAULT 0,
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_term_deposits_user ON term_deposits(user_id);
CREATE INDEX idx_term_deposits_status_maturity ON term_deposits(status, maturity_date);
