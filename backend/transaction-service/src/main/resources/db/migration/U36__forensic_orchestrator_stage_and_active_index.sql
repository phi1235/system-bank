-- Rollback Migration U36: Drop investigation_stage and active transaction unique index

DROP INDEX IF EXISTS uq_forensic_case_active_transaction;

ALTER TABLE forensic_cases
  DROP CONSTRAINT IF EXISTS ck_forensic_case_investigation_stage;

ALTER TABLE forensic_cases
  DROP COLUMN IF EXISTS investigation_stage;
