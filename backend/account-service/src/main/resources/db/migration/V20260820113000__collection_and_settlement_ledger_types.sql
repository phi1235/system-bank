CREATE INDEX IF NOT EXISTS idx_ledger_journal_type_created
  ON ledger_journals (journal_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ledger_posting_code_created
  ON ledger_postings (ledger_account_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ledger_journal_cmd
  ON ledger_journals (business_command_id);

ALTER TABLE ledger_journals ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

