package com.banksystem.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM helpers for PII / TOTP secrets.
 * Key: base64-encoded 32-byte secret (AES-256).
 */
public final class CryptoUtils {
  private static final String TRANSFORM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;

  private CryptoUtils() {}

  public static String encrypt(String plaintext, String base64Key) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORM);
      cipher.init(Cipher.ENCRYPT_MODE, toKey(base64Key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
      buffer.put(iv);
      buffer.put(cipherText);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES encrypt failed", e);
    }
  }

  public static String decrypt(String ciphertextBase64, String base64Key) {
    try {
      byte[] decoded = Base64.getDecoder().decode(ciphertextBase64);
      ByteBuffer buffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      buffer.get(iv);
      byte[] cipherBytes = new byte[buffer.remaining()];
      buffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance(TRANSFORM);
      cipher.init(Cipher.DECRYPT_MODE, toKey(base64Key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] plain = cipher.doFinal(cipherBytes);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES decrypt failed", e);
    }
  }

  public static String maskNationalId(String nationalId) {
    if (nationalId == null || nationalId.length() < 4) {
      return "****";
    }
    int visible = 4;
    return "*".repeat(nationalId.length() - visible) + nationalId.substring(nationalId.length() - visible);
  }

  private static SecretKey toKey(String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new IllegalArgumentException("AES key must be 16/24/32 bytes after base64 decode");
    }
    return new SecretKeySpec(keyBytes, "AES");
  }
}
