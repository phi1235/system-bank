ALTER TABLE notification_deliveries
  ADD COLUMN IF NOT EXISTS attachment_filename VARCHAR(255),
  ADD COLUMN IF NOT EXISTS attachment_content BYTEA,
  ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(100),
  ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

ALTER TABLE notification_deliveries
  DROP CONSTRAINT IF EXISTS ck_notification_delivery_status;

ALTER TABLE notification_deliveries
  ADD CONSTRAINT ck_notification_delivery_status
  CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'DEAD'));

ALTER TABLE notification_deliveries
  ADD CONSTRAINT ck_notification_delivery_attachment_pair
  CHECK (
    (attachment_filename IS NULL AND attachment_content IS NULL)
    OR (attachment_filename IS NOT NULL AND attachment_content IS NOT NULL)
  );
