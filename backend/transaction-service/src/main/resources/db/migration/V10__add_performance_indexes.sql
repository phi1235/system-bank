-- V10: Add performance indexes for transactions and beneficiaries tables

CREATE INDEX IF NOT EXISTS idx_transactions_sender_created ON transactions(sender_account_no, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_status_created ON transactions(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_beneficiaries_customer_id ON beneficiaries(customer_id);
