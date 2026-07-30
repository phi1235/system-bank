-- V6: Add performance indexes for accounts and cards tables

CREATE INDEX IF NOT EXISTS idx_accounts_customer_status ON accounts(user_id, status);
CREATE INDEX IF NOT EXISTS idx_cards_account_id ON cards(account_id);
CREATE INDEX IF NOT EXISTS idx_cards_status ON cards(status);
