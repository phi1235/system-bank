-- Migration to strengthen corporate account ownership and account hold command types
ALTER TABLE accounts 
    ADD COLUMN IF NOT EXISTS owner_type VARCHAR(32) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN IF NOT EXISTS corporate_id UUID;

CREATE INDEX IF NOT EXISTS idx_accounts_owner_corporate ON accounts(owner_type, corporate_id) WHERE corporate_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_accounts_user_owner ON accounts(user_id, owner_type);

-- Update check constraints on account_hold_commands to support all lifecycle command types
ALTER TABLE account_hold_commands 
    DROP CONSTRAINT IF EXISTS ck_hold_command_type;

ALTER TABLE account_hold_commands 
    ADD CONSTRAINT ck_hold_command_type 
    CHECK (command_type IN ('CREATE', 'CREATE_BATCH', 'CAPTURE', 'PARTIAL_CAPTURE', 'RELEASE', 'RELEASE_REMAINING', 'EXPIRE', 'DEBIT_AGAINST_HOLD', 'COMPENSATE_CREDIT_AGAINST_HOLD'));
