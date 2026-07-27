-- Virtual debit cards attached to payment accounts. PAN is AES-GCM encrypted at rest
-- (only last4 stored in clear); no CVV is ever stored. One non-closed card per account,
-- enforced by a partial unique index.

CREATE TABLE cards (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES accounts(id),
  user_id UUID NOT NULL,
  pan_encrypted TEXT NOT NULL,
  pan_last4 VARCHAR(4) NOT NULL,
  brand VARCHAR(20) NOT NULL DEFAULT 'NAPAS',
  status VARCHAR(20) NOT NULL,              -- PENDING_ACTIVATION | ACTIVE | LOCKED | CLOSED
  daily_limit NUMERIC(19,2) NOT NULL CHECK (daily_limit >= 0),
  expires_on DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cards_user ON cards(user_id);
CREATE UNIQUE INDEX uq_cards_account_active ON cards(account_id) WHERE status <> 'CLOSED';
