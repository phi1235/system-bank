-- Migration V36: Forensic Orchestrator Investigation Stage and Active Case Unique Partial Index

ALTER TABLE forensic_cases
  ADD COLUMN investigation_stage VARCHAR(40) NOT NULL DEFAULT 'INITIALIZED';

ALTER TABLE forensic_cases
  ADD CONSTRAINT ck_forensic_case_investigation_stage
  CHECK (investigation_stage IN (
    'INITIALIZED',
    'VIOLATION_DETECTED',
    'CAUSAL_GRAPH_ATTACHED',
    'ROOT_CAUSE_CONFIRMED',
    'REPLAY_VERIFIED',
    'INVESTIGATION_CONCLUDED'
  ));

-- Unique partial index to prevent duplicate active cases on the same transaction (DB-level race condition guard)
CREATE UNIQUE INDEX uq_forensic_case_active_transaction
  ON forensic_cases (transaction_id)
  WHERE transaction_id IS NOT NULL
    AND status IN ('OPEN', 'ASSIGNED', 'INVESTIGATING', 'PENDING_CHECKER', 'REOPENED');
