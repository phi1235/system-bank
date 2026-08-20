-- Phase 3: Fix available_balance trigger to account for partial captures/releases,
-- and add hold integrity CHECK constraints.
--
-- Problem: The existing triggers compute active holds as SUM(amount), which does not
-- account for partial captures or partial releases. After a debitAgainstHold increases
-- captured_amount, the hold's effective remaining should decrease, but the trigger
-- still subtracts the full original amount from available_balance.
--
-- Fix: Use SUM(GREATEST(original_amount - captured_amount - released_amount, 0))
-- to compute the actual remaining held funds for active, unexpired holds.

-- 1. Replace the accounts balance projection trigger function
CREATE OR REPLACE FUNCTION sync_account_balance_projection()
RETURNS TRIGGER AS $$
DECLARE
  active_holds NUMERIC(19,2);
BEGIN
  SELECT COALESCE(SUM(GREATEST(
      COALESCE(h.original_amount, h.amount) - h.captured_amount - h.released_amount,
      0
  )), 0)
  INTO active_holds
  FROM account_holds h
  WHERE h.account_id = NEW.id
    AND h.status = 'ACTIVE'
    AND h.expires_at > NOW();

  NEW.booked_balance := NEW.balance;
  NEW.available_balance := NEW.balance - active_holds;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Replace the hold change trigger function
CREATE OR REPLACE FUNCTION refresh_available_balance_after_hold()
RETURNS TRIGGER AS $$
DECLARE
  target_account_id UUID;
  active_holds NUMERIC(19,2);
BEGIN
  target_account_id := COALESCE(NEW.account_id, OLD.account_id);

  SELECT COALESCE(SUM(GREATEST(
      COALESCE(h.original_amount, h.amount) - h.captured_amount - h.released_amount,
      0
  )), 0)
  INTO active_holds
  FROM account_holds h
  WHERE h.account_id = target_account_id
    AND h.status = 'ACTIVE'
    AND h.expires_at > NOW();

  UPDATE accounts
  SET available_balance = booked_balance - active_holds
  WHERE id = target_account_id;

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- 3. Extend the hold trigger to also fire on captured_amount and released_amount changes
DROP TRIGGER IF EXISTS trg_hold_available_balance ON account_holds;

CREATE TRIGGER trg_hold_available_balance
AFTER INSERT OR UPDATE OF status, expires_at, captured_amount, released_amount OR DELETE ON account_holds
FOR EACH ROW EXECUTE FUNCTION refresh_available_balance_after_hold();

-- 4. Add CHECK constraints on account_holds for hold amount invariants
ALTER TABLE account_holds
  DROP CONSTRAINT IF EXISTS ck_hold_original_positive;
ALTER TABLE account_holds
  ADD CONSTRAINT ck_hold_original_positive CHECK (COALESCE(original_amount, amount) > 0);

ALTER TABLE account_holds
  DROP CONSTRAINT IF EXISTS ck_hold_captured_non_negative;
ALTER TABLE account_holds
  ADD CONSTRAINT ck_hold_captured_non_negative CHECK (captured_amount >= 0);

ALTER TABLE account_holds
  DROP CONSTRAINT IF EXISTS ck_hold_released_non_negative;
ALTER TABLE account_holds
  ADD CONSTRAINT ck_hold_released_non_negative CHECK (released_amount >= 0);

ALTER TABLE account_holds
  DROP CONSTRAINT IF EXISTS ck_hold_captured_released_within_original;
ALTER TABLE account_holds
  ADD CONSTRAINT ck_hold_captured_released_within_original
  CHECK (captured_amount + released_amount <= COALESCE(original_amount, amount));

-- 5. Add non-negative balance constraint on accounts (safety net)
ALTER TABLE accounts
  DROP CONSTRAINT IF EXISTS ck_account_balance_non_negative;
ALTER TABLE accounts
  ADD CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0);

ALTER TABLE accounts
  DROP CONSTRAINT IF EXISTS ck_account_booked_non_negative;
ALTER TABLE accounts
  ADD CONSTRAINT ck_account_booked_non_negative CHECK (booked_balance >= 0);

ALTER TABLE accounts
  DROP CONSTRAINT IF EXISTS ck_account_available_non_negative;
ALTER TABLE accounts
  ADD CONSTRAINT ck_account_available_non_negative CHECK (available_balance >= 0);

-- Keep command audit extensible for the atomic hold-aware compensation operation.
ALTER TABLE account_hold_commands
  DROP CONSTRAINT IF EXISTS ck_hold_command_type;
ALTER TABLE account_hold_commands
  ADD CONSTRAINT ck_hold_command_type CHECK (command_type IN (
    'CREATE', 'CREATE_BATCH', 'CAPTURE', 'PARTIAL_CAPTURE', 'RELEASE', 'RELEASE_REMAINING',
    'EXPIRE', 'DEBIT_AGAINST_HOLD', 'COMPENSATE_CREDIT_AGAINST_HOLD'
  ));
