package com.banksystem.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.banksystem.common.security.CryptoUtils;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoMaskTest {
  private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  @Test
  void encryptAndMask() {
    String id = "001234567890";
    String enc = CryptoUtils.encrypt(id, KEY);
    assertNotEquals(id, enc);
    assertEquals(id, CryptoUtils.decrypt(enc, KEY));
    assertEquals("********7890", CryptoUtils.maskNationalId(id));
  }
}
