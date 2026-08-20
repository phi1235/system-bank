package com.banksystem.common.security;

public final class SecurityHeaders {
  public static final String CORRELATION_ID = "X-Correlation-Id";
  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String USER_PERMISSIONS = "X-User-Permissions";
  public static final String USER_REALM = "X-User-Realm";
  public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";
  public static final String GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";
  public static final String GATEWAY_SIGNATURE = "X-Gateway-Signature";

  /* ── Open Banking B2B Headers & FAPI ── */
  public static final String B2B_CLIENT_ID = "X-B2B-Client-Id";
  public static final String B2B_SCOPES = "X-B2B-Scopes";
  public static final String B2B_CERT_THUMBPRINT = "X-B2B-Cert-Thumbprint";
  public static final String B2B_ORG_TAX = "X-B2B-Org-Tax";
  public static final String JWS_SIGNATURE = "X-JWS-Signature";

  public static final String JWT_CLAIM_ROLES = "roles";
  public static final String JWT_CLAIM_PERMISSIONS = "permissions";
  public static final String JWT_CLAIM_TYPE = "typ";
  public static final String JWT_CLAIM_REALM = "realm";
  public static final String JWT_CLAIM_CNF = "cnf";
  public static final String JWT_CLAIM_CLIENT_ID = "client_id";

  /* ── Granular BO permissions: screen:feature:action ── */
  public static final String PERM_DASHBOARD_VIEW = "dashboard:view";
  public static final String PERM_CUSTOMERS_LIST_VIEW = "customers:list:view";
  public static final String PERM_CUSTOMERS_KYC_DECIDE = "customers:kyc:decide";
  public static final String PERM_CUSTOMERS_KYC_REVIEW = "customers:kyc:review";
  public static final String PERM_CUSTOMERS_KYC_APPROVE = "customers:kyc:approve";
  public static final String PERM_ACCOUNTS_LOOKUP_VIEW = "accounts:lookup:view";
  public static final String PERM_ACCOUNTS_FREEZE_EXECUTE = "accounts:freeze:execute";
  public static final String PERM_ACCOUNTS_TOPUP_EXECUTE = "accounts:topup:execute";
  public static final String PERM_TX_LIST_VIEW = "transactions:list:view";
  public static final String PERM_TX_RECON_VIEW = "transactions:recon:view";
  public static final String PERM_TX_RECON_MANAGE = "transactions:recon:manage";
  public static final String PERM_AUDIT_LIST_VIEW = "audit:list:view";
  public static final String PERM_RBAC_ACCESS = "rbac:access";
  public static final String PERM_RBAC_USERS_ASSIGN = "rbac:users:assign";
  public static final String PERM_RBAC_ROLES_MANAGE = "rbac:roles:manage";
  public static final String PERM_RISK_VIEW = "risk:view";
  public static final String PERM_RISK_MANAGE = "risk:manage";
  public static final String PERM_RISK_DECIDE = "risk:decide";
  public static final String PERM_FORENSICS_VIEW = "forensics:view";
  public static final String PERM_FORENSICS_VERIFY_EXECUTE = "forensics:verify:execute";
  public static final String PERM_FORENSICS_CASE_REVIEW = "forensics:case:review";
  public static final String PERM_FORENSICS_EVIDENCE_EXPORT = "forensics:evidence:export";
  public static final String PERM_FORENSICS_REPLAY_EXECUTE = "forensics:replay:execute";
  public static final String PERM_FORENSICS_COPILOT_USE = "forensics:copilot:use";
  public static final String PERM_FORENSICS_AUDIT_VIEW = "forensics:audit:view";
  public static final String PERM_FORENSICS_ADMIN = "forensics:admin";
  public static final String PERM_NOTIFICATIONS_OPS_VIEW = "notifications:ops:view";
  public static final String PERM_VA_OPERATIONS_VIEW = "va:operations:view";
  public static final String PERM_VA_OPERATIONS_REVIEW = "va:operations:review";
  public static final String PERM_SETTLEMENT_VIEW = "settlement:view";
  public static final String PERM_SETTLEMENT_RETRY = "settlement:retry";
  public static final String PERM_SETTLEMENT_APPROVE = "settlement:approve";
  public static final String PERM_PAYOUT_VIEW = "payout:view";
  public static final String PERM_PAYOUT_APPROVE = "payout:approve";

