-- Rollback Migration U37: Drop narrative_json column from forensic_cases

ALTER TABLE forensic_cases
  DROP COLUMN IF EXISTS narrative_json;
