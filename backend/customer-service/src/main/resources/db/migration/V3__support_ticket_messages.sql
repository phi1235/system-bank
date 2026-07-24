-- Message thread + WAITING_CUSTOMER (request-info / customer reply)
CREATE TABLE IF NOT EXISTS support_ticket_messages (
  id UUID PRIMARY KEY,
  ticket_id UUID NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
  author_user_id UUID NOT NULL,
  author_role VARCHAR(20) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_messages_ticket
  ON support_ticket_messages (ticket_id, created_at ASC);

COMMENT ON TABLE support_ticket_messages IS 'Customer/staff messages on support tickets (request-info thread)';
COMMENT ON COLUMN support_ticket_messages.author_role IS 'CUSTOMER or STAFF';
