-- Ops / staff notification audience (shared BO inbox, not user-scoped)
ALTER TABLE notification_logs
  ADD COLUMN IF NOT EXISTS audience VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

UPDATE notification_logs
SET audience = 'CUSTOMER'
WHERE audience IS NULL OR audience = '';

CREATE INDEX IF NOT EXISTS idx_notification_logs_audience_created
  ON notification_logs (audience, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_logs_audience_unread
  ON notification_logs (audience, created_at DESC)
  WHERE read_at IS NULL AND audience = 'OPS';
