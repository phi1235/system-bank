-- Add remediation tracking to forensic cases.
-- A case with CONFIRMED_ISSUE cannot be approved until remediation_status = 'COMPLETED'.
ALTER TABLE forensic_cases ADD COLUMN remediation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE forensic_cases ADD COLUMN remediation_json TEXT;
ALTER TABLE forensic_cases ADD COLUMN systemic BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE forensic_cases ADD COLUMN investigation_cycle INT NOT NULL DEFAULT 1;

ALTER TABLE forensic_cases ADD CONSTRAINT ck_forensic_case_remediation_status
  CHECK (remediation_status IN ('NOT_REQUIRED', 'PENDING', 'IN_PROGRESS', 'COMPLETED'));
