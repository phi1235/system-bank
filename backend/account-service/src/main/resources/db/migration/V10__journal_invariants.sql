CREATE OR REPLACE FUNCTION validate_posted_journal_balance()
RETURNS TRIGGER AS $$
DECLARE
  debit_total NUMERIC(19,2);
  credit_total NUMERIC(19,2);
  posting_count INTEGER;
BEGIN
  IF NEW.status = 'POSTED' AND OLD.status <> 'POSTED' THEN
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

CREATE TRIGGER trg_validate_posted_journal
BEFORE UPDATE OF status ON ledger_journals
FOR EACH ROW EXECUTE FUNCTION validate_posted_journal_balance();

CREATE OR REPLACE FUNCTION reject_posted_journal_mutation()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.status IN ('POSTED', 'REVERSED') THEN
    RAISE EXCEPTION 'posted journal is immutable; create a reversal journal';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_journal_immutable
BEFORE UPDATE OR DELETE ON ledger_journals
FOR EACH ROW
WHEN (OLD.status IN ('POSTED', 'REVERSED'))
EXECUTE FUNCTION reject_posted_journal_mutation();

CREATE OR REPLACE FUNCTION reject_posting_mutation_for_posted_journal()
RETURNS TRIGGER AS $$
DECLARE
  target_journal UUID;
  journal_status VARCHAR(20);
BEGIN
  target_journal := COALESCE(OLD.journal_id, NEW.journal_id);
  SELECT status INTO journal_status FROM ledger_journals WHERE id = target_journal;
  IF journal_status IN ('POSTED', 'REVERSED') THEN
    RAISE EXCEPTION 'posting of a posted journal is immutable';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_posting_immutable
BEFORE UPDATE OR DELETE ON ledger_postings
FOR EACH ROW EXECUTE FUNCTION reject_posting_mutation_for_posted_journal();
