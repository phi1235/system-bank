ALTER TABLE accounts ADD COLUMN booked_balance NUMERIC(19,2);
ALTER TABLE accounts ADD COLUMN available_balance NUMERIC(19,2);

UPDATE accounts
SET booked_balance = balance,
    available_balance = balance
WHERE booked_balance IS NULL OR available_balance IS NULL;

ALTER TABLE accounts ALTER COLUMN booked_balance SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN available_balance SET NOT NULL;

ALTER TABLE accounts
  ADD CONSTRAINT ck_account_available_not_above_booked
  CHECK (available_balance <= booked_balance);

CREATE OR REPLACE FUNCTION sync_account_balance_projection()
RETURNS TRIGGER AS $$
DECLARE
  active_holds NUMERIC(19,2);
BEGIN
  SELECT COALESCE(SUM(amount), 0)
  INTO active_holds
  FROM account_holds
  WHERE account_id = NEW.id
    AND status = 'ACTIVE'
    AND expires_at > NOW();

  NEW.booked_balance := NEW.balance;
  NEW.available_balance := NEW.balance - active_holds;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_balance_projection
BEFORE INSERT OR UPDATE OF balance ON accounts
FOR EACH ROW EXECUTE FUNCTION sync_account_balance_projection();

CREATE OR REPLACE FUNCTION refresh_available_balance_after_hold()
RETURNS TRIGGER AS $$
DECLARE
  target_account_id UUID;
  active_holds NUMERIC(19,2);
BEGIN
  target_account_id := COALESCE(NEW.account_id, OLD.account_id);
  SELECT COALESCE(SUM(amount), 0)
  INTO active_holds
  FROM account_holds
  WHERE account_id = target_account_id
    AND status = 'ACTIVE'
    AND expires_at > NOW();

  UPDATE accounts
  SET available_balance = booked_balance - active_holds
  WHERE id = target_account_id;
  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hold_available_balance
AFTER INSERT OR UPDATE OF status, expires_at OR DELETE ON account_holds
FOR EACH ROW EXECUTE FUNCTION refresh_available_balance_after_hold();

ALTER TABLE ledger_journals ADD COLUMN sequence_no INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX uq_ledger_transaction_outcome
  ON ledger_journals (transaction_id, journal_type, sequence_no)
  WHERE transaction_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ledger_single_reversal
  ON ledger_journals (reversal_of_journal_id)
  WHERE reversal_of_journal_id IS NOT NULL;

CREATE TABLE account_hold_commands (
  command_id VARCHAR(160) PRIMARY KEY,
  hold_id UUID NOT NULL REFERENCES account_holds(id),
  command_type VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_hold_command_type CHECK (command_type IN ('CREATE', 'CAPTURE', 'RELEASE', 'EXPIRE'))
);

DROP TRIGGER trg_validate_posted_journal ON ledger_journals;

CREATE OR REPLACE FUNCTION validate_posted_journal_balance()
RETURNS TRIGGER AS $$
DECLARE
  debit_total NUMERIC(19,2);
  credit_total NUMERIC(19,2);
  posting_count INTEGER;
BEGIN
  IF NEW.status = 'POSTED' THEN
    SELECT
      COALESCE(SUM(amount) FILTER (WHERE side = 'DEBIT'), 0),
      COALESCE(SUM(amount) FILTER (WHERE side = 'CREDIT'), 0),
      COUNT(*)
    INTO debit_total, credit_total, posting_count
    FROM ledger_postings
    WHERE journal_id = NEW.id;

    IF posting_count < 2 OR debit_total <> credit_total THEN
      RAISE EXCEPTION 'unbalanced journal %, debit %, credit %, postings %',
        NEW.id, debit_total, credit_total, posting_count;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_validate_posted_journal
AFTER INSERT OR UPDATE OF status ON ledger_journals
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_posted_journal_balance();
