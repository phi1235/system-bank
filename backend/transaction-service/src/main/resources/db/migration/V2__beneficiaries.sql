CREATE TABLE beneficiaries (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  nickname VARCHAR(80) NOT NULL,
  account_number VARCHAR(20) NOT NULL,
  account_id UUID,
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_beneficiary_user_account UNIQUE (user_id, account_number)
);

CREATE INDEX idx_beneficiary_user ON beneficiaries(user_id);
CREATE INDEX idx_beneficiary_user_active ON beneficiaries(user_id, active);
