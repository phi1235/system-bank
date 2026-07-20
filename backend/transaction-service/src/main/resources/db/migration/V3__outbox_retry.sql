-- Outbox reliability: retry scheduling + dead-letter status.
ALTER TABLE outbox_events
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

-- Already-published rows stay published; pending rows are eligible immediately.
UPDATE outbox_events
SET status = 'PUBLISHED'
WHERE published_at IS NOT NULL
  AND status <> 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_outbox_ready
  ON outbox_events (next_attempt_at, created_at)
  WHERE published_at IS NULL AND status = 'PENDING';
