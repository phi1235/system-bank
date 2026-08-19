package com.banksystem.transaction.application.transfer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BeneficiaryCryptoService {

  private static final String GCM_TRANSFORM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private static final String HMAC_ALGO = "HmacSHA256";
  private static final String ENVELOPE_PREFIX = "v1:";

  private final SecretKey aesKey;
  private final SecretKeySpec hmacKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public BeneficiaryCryptoService(
      @Value("${bank.security.aes-key}") String aesKey,
      @Value("${bank.security.hmac-key}") String hmacKey) {
    byte[] aesKeyBytes = decodeBase64Key(aesKey, "BANK_ENCRYPTION_AES_KEY");
    if (aesKeyBytes.length != 32) {
      throw new IllegalArgumentException("BANK_ENCRYPTION_AES_KEY must decode to exactly 32 bytes");
    }
    byte[] hmacKeyBytes = decodeBase64Key(hmacKey, "BANK_ENCRYPTION_HMAC_KEY");
    if (hmacKeyBytes.length < 32) {
      throw new IllegalArgumentException("BANK_ENCRYPTION_HMAC_KEY must decode to at least 32 bytes");
    }
    this.aesKey = new SecretKeySpec(aesKeyBytes, "AES");
    this.hmacKey = new SecretKeySpec(hmacKeyBytes, HMAC_ALGO);
  }

  public String encryptEnvelope(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return "";
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(GCM_TRANSFORM);
      cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      String ivBase64 = Base64.getEncoder().encodeToString(iv);
      String cipherBase64 = Base64.getEncoder().encodeToString(cipherText);
      return ENVELOPE_PREFIX + ivBase64 + ":" + cipherBase64;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  public String decryptEnvelope(String envelope) {
    if (envelope == null || envelope.isBlank()) {
      return "";
    }
    try {
      if (!envelope.startsWith(ENVELOPE_PREFIX)) {
        throw new IllegalArgumentException("Unsupported envelope version: " + envelope);
      }
      String[] parts = envelope.substring(ENVELOPE_PREFIX.length()).split(":");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Malformed encrypted envelope format");
      }
      byte[] iv = Base64.getDecoder().decode(parts[0]);
      byte[] cipherBytes = Base64.getDecoder().decode(parts[1]);

      Cipher cipher = Cipher.getInstance(GCM_TRANSFORM);
      cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] plain = cipher.doFinal(cipherBytes);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM decryption failed", e);
    }
  }

  public String computeHmac(String data) {
    if (data == null || data.isBlank()) {
      return "";
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(hmacKey);
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(rawHmac);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC computation failed", e);
    }
  }

  public String maskAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.length() < 4) {
      return "******";
    }
    String last4 = accountNumber.substring(accountNumber.length() - 4);
    return "******" + last4;
  }

  public boolean matchesHmac(String data, String expectedBase64Hmac) {
    if (expectedBase64Hmac == null || expectedBase64Hmac.isBlank()) {
      return false;
    }
    try {
      byte[] expected = Base64.getDecoder().decode(expectedBase64Hmac);
      byte[] actual = Base64.getDecoder().decode(computeHmac(data));
      return MessageDigest.isEqual(actual, expected);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private byte[] decodeBase64Key(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(propertyName + " is required");
    }
    try {
      return Base64.getDecoder().decode(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(propertyName + " must be valid Base64", ex);
    }
  }
}
