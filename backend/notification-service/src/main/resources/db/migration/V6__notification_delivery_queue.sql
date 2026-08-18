CREATE TABLE notification_deliveries (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL,
  channel VARCHAR(10) NOT NULL,
  destination VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  last_error VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  sent_at TIMESTAMPTZ,
  CONSTRAINT uq_notification_delivery_event_channel UNIQUE (event_id, channel),
  CONSTRAINT ck_notification_delivery_channel CHECK (channel IN ('EMAIL', 'SMS')),
  CONSTRAINT ck_notification_delivery_status CHECK (status IN ('PENDING', 'SENT', 'DEAD')),
  CONSTRAINT ck_notification_delivery_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_delivery_due
  ON notification_deliveries (next_attempt_at, created_at)
  WHERE status = 'PENDING';

CREATE INDEX idx_notification_delivery_event
  ON notification_deliveries (event_id);
