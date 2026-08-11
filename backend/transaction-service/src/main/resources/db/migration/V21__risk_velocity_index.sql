CREATE INDEX IF NOT EXISTS idx_transfer_risk_velocity
  ON transfer_orders(user_id, created_at DESC, status)
  INCLUDE (amount);

CREATE INDEX IF NOT EXISTS idx_risk_assessment_user_created
  ON risk_assessments(user_id, created_at DESC);
