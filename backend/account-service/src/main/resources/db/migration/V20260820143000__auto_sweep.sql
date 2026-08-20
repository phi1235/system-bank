-- Flexible overnight deposit positions backing customer-configured CASA auto-sweep.
CREATE TABLE sweep_products (
  code VARCHAR(20) PRIMARY KEY,
  currency VARCHAR(3) NOT NULL,
  annual_rate_bps INT NOT NULL CHECK (annual_rate_bps >= 0),
  min_threshold NUMERIC(19,2) NOT NULL CHECK (min_threshold >= 0),
  default_threshold NUMERIC(19,2) NOT NULL CHECK (default_threshold >= min_threshold),
  min_sweep_amount NUMERIC(19,2) NOT NULL CHECK (min_sweep_amount > 0),
  max_position_amount NUMERIC(19,2),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_sweep_product_max CHECK (
    max_position_amount IS NULL OR max_position_amount >= min_sweep_amount
  )
);

INSERT INTO sweep_products (
  code, currency, annual_rate_bps, min_threshold, default_threshold,
  min_sweep_amount, max_position_amount
) VALUES ('FLEX_VND', 'VND', 320, 1000000, 10000000, 100000, NULL);

CREATE TABLE auto_sweep_profiles (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  source_account_id UUID NOT NULL REFERENCES accounts(id),
  product_code VARCHAR(20) NOT NULL REFERENCES sweep_products(code),
  threshold_amount NUMERIC(19,2) NOT NULL CHECK (threshold_amount >= 0),
  min_sweep_amount NUMERIC(19,2) NOT NULL CHECK (min_sweep_amount > 0),
  status VARCHAR(20) NOT NULL,
  last_sweep_business_date DATE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_auto_sweep_source UNIQUE (source_account_id),
  CONSTRAINT ck_auto_sweep_profile_status CHECK (status IN ('ENABLED', 'PAUSED', 'CLOSED'))
);
CREATE INDEX idx_auto_sweep_profile_user ON auto_sweep_profiles(user_id, created_at DESC);
CREATE INDEX idx_auto_sweep_profile_enabled ON auto_sweep_profiles(id)
  WHERE status = 'ENABLED';

CREATE TABLE auto_sweep_positions (
  id UUID PRIMARY KEY,
  profile_id UUID NOT NULL REFERENCES auto_sweep_profiles(id),
  source_account_id UUID NOT NULL REFERENCES accounts(id),
  currency VARCHAR(3) NOT NULL,
  principal_balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (principal_balance >= 0),
  -- Cumulative interest earned for display/audit. Interest is capitalized into principal daily.
  accrued_interest NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (accrued_interest >= 0),
  last_accrual_date DATE NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_auto_sweep_position_profile UNIQUE (profile_id),
  CONSTRAINT uq_auto_sweep_position_source UNIQUE (source_account_id)
);

CREATE TABLE auto_sweep_operations (
  id UUID PRIMARY KEY,
  profile_id UUID NOT NULL REFERENCES auto_sweep_profiles(id),
  position_id UUID NOT NULL REFERENCES auto_sweep_positions(id),
  user_id UUID NOT NULL,
  source_account_id UUID NOT NULL REFERENCES accounts(id),
  operation_type VARCHAR(30) NOT NULL,
  trigger_type VARCHAR(20) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  annual_rate_bps INT,
  business_date DATE NOT NULL,
  command_id VARCHAR(180) NOT NULL,
  payment_reference VARCHAR(128),
  journal_id UUID NOT NULL REFERENCES ledger_journals(id),
  statement_entry_id UUID REFERENCES ledger_entries(id),
  casa_balance_before NUMERIC(19,2) NOT NULL,
  casa_balance_after NUMERIC(19,2) NOT NULL,
  position_balance_before NUMERIC(19,2) NOT NULL,
  position_balance_after NUMERIC(19,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_auto_sweep_operation_command UNIQUE (command_id),
  CONSTRAINT ck_auto_sweep_operation_type CHECK (
    operation_type IN ('SWEEP_IN', 'SWEEP_OUT', 'INTEREST_ACCRUAL')
  ),
  CONSTRAINT ck_auto_sweep_trigger_type CHECK (
    trigger_type IN ('EOD', 'PAYMENT', 'RECOVERY')
  )
);
CREATE INDEX idx_auto_sweep_operation_profile
  ON auto_sweep_operations(profile_id, created_at DESC);
CREATE INDEX idx_auto_sweep_operation_user
  ON auto_sweep_operations(user_id, created_at DESC);
CREATE INDEX idx_auto_sweep_operation_payment
  ON auto_sweep_operations(payment_reference)
  WHERE payment_reference IS NOT NULL;

CREATE TABLE auto_sweep_batch_runs (
  id UUID PRIMARY KEY,
  job_type VARCHAR(30) NOT NULL,
  business_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  worker_id VARCHAR(100) NOT NULL,
  lease_until TIMESTAMPTZ NOT NULL,
  processed_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  CONSTRAINT uq_auto_sweep_batch UNIQUE (job_type, business_date),
  CONSTRAINT ck_auto_sweep_batch_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);
