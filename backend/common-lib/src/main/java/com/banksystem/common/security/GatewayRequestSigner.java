package com.banksystem.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs the gateway-authenticated identity and request target before forwarding downstream. */
public final class GatewayRequestSigner {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private GatewayRequestSigner() {}

  public static String sign(
      String secret,
      String method,
      String path,
      String query,
      String userId,
      String roles,
      String permissions,
      String realm,
      long timestamp) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("Gateway signing secret must contain at least 32 bytes");
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] digest = mac.doFinal(canonical(
          method, path, query, userId, roles, permissions, realm, timestamp)
          .getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
    }
  }

  public static boolean verify(
      String suppliedSignature,
      String secret,
      String method,
      String path,
      String query,
      String userId,
      String roles,
      String permissions,
      String realm,
      long timestamp) {
    if (suppliedSignature == null) {
      return false;
    }
    String expected = sign(secret, method, path, query, userId, roles, permissions, realm, timestamp);
    return SecretVerifier.matches(suppliedSignature, expected);
  }

  private static String canonical(
      String method,
      String path,
      String query,
      String userId,
      String roles,
      String permissions,
      String realm,
      long timestamp) {
    return String.join("\n",
        value(method).toUpperCase(Locale.ROOT),
        value(path),
        value(query),
        value(userId),
        value(roles),
        value(permissions),
        value(realm),
        Long.toString(timestamp));
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
