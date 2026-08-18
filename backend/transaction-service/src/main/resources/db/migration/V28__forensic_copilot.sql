CREATE TABLE forensic_copilot_sessions (
  id UUID PRIMARY KEY,
  transaction_id UUID REFERENCES transfer_orders(id),
  case_id UUID REFERENCES forensic_cases(id),
  created_by UUID NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_copilot_session_scope CHECK (transaction_id IS NOT NULL OR case_id IS NOT NULL),
  CONSTRAINT ck_copilot_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED'))
);

CREATE INDEX idx_copilot_session_actor ON forensic_copilot_sessions (created_by, updated_at DESC);

CREATE TABLE forensic_copilot_messages (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES forensic_copilot_sessions(id),
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  response_status VARCHAR(40),
  tool_calls_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  citations_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  validation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_copilot_message_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_copilot_message_session
  ON forensic_copilot_messages (session_id, created_at ASC);
