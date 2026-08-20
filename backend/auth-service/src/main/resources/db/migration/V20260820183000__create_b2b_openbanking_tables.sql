-- V20260820183000: Open Banking B2B Applications, Account Consents & RBAC Permissions

-- 1. B2B Client Applications (ERP, TPP)
CREATE TABLE IF NOT EXISTS b2b_client_applications (
    id UUID PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    organization_tax_code VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, REVOKED
    allowed_grant_types VARCHAR(128) NOT NULL DEFAULT 'client_credentials',
    allowed_scopes VARCHAR(512) NOT NULL,
    token_endpoint_auth_method VARCHAR(32) NOT NULL DEFAULT 'private_key_jwt', -- private_key_jwt, tls_client_auth
    jwks_uri VARCHAR(1024),
    public_key_pem TEXT,
    client_cert_thumbprint_sha256 VARCHAR(128),
    webhook_callback_url VARCHAR(1024),
    webhook_secret VARCHAR(256),
    rate_limit_rpm INT NOT NULL DEFAULT 120,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_b2b_client_status ON b2b_client_applications(status);
CREATE INDEX IF NOT EXISTS idx_b2b_client_tax ON b2b_client_applications(organization_tax_code);

-- 2. B2B Account Access Consents
CREATE TABLE IF NOT EXISTS b2b_account_consents (
    id UUID PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES b2b_client_applications(client_id) ON DELETE CASCADE,
    account_number VARCHAR(32) NOT NULL,
    customer_id UUID NOT NULL,
    permissions VARCHAR(256) NOT NULL, -- ReadAccountsDetail,ReadBalances,ReadStatements,CreateSinglePayment,CreateBulkPayment
    status VARCHAR(20) NOT NULL DEFAULT 'AUTHORISED', -- AWAITING_AUTH, AUTHORISED, REVOKED, EXPIRED
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_b2b_consents_lookup ON b2b_account_consents(client_id, account_number, status);
CREATE INDEX IF NOT EXISTS idx_b2b_consents_customer ON b2b_account_consents(customer_id);

-- 3. Open Banking RBAC Permissions
INSERT INTO permissions (code, description) VALUES
  ('b2b:openbanking:apps:view',     'Open Banking - View registered B2B client applications'),
  ('b2b:openbanking:apps:manage',   'Open Banking - Register and manage B2B applications & certificates'),
  ('b2b:openbanking:consents:view', 'Open Banking - View account access delegation and consents'),
  ('b2b:openbanking:consents:manage','Open Banking - Grant and revoke account access consents'),
  ('b2b:openbanking:sandbox:use',   'Open Banking - Execute interactive ISO 20022 sandbox requests'),
  ('b2b:openbanking:logs:view',      'Open Banking - View API traffic logs and FAPI audit metrics')
ON CONFLICT (code) DO UPDATE
SET description = EXCLUDED.description;

-- Grant to ADMIN, BUSINESS_OWNER, BUSINESS_FINANCE, BUSINESS_OPERATOR, BUSINESS_VIEWER
INSERT INTO role_permissions (role_code, permission_code)
VALUES
  ('ADMIN', 'b2b:openbanking:apps:view'),
  ('ADMIN', 'b2b:openbanking:apps:manage'),
  ('ADMIN', 'b2b:openbanking:consents:view'),
  ('ADMIN', 'b2b:openbanking:consents:manage'),
  ('ADMIN', 'b2b:openbanking:sandbox:use'),
  ('ADMIN', 'b2b:openbanking:logs:view'),
  ('BUSINESS_OWNER', 'b2b:openbanking:apps:view'),
  ('BUSINESS_OWNER', 'b2b:openbanking:apps:manage'),
  ('BUSINESS_OWNER', 'b2b:openbanking:consents:view'),
  ('BUSINESS_OWNER', 'b2b:openbanking:consents:manage'),
  ('BUSINESS_OWNER', 'b2b:openbanking:sandbox:use'),
  ('BUSINESS_OWNER', 'b2b:openbanking:logs:view'),
  ('BUSINESS_FINANCE', 'b2b:openbanking:apps:view'),
  ('BUSINESS_FINANCE', 'b2b:openbanking:consents:view'),
  ('BUSINESS_FINANCE', 'b2b:openbanking:sandbox:use'),
  ('BUSINESS_FINANCE', 'b2b:openbanking:logs:view'),
  ('BUSINESS_OPERATOR', 'b2b:openbanking:apps:view'),
  ('BUSINESS_OPERATOR', 'b2b:openbanking:consents:view'),
  ('BUSINESS_OPERATOR', 'b2b:openbanking:sandbox:use'),
  ('BUSINESS_VIEWER', 'b2b:openbanking:apps:view'),
  ('BUSINESS_VIEWER', 'b2b:openbanking:consents:view')
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- 4. Seed Demo B2B Client Application (MISA ERP Enterprise & SAP Vietnam)
INSERT INTO b2b_client_applications (
    id,
    client_id,
    client_name,
    organization_tax_code,
    status,
    allowed_grant_types,
    allowed_scopes,
    token_endpoint_auth_method,
    public_key_pem,
    client_cert_thumbprint_sha256,
    webhook_callback_url,
    webhook_secret,
    rate_limit_rpm
) VALUES (
    'a1111111-1111-1111-1111-111111111111',
    'client_misa_erp_prod',
    'MISA SME & AMIS Enterprise ERP',
    '0101243150',
    'ACTIVE',
    'client_credentials',
    'openbanking:accounts:read openbanking:statements:read openbanking:payments:write openbanking:payments:bulk:write openbanking:payments:read',
    'private_key_jwt',
    '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyK+k4u81s0x7r0J4pG9e
s/v9R8v7J1e5T2yK4N0pL9wO7x8Q4o3e7w6r5y9m8p7u3v4x1w2y3z4a5b6c7d8e
9f0g1h2i3j4k5l6m7n8o9p0q1r2s3t4u5v6w7x8y9z0a1b2c3d4e5f6g7h8i9j0k
1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2g3h4i5j6k7l8m9n0o1p2q
3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9h0i1j2k3l4m5n6o7p8q9r0s1t2u3v4w
5x6y7z8a9b0c1d2e3f4g5h6i7j8k9l0m1n2o3p4q5r6s7t8u9v0w1x2y3z4a5b6c
7d8e9f0g1wIDAQAB
-----END PUBLIC KEY-----',
    'a3b8c9e0f1d2c3b4a596877869504132231405162738495a6b7c8d9e0f1a2b3c',
    'https://erp-demo.misa.vn/api/v1/bank-callback/pain002',
    'whsec_misa_sandbox_secret_key_889900',
    300
), (
    'b2222222-2222-2222-2222-222222222222',
    'client_sap_s4hana_hub',
    'SAP S/4HANA Corporate Integration Hub',
    '0309876543',
    'ACTIVE',
    'client_credentials',
    'openbanking:accounts:read openbanking:statements:read openbanking:payments:write openbanking:payments:bulk:write openbanking:payments:read',
    'private_key_jwt',
    '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyK+k4u81s0x7r0J4pG9e
s/v9R8v7J1e5T2yK4N0pL9wO7x8Q4o3e7w6r5y9m8p7u3v4x1w2y3z4a5b6c7d8e
9f0g1h2i3j4k5l6m7n8o9p0q1r2s3t4u5v6w7x8y9z0a1b2c3d4e5f6g7h8i9j0k
1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2g3h4i5j6k7l8m9n0o1p2q
3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9h0i1j2k3l4m5n6o7p8q9r0s1t2u3v4w
5x6y7z8a9b0c1d2e3f4g5h6i7j8k9l0m1n2o3p4q5r6s7t8u9v0w1x2y3z4a5b6c
7d8e9f0g1wIDAQAB
-----END PUBLIC KEY-----',
    'f1e2d3c4b5a6978879605142332415061728394a5b6c7d8e9f0a1b2c3d4e5f60',
    'https://sap-gateway.enterprise.com/webhooks/iso20022',
    'whsec_sap_corporate_secret_token_112233',
    600
) ON CONFLICT (client_id) DO NOTHING;
