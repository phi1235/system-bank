-- Existing outbox history remains untouched (NULL values do not conflict in PostgreSQL).
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(180);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_dedupe_key
  ON outbox_events(dedupe_key)
  WHERE dedupe_key IS NOT NULL;
