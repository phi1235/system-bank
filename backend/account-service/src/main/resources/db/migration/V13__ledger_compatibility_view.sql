CREATE OR REPLACE VIEW ledger_compatibility_entries AS
SELECT id, account_id, entry_type, amount, reference_id, description, created_at
FROM ledger_entries;
