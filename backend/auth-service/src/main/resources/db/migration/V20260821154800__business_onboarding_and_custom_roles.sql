-- ====================================================================
-- V20260821154800: Business onboarding (KYC) + Custom roles system
-- ====================================================================

-- 1. Mở rộng business_organizations cho luồng onboarding KYC
ALTER TABLE business_organizations
  ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_KYC',
  ADD COLUMN IF NOT EXISTS short_name VARCHAR(100),
  ADD COLUMN IF NOT EXISTS contact_email VARCHAR(160),
  ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50),
  ADD COLUMN IF NOT EXISTS address VARCHAR(500),
  ADD COLUMN IF NOT EXISTS representative_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS business_license_url VARCHAR(500),
  ADD COLUMN IF NOT EXISTS id_card_url VARCHAR(500),
  ADD COLUMN IF NOT EXISTS industry VARCHAR(100),
  ADD COLUMN IF NOT EXISTS kyc_reviewed_by UUID,
  ADD COLUMN IF NOT EXISTS kyc_reviewed_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS kyc_reject_reason VARCHAR(500);

-- Existing org (demo seed) = đã duyệt
UPDATE business_organizations SET kyc_status = 'APPROVED' WHERE kyc_status = 'PENDING_KYC';

CREATE INDEX IF NOT EXISTS idx_business_org_kyc_status ON business_organizations(kyc_status);

-- 2. Custom roles (vai trò động do DN tự tạo)
CREATE TABLE org_custom_roles (
  id              UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES business_organizations(id) ON DELETE CASCADE,
  code            VARCHAR(50) NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  description     VARCHAR(255),
  is_owner_role   BOOLEAN NOT NULL DEFAULT FALSE,
  is_default      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_org_role_code UNIQUE(organization_id, code)
);

CREATE INDEX IF NOT EXISTS idx_org_custom_roles_org ON org_custom_roles(organization_id);

-- 3. Custom role permissions (DN tick chọn permissions cho mỗi role)
CREATE TABLE org_custom_role_permissions (
  role_id         UUID NOT NULL REFERENCES org_custom_roles(id) ON DELETE CASCADE,
  permission_code VARCHAR(80) NOT NULL,
  PRIMARY KEY (role_id, permission_code)
);

-- 4. Liên kết business_members với custom role (thay vì business_role cứng)
ALTER TABLE business_members
  ADD COLUMN IF NOT EXISTS custom_role_id UUID REFERENCES org_custom_roles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_business_members_custom_role ON business_members(custom_role_id);

-- 5. Seed default roles cho org demo hiện có
DO $$
DECLARE
  v_org_id UUID;
  v_owner_role_id UUID := gen_random_uuid();
  v_viewer_role_id UUID := gen_random_uuid();
BEGIN
  SELECT id INTO v_org_id FROM business_organizations WHERE code = 'TECHMART_VN';

  IF v_org_id IS NOT NULL THEN
    -- Owner role
    INSERT INTO org_custom_roles (id, organization_id, code, display_name, description, is_owner_role, is_default)
    VALUES (v_owner_role_id, v_org_id, 'OWNER', 'Chủ doanh nghiệp', 'Toàn quyền quản lý tổ chức', TRUE, TRUE)
    ON CONFLICT (organization_id, code) DO NOTHING;

    -- Viewer role
    INSERT INTO org_custom_roles (id, organization_id, code, display_name, description, is_owner_role, is_default)
    VALUES (v_viewer_role_id, v_org_id, 'VIEWER', 'Nhân viên', 'Chỉ xem báo cáo và giao dịch', FALSE, TRUE)
    ON CONFLICT (organization_id, code) DO NOTHING;

    -- Owner gets all permissions
    INSERT INTO org_custom_role_permissions (role_id, permission_code)
    SELECT v_owner_role_id, unnest(ARRAY[
      'org:settings', 'org:members', 'org:roles',
      'collection:view', 'collection:create',
      'va:view', 'va:manage',
      'transfer:view', 'transfer:create', 'transfer:approve',
      'batch:create', 'batch:approve',
      'report:view', 'account:manage', 'split:manage',
      'approval:config', 'developer:manage'
    ])
    ON CONFLICT DO NOTHING;

    -- Viewer gets view-only permissions
    INSERT INTO org_custom_role_permissions (role_id, permission_code)
    SELECT v_viewer_role_id, unnest(ARRAY[
      'collection:view', 'va:view', 'transfer:view', 'report:view'
    ])
    ON CONFLICT DO NOTHING;

    -- Update existing members to use owner role
    UPDATE business_members
    SET custom_role_id = v_owner_role_id
    WHERE organization_id = v_org_id AND custom_role_id IS NULL;
  END IF;
END $$;
