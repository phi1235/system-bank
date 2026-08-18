CREATE TABLE ledger_journals (
  id UUID PRIMARY KEY,
  business_command_id VARCHAR(160) NOT NULL UNIQUE,
  business_reference VARCHAR(100) NOT NULL,
  transaction_id UUID,
  journal_type VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  description VARCHAR(255),
  reversal_of_journal_id UUID REFERENCES ledger_journals(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  posted_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_ledger_journal_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED'))
);

CREATE TABLE ledger_postings (
  id UUID PRIMARY KEY,
  journal_id UUID NOT NULL REFERENCES ledger_journals(id),
  account_id UUID REFERENCES accounts(id),
  ledger_account_code VARCHAR(80) NOT NULL,
  side VARCHAR(10) NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_ledger_posting_side CHECK (side IN ('DEBIT', 'CREDIT')),
  CONSTRAINT ck_ledger_posting_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_journal_transaction
  ON ledger_journals (transaction_id, created_at DESC)
  WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_ledger_journal_reference
  ON ledger_journals (business_reference, created_at DESC);

CREATE INDEX idx_ledger_posting_journal ON ledger_postings (journal_id);
CREATE INDEX idx_ledger_posting_account ON ledger_postings (account_id, created_at DESC)
  WHERE account_id IS NOT NULL;
