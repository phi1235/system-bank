package com.banksystem.common.security;

public final class SecurityHeaders {
  public static final String CORRELATION_ID = "X-Correlation-Id";
  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String USER_REALM = "X-User-Realm";
  public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

  public static final String JWT_CLAIM_ROLES = "roles";
  public static final String JWT_CLAIM_TYPE = "typ";
  public static final String JWT_CLAIM_REALM = "realm";

  private SecurityHeaders() {}
}
