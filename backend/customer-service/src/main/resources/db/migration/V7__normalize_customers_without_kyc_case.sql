ALTER TABLE customers
  ALTER COLUMN kyc_status SET DEFAULT 'NOT_STARTED';

UPDATE customers customer
SET kyc_status = 'NOT_STARTED',
    updated_at = NOW()
WHERE customer.kyc_status = 'PENDING'
  AND NOT EXISTS (
    SELECT 1
    FROM kyc_cases kyc_case
    WHERE kyc_case.customer_id = customer.id
      AND kyc_case.is_current = TRUE
  );
