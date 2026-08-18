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

  public static final String JWT_CLAIM_ROLES = "roles";
  public static final String JWT_CLAIM_PERMISSIONS = "permissions";
  public static final String JWT_CLAIM_TYPE = "typ";
  public static final String JWT_CLAIM_REALM = "realm";

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

  private SecurityHeaders() {}
}
