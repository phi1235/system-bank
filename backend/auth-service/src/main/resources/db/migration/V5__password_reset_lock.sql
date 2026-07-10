-- Account lock metadata + force password change + reset tickets

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS locked_reason VARCHAR(255);

CREATE TABLE IF NOT EXISTS password_reset_tickets (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id),
  username      VARCHAR(50) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  channel       VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
  status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  requester_note TEXT,
  reject_reason TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  fulfilled_at  TIMESTAMPTZ,
  fulfilled_by  UUID,
  rejected_at   TIMESTAMPTZ,
  rejected_by   UUID
);

CREATE INDEX IF NOT EXISTS idx_pwd_tickets_status ON password_reset_tickets(status);
CREATE INDEX IF NOT EXISTS idx_pwd_tickets_user ON password_reset_tickets(user_id);

-- RBAC permissions for lock / password reset
INSERT INTO permissions (code, description) VALUES
  ('users:lock:execute', 'BO · Lock / unlock user login'),
  ('users:password:reset', 'BO · Fulfill password-reset ticket (blind temp password)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('users:lock:execute', 'users:password:reset')
ON CONFLICT DO NOTHING;
