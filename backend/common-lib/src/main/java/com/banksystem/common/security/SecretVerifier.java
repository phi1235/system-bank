package com.banksystem.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time comparison for server-side shared secrets. */
public final class SecretVerifier {

  private SecretVerifier() {}

  public static boolean matches(String supplied, String expected) {
    if (supplied == null || expected == null) {
      return false;
    }
    return MessageDigest.isEqual(
        supplied.getBytes(StandardCharsets.UTF_8),
        expected.getBytes(StandardCharsets.UTF_8));
  }
}
