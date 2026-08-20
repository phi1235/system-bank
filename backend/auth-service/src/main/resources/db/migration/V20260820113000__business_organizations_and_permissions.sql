-- V21: Business organizations, members, and B2B/operations RBAC permissions

CREATE TABLE business_organizations (
  id          UUID PRIMARY KEY,
  code        VARCHAR(50) NOT NULL UNIQUE,
  legal_name  VARCHAR(255) NOT NULL,
  tax_number  VARCHAR(50),
  status      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE business_members (
  id              UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES business_organizations(id) ON DELETE CASCADE,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  business_role   VARCHAR(50) NOT NULL,
  status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_business_member UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_business_members_org ON business_members(organization_id);
CREATE INDEX idx_business_members_user ON business_members(user_id);

-- Insert B2B roles into roles catalog
INSERT INTO roles (code, name, description, staff) VALUES
  ('BUSINESS_OWNER',    'Business Owner',    'Full management of business organization and virtual accounts', FALSE),
  ('BUSINESS_FINANCE',  'Business Finance',  'Finance operations, collection orders and settlements', FALSE),
  ('BUSINESS_OPERATOR', 'Business Operator', 'Order creation, VA management and customer tracking', FALSE),
  ('BUSINESS_VIEWER',   'Business Viewer',   'Read-only view for business reports and transactions', FALSE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name, description = EXCLUDED.description;

-- Insert permissions for Business Portal and VA/Settlement Back-Office Operations
INSERT INTO permissions (code, description) VALUES
  ('business:dashboard:view',     'Business portal - view dashboard and stats'),
  ('business:va:view',            'Business portal - view virtual accounts'),
  ('business:va:manage',          'Business portal - create and manage virtual accounts'),
  ('business:orders:view',        'Business portal - view collection orders'),
  ('business:orders:manage',      'Business portal - create and manage collection orders'),
  ('business:settlements:view',   'Business portal - view settlements and split reports'),
  ('business:settlements:execute','Business portal - execute order completions and payouts'),
  ('business:split:view',         'Business portal - view multi-tier split rules'),
  ('business:split:manage',       'Business portal - create and manage multi-tier split rules'),
  ('business:credentials:manage', 'Business portal - manage API keys and webhook endpoints'),
  ('va:operations:view',          'Back office - view virtual account operations and inbound webhooks'),
  ('va:operations:review',        'Back office - review and match anomalous inbound payments'),
  ('settlement:view',             'Back office - view settlement runs and payout legs'),
  ('settlement:retry',            'Back office - retry failed settlements and payouts'),
  ('settlement:approve',          'Back office - approve high-value settlements and payouts'),
  ('payout:view',                 'Back office - view external payouts and saga states'),
  ('payout:approve',              'Back office - maker-checker approve high-value payout transactions')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

-- Map permissions for BUSINESS_OWNER (All business:* permissions)
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'BUSINESS_OWNER', p.code
FROM permissions p
WHERE p.code LIKE 'business:%'
ON CONFLICT DO NOTHING;

-- Map permissions for BUSINESS_FINANCE
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('BUSINESS_FINANCE', 'business:dashboard:view'),
  ('BUSINESS_FINANCE', 'business:va:view'),
  ('BUSINESS_FINANCE', 'business:orders:view'),
  ('BUSINESS_FINANCE', 'business:settlements:view'),
  ('BUSINESS_FINANCE', 'business:settlements:execute'),
  ('BUSINESS_FINANCE', 'business:split:view'),
  ('BUSINESS_FINANCE', 'business:split:manage')
ON CONFLICT DO NOTHING;

-- Map permissions for BUSINESS_OPERATOR
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('BUSINESS_OPERATOR', 'business:dashboard:view'),
  ('BUSINESS_OPERATOR', 'business:va:view'),
  ('BUSINESS_OPERATOR', 'business:va:manage'),
  ('BUSINESS_OPERATOR', 'business:orders:view'),
  ('BUSINESS_OPERATOR', 'business:orders:manage'),
  ('BUSINESS_OPERATOR', 'business:split:view')
ON CONFLICT DO NOTHING;

-- Map permissions for BUSINESS_VIEWER
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('BUSINESS_VIEWER', 'business:dashboard:view'),
  ('BUSINESS_VIEWER', 'business:va:view'),
  ('BUSINESS_VIEWER', 'business:orders:view'),
  ('BUSINESS_VIEWER', 'business:settlements:view'),
  ('BUSINESS_VIEWER', 'business:split:view')
ON CONFLICT DO NOTHING;

-- Back-office roles assignment: SUPER_ADMIN and ADMIN get all permissions
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN')
  AND p.code IN (
    'va:operations:view', 'va:operations:review',
    'settlement:view', 'settlement:retry', 'settlement:approve',
    'payout:view', 'payout:approve'
  )
ON CONFLICT DO NOTHING;

-- Back-office OPS_ADMIN assignment
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'OPS_ADMIN', p.code
FROM permissions p
WHERE p.code IN (
  'va:operations:view', 'va:operations:review',
  'settlement:view', 'settlement:retry',
  'payout:view'
)
ON CONFLICT DO NOTHING;

-- Back-office COMPLIANCE / AUDITOR assignment
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('COMPLIANCE', 'AUDITOR')
  AND p.code IN ('va:operations:view', 'settlement:view', 'payout:view')
ON CONFLICT DO NOTHING;

-- Seed default demo business organization
INSERT INTO business_organizations (id, code, legal_name, tax_number, status, created_at, updated_at)
VALUES (
  'a0000000-0000-0000-0000-000000000001',
  'TECHMART_VN',
  'TechMart Vietnam Joint Stock Company',
  '0109876543',
  'ACTIVE',
  NOW(),
  NOW()
) ON CONFLICT (code) DO NOTHING;
