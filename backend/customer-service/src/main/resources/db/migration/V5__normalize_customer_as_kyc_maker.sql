-- The customer submitting the KYC case is the maker; back-office staff is the checker.
-- Normalize active cases created by the previous two-back-office-stage workflow.
UPDATE kyc_cases
SET maker_id = customer_id,
    maker_recommendation = 'SUBMIT',
    maker_note = NULL,
    maker_at = COALESCE(submitted_at, maker_at, updated_at),
    status = CASE WHEN status = 'SUBMITTED' THEN 'PENDING_APPROVAL' ELSE status END,
    version = version + 1
WHERE status IN ('SUBMITTED', 'PENDING_APPROVAL');
