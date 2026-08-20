-- V20260820113004: Durable Merchant Webhook Deliveries

CREATE TABLE merchant_webhook_deliveries (
  id                   UUID PRIMARY KEY,
  endpoint_id          UUID NOT NULL REFERENCES merchant_webhook_endpoints(id) ON DELETE CASCADE,
  organization_id      UUID NOT NULL,
  event_id             UUID NOT NULL,
  event_type           VARCHAR(100) NOT NULL,
  payload              TEXT NOT NULL,
  status               VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED, DEAD_LETTER
  response_status_code INT,
  response_body        VARCHAR(1000),
  error_message        VARCHAR(500),
  retry_count          INT NOT NULL DEFAULT 0,
  next_retry_at        TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_merchant_delivery_event_endpoint UNIQUE (endpoint_id, event_id)
);

CREATE INDEX idx_merchant_delivery_org ON merchant_webhook_deliveries(organization_id);
CREATE INDEX idx_merchant_delivery_status_retry ON merchant_webhook_deliveries(status, next_retry_at);
