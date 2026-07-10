-- RBAC catalog (roles × permissions). User assignment stays on users.roles (CSV).

CREATE TABLE roles (
  code        VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  staff       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE permissions (
  code        VARCHAR(60) PRIMARY KEY,
  description VARCHAR(255) NOT NULL
);

CREATE TABLE role_permissions (
  role_code       VARCHAR(40) NOT NULL REFERENCES roles(code) ON DELETE CASCADE,
  permission_code VARCHAR(60) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
  PRIMARY KEY (role_code, permission_code)
);

INSERT INTO roles (code, name, description, staff) VALUES
  ('CUSTOMER',    'Customer',      'Internet Banking end-user', FALSE),
  ('ADMIN',       'Admin (legacy)', 'Full back-office (MVP seed)', TRUE),
  ('SUPER_ADMIN', 'Super Admin',   'Full back-office + RBAC manage', TRUE),
  ('OPS_ADMIN',   'Ops Admin',     'Accounts & transfers operations', TRUE),
  ('KYC_OFFICER', 'KYC Officer',   'Customer KYC decisions', TRUE),
  ('COMPLIANCE',  'Compliance',    'Audit & risk read', TRUE),
  ('SUPPORT',     'Support',       'Customer read-only support', TRUE),
  ('AUDITOR',     'Auditor',       'Audit log read-only', TRUE);

INSERT INTO permissions (code, description) VALUES
  ('dashboard:read',       'View ops dashboard'),
  ('customers:read',       'List / view customers'),
  ('customers:kyc_decide', 'Update customer KYC status'),
  ('accounts:read',        'Lookup accounts'),
  ('accounts:freeze',      'Freeze / unfreeze accounts'),
  ('tx:monitor',           'Monitor system transfers'),
  ('audit:read',           'Read audit logs'),
  ('rbac:manage',          'Manage roles and staff assignment'),
  ('risk:read',            'View risk & compliance module');

-- SUPER_ADMIN + ADMIN: all permissions
INSERT INTO role_permissions (role_code, permission_code)
SELECT r.code, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN');

-- OPS_ADMIN
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('OPS_ADMIN', 'dashboard:read'),
  ('OPS_ADMIN', 'customers:read'),
  ('OPS_ADMIN', 'accounts:read'),
  ('OPS_ADMIN', 'accounts:freeze'),
  ('OPS_ADMIN', 'tx:monitor');

-- KYC_OFFICER
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('KYC_OFFICER', 'dashboard:read'),
  ('KYC_OFFICER', 'customers:read'),
  ('KYC_OFFICER', 'customers:kyc_decide');

-- COMPLIANCE
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('COMPLIANCE', 'dashboard:read'),
  ('COMPLIANCE', 'customers:read'),
  ('COMPLIANCE', 'tx:monitor'),
  ('COMPLIANCE', 'audit:read'),
  ('COMPLIANCE', 'risk:read');

-- SUPPORT
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('SUPPORT', 'dashboard:read'),
  ('SUPPORT', 'customers:read');

-- AUDITOR
INSERT INTO role_permissions (role_code, permission_code) VALUES
  ('AUDITOR', 'dashboard:read'),
  ('AUDITOR', 'audit:read');

-- CUSTOMER: no BO permissions (empty)
