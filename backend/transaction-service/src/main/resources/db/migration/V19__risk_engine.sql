ALTER TABLE transfer_orders
  ADD COLUMN IF NOT EXISTS risk_decision VARCHAR(20),
  ADD COLUMN IF NOT EXISTS risk_score INTEGER,
  ADD COLUMN IF NOT EXISTS risk_reason VARCHAR(500);

CREATE TABLE risk_rules (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  rule_type VARCHAR(30) NOT NULL,
  action VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  priority INTEGER NOT NULL DEFAULT 100,
  threshold_amount NUMERIC(19,2),
  window_seconds BIGINT,
  max_count BIGINT,
  max_total_amount NUMERIC(19,2),
  description VARCHAR(255),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_rule_enabled_priority ON risk_rules(enabled, priority);

CREATE TABLE risk_blacklist (
  id UUID PRIMARY KEY,
  subject_type VARCHAR(30) NOT NULL,
  subject_value VARCHAR(160) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  expires_at TIMESTAMPTZ,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_risk_blacklist_subject UNIQUE(subject_type, subject_value)
);

CREATE INDEX idx_risk_blacklist_active ON risk_blacklist(active, subject_type, subject_value);

CREATE TABLE risk_assessments (
  id UUID PRIMARY KEY,
  transfer_id UUID NOT NULL UNIQUE REFERENCES transfer_orders(id),
  user_id UUID NOT NULL,
  decision VARCHAR(20) NOT NULL,
  score INTEGER NOT NULL,
  matched_rules TEXT,
  reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO risk_rules (
  id, code, rule_type, action, priority, threshold_amount, description)
VALUES (
  '8a4a4c5a-9da7-4c51-a66e-a38f30b21d11', 'HIGH_VALUE_REVIEW', 'AMOUNT', 'REVIEW', 100,
  40000000, 'Review high-value transfers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO risk_rules (
  id, code, rule_type, action, priority, window_seconds, max_count, description)
VALUES (
  '8a4a4c5a-9da7-4c51-a66e-a38f30b21d12', 'VELOCITY_COUNT_REVIEW', 'VELOCITY_COUNT',
  'REVIEW', 200, 600, 5, 'Review rapid repeated transfers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO risk_rules (
  id, code, rule_type, action, priority, window_seconds, max_total_amount, description)
VALUES (
  '8a4a4c5a-9da7-4c51-a66e-a38f30b21d13', 'VELOCITY_TOTAL_REVIEW', 'VELOCITY_TOTAL',
  'REVIEW', 300, 86400, 100000000, 'Review excessive rolling transfer total')
ON CONFLICT (code) DO NOTHING;
