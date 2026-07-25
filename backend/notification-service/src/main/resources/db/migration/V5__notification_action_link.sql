-- Deep-link fields for click-through from in-app notifications to entity screens.
ALTER TABLE notification_logs
  ADD COLUMN IF NOT EXISTS action_type VARCHAR(40),
  ADD COLUMN IF NOT EXISTS action_id VARCHAR(64),
  ADD COLUMN IF NOT EXISTS action_path VARCHAR(300);

COMMENT ON COLUMN notification_logs.action_type IS 'SUPPORT_TICKET | TRANSFER | ACCOUNT | KYC | AUDIT | ...';
COMMENT ON COLUMN notification_logs.action_id IS 'Entity id (UUID string) for deep link';
COMMENT ON COLUMN notification_logs.action_path IS 'In-app path preferred by FE, e.g. /customer/support?ticketId=...';
