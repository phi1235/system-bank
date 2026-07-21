-- Customer inbox fields on notification delivery log.
-- Existing rows keep user_id NULL (not listed in customer inbox until backfilled).

ALTER TABLE notification_logs
  ADD COLUMN IF NOT EXISTS user_id UUID,
  ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notification_user_created
  ON notification_logs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_user_unread
  ON notification_logs (user_id, created_at DESC)
  WHERE user_id IS NOT NULL AND read_at IS NULL;
