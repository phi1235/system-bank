-- V7: Add performance partial index for term_deposits OPEN status reporting

CREATE INDEX IF NOT EXISTS idx_term_deposits_open_stats
  ON term_deposits (product_code, maturity_date, amount, accrued_interest)
  WHERE status = 'OPEN';
