CREATE TABLE kyc_cases (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
  status VARCHAR(30) NOT NULL,
  maker_id UUID,
  maker_recommendation VARCHAR(20),
  maker_note VARCHAR(500),
  maker_at TIMESTAMPTZ,
  checker_id UUID,
  decision VARCHAR(20),
  decision_reason VARCHAR(500),
  submitted_at TIMESTAMPTZ,
  decided_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_case_status_updated ON kyc_cases(status, updated_at);

CREATE TABLE kyc_documents (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES kyc_cases(id),
  customer_id UUID NOT NULL REFERENCES customers(id),
  document_type VARCHAR(40) NOT NULL,
  object_key VARCHAR(300) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
  sha256 VARCHAR(64) NOT NULL,
  scan_status VARCHAR(20) NOT NULL,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_document_case ON kyc_documents(case_id, uploaded_at);
CREATE INDEX idx_kyc_document_customer ON kyc_documents(customer_id, uploaded_at);

CREATE TABLE kyc_decision_history (
  id UUID PRIMARY KEY,
  case_id UUID NOT NULL REFERENCES kyc_cases(id),
  actor_id UUID NOT NULL,
  action VARCHAR(40) NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_history_case_created ON kyc_decision_history(case_id, created_at);
