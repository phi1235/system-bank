-- V20: Corporate account ownership and batch hold support

ALTER TABLE accounts
  ADD COLUMN owner_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
  ADD COLUMN owner_id UUID;

UPDATE accounts
SET owner_id = user_id
WHERE owner_id IS NULL;

ALTER TABLE accounts
  ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX idx_accounts_owner ON accounts(owner_type, owner_id);

ALTER TABLE account_holds
  ADD COLUMN original_amount NUMERIC(19,2),
  ADD COLUMN captured_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN released_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN batch_id UUID;

UPDATE account_holds
SET original_amount = amount
WHERE original_amount IS NULL;
