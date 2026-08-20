ALTER TABLE accounts
  ADD COLUMN IF NOT EXISTS creation_command_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_creation_command_id
  ON accounts (creation_command_id)
  WHERE creation_command_id IS NOT NULL;
