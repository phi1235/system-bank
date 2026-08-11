ALTER TABLE kyc_cases
  ADD COLUMN IF NOT EXISTS is_current BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE kyc_cases
  DROP CONSTRAINT IF EXISTS kyc_cases_customer_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_kyc_current_customer
  ON kyc_cases(customer_id)
  WHERE is_current;

CREATE INDEX IF NOT EXISTS idx_kyc_customer_created
  ON kyc_cases(customer_id, created_at DESC);
