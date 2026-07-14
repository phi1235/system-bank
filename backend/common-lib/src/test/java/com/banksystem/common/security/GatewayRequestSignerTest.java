package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GatewayRequestSignerTest {

  private static final String SECRET = "test-gateway-signing-secret-at-least-32-bytes";

  @Test
  void verifiesUntamperedRequest() {
    long timestamp = 1_700_000_000_000L;
    String signature = GatewayRequestSigner.sign(
        SECRET, "GET", "/api/v1/accounts", "page=0", "user-1",
        "CUSTOMER", "accounts:view", "INTERNET_BANKING", timestamp);

    assertTrue(GatewayRequestSigner.verify(
        signature, SECRET, "GET", "/api/v1/accounts", "page=0", "user-1",
        "CUSTOMER", "accounts:view", "INTERNET_BANKING", timestamp));
  }

  @Test
  void rejectsTamperedIdentityOrPath() {
    long timestamp = 1_700_000_000_000L;
    String signature = GatewayRequestSigner.sign(
        SECRET, "GET", "/api/v1/accounts", null, "user-1",
        "CUSTOMER", "accounts:view", "INTERNET_BANKING", timestamp);

    assertFalse(GatewayRequestSigner.verify(
        signature, SECRET, "GET", "/api/v1/admin/accounts", null, "user-1",
        "ADMIN", "accounts:freeze:execute", "BACK_OFFICE", timestamp));
  }

  @Test
  void rejectsShortSigningSecret() {
    assertThrows(IllegalArgumentException.class, () -> GatewayRequestSigner.sign(
        "too-short", "GET", "/api/v1/accounts", null, null,
        null, null, null, 0));
  }
}
