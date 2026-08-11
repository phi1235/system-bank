INSERT INTO permissions (code, description) VALUES
  ('risk:manage', 'Risk - manage rules and blacklist'),
  ('risk:decide', 'Risk - approve or reject held transfers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT existing.role_code, target.code
FROM role_permissions existing
JOIN permissions target ON target.code IN ('risk:manage', 'risk:decide')
WHERE existing.permission_code = 'risk:view'
ON CONFLICT DO NOTHING;
