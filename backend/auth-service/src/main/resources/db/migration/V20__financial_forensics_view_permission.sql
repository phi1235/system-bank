INSERT INTO permissions (code, description) VALUES
  ('forensics:view', 'Financial forensics - view investigations and evidence'),
  ('forensics:verify:execute', 'Financial forensics - execute verification'),
  ('forensics:case:review', 'Financial forensics - manage investigation cases'),
  ('forensics:evidence:export', 'Financial forensics - export restricted evidence'),
  ('forensics:replay:execute', 'Financial forensics - execute sanitized replay'),
  ('forensics:copilot:use', 'Financial forensics - use AI copilot'),
  ('forensics:audit:view', 'Financial forensics - view case audit history'),
  ('forensics:admin', 'Financial forensics - administer and reopen cases')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_code, permission_code)
SELECT role.code, permission.code
FROM roles role
JOIN permissions permission ON permission.code IN (
  'forensics:view', 'forensics:audit:view')
WHERE role.code IN ('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE', 'AUDITOR')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT role.code, permission.code
FROM roles role
JOIN permissions permission ON permission.code IN (
  'forensics:verify:execute', 'forensics:case:review', 'forensics:copilot:use')
WHERE role.code IN ('SUPER_ADMIN', 'ADMIN', 'COMPLIANCE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT role.code, permission.code
FROM roles role
JOIN permissions permission ON permission.code IN (
  'forensics:evidence:export', 'forensics:replay:execute', 'forensics:admin')
WHERE role.code IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
