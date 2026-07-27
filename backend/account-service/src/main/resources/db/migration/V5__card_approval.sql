-- Card issuance now goes through back-office approval:
--   customer request → REQUESTED (no PAN yet) → staff approve → PAN generated → PENDING_ACTIVATION
--                                             → staff reject  → REJECTED + mandatory reason
-- The PAN exists only after approval, so REQUESTED rows have NULL pan/expiry.

ALTER TABLE cards ALTER COLUMN pan_encrypted DROP NOT NULL;
ALTER TABLE cards ALTER COLUMN pan_last4 DROP NOT NULL;
ALTER TABLE cards ALTER COLUMN expires_on DROP NOT NULL;

ALTER TABLE cards
  ADD COLUMN approved_by UUID,
  ADD COLUMN approved_at TIMESTAMPTZ,
  ADD COLUMN rejected_by UUID,
  ADD COLUMN rejected_at TIMESTAMPTZ,
  ADD COLUMN reject_reason VARCHAR(255);

-- A REJECTED request must not block a new one; only live lifecycles are exclusive.
DROP INDEX uq_cards_account_active;
CREATE UNIQUE INDEX uq_cards_account_active ON cards(account_id)
  WHERE status NOT IN ('CLOSED', 'REJECTED');
