package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

  private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  @Test
  void encryptDecryptRoundTrip() {
    String plain = "001234567890";
    String encrypted = CryptoUtils.encrypt(plain, KEY);
    assertNotEquals(plain, encrypted);
    assertEquals(plain, CryptoUtils.decrypt(encrypted, KEY));
  }

  @Test
  void maskNationalId() {
    assertEquals("********7890", CryptoUtils.maskNationalId("001234567890"));
  }
}
