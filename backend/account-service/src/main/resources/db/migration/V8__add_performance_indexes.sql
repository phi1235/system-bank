-- V8: Add performance composite indexes for accounts, cards, and term_deposits
CREATE INDEX IF NOT EXISTS idx_accounts_user_created
  ON accounts (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_accounts_status_created
  ON accounts (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_term_deposits_user_created
  ON term_deposits (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_term_deposits_status_created
  ON term_deposits (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cards_user_created
  ON cards (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cards_status_created
  ON cards (status, created_at DESC);