  /* ── Business Portal (B2B Merchant) ── */
  public static final String PERM_BUSINESS_DASHBOARD_VIEW = "business:dashboard:view";
  public static final String PERM_BUSINESS_VA_VIEW = "business:va:view";
  public static final String PERM_BUSINESS_VA_MANAGE = "business:va:manage";
  public static final String PERM_BUSINESS_ORDERS_VIEW = "business:orders:view";
  public static final String PERM_BUSINESS_ORDERS_MANAGE = "business:orders:manage";
  public static final String PERM_BUSINESS_SETTLEMENTS_VIEW = "business:settlements:view";
  public static final String PERM_BUSINESS_SETTLEMENTS_EXECUTE = "business:settlements:execute";
  public static final String PERM_BUSINESS_SPLIT_VIEW = "business:split:view";
  public static final String PERM_BUSINESS_SPLIT_MANAGE = "business:split:manage";
  public static final String PERM_BUSINESS_CREDENTIALS_MANAGE = "business:credentials:manage";

  /* ── Internet Banking (customer portal) ── */
  public static final String PERM_IB_HOME_VIEW = "ib:home:view";
  public static final String PERM_IB_ACCOUNTS_VIEW = "ib:accounts:view";
  public static final String PERM_IB_ACCOUNTS_OPEN = "ib:accounts:open";
  public static final String PERM_IB_TRANSFER_VIEW = "ib:transfer:view";
  public static final String PERM_IB_TRANSFER_EXECUTE = "ib:transfer:execute";
  public static final String PERM_IB_HISTORY_VIEW = "ib:history:view";
  public static final String PERM_IB_PROFILE_VIEW = "ib:profile:view";
  public static final String PERM_IB_PROFILE_EDIT = "ib:profile:edit";
  public static final String PERM_IB_PROFILE_MFA = "ib:profile:mfa";
  public static final String PERM_IB_CARDS_VIEW = "ib:cards:view";
  public static final String PERM_IB_WEALTH_VIEW = "ib:wealth:view";
  public static final String PERM_IB_SUPPORT_VIEW = "ib:support:view";
  public static final String PERM_IB_NOTIFICATIONS_VIEW = "ib:notifications:view";

  /* ── Corporate Portal ── */
  public static final String PERM_CORP_PORTAL_VIEW = "corp:portal:view";
  public static final String PERM_CORP_PAYOUT_CREATE = "corp:payout:create";
  public static final String PERM_CORP_PAYOUT_SUBMIT = "corp:payout:submit";
  public static final String PERM_CORP_PAYOUT_APPROVE_CHECKER = "corp:payout:approve:checker";
  public static final String PERM_CORP_PAYOUT_APPROVE_CFO = "corp:payout:approve:cfo";
  public static final String PERM_CORP_PAYOUT_APPROVE_CHAIRMAN = "corp:payout:approve:chairman";
  public static final String PERM_CORP_PAYOUT_CANCEL = "corp:payout:cancel";
  public static final String PERM_CORP_PAYOUT_RETRY = "corp:payout:retry";
  public static final String PERM_CORP_MATRIX_VIEW = "corp:matrix:view";
  public static final String PERM_CORP_MATRIX_MANAGE = "corp:matrix:manage";
  public static final String PERM_CORP_RECEIPT_DOWNLOAD = "corp:receipt:download";
  public static final String PERM_CORP_AUDIT_VIEW = "corp:audit:view";

  /* ── Open Banking B2B Scopes ── */
  public static final String SCOPE_OPENBANKING_ACCOUNTS_READ = "openbanking:accounts:read";
  public static final String SCOPE_OPENBANKING_STATEMENTS_READ = "openbanking:statements:read";
  public static final String SCOPE_OPENBANKING_PAYMENTS_WRITE = "openbanking:payments:write";
  public static final String SCOPE_OPENBANKING_PAYMENTS_BULK_WRITE = "openbanking:payments:bulk:write";
  public static final String SCOPE_OPENBANKING_PAYMENTS_READ = "openbanking:payments:read";

  /* ── Open Banking Developer Portal Permissions ── */
  public static final String PERM_B2B_OPENBANKING_APPS_VIEW = "b2b:openbanking:apps:view";
  public static final String PERM_B2B_OPENBANKING_APPS_MANAGE = "b2b:openbanking:apps:manage";
  public static final String PERM_B2B_OPENBANKING_CONSENTS_VIEW = "b2b:openbanking:consents:view";
  public static final String PERM_B2B_OPENBANKING_CONSENTS_MANAGE = "b2b:openbanking:consents:manage";
  public static final String PERM_B2B_OPENBANKING_SANDBOX_USE = "b2b:openbanking:sandbox:use";
  public static final String PERM_B2B_OPENBANKING_LOGS_VIEW = "b2b:openbanking:logs:view";

  private SecurityHeaders() {}
}
