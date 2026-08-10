package com.banksystem.transaction.infrastructure.napas;

import com.banksystem.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Verifies provider callbacks with timestamped HMAC-SHA256 and constant-time comparison. */
@Component
public class NapasWebhookVerifier {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;
  private final long maxClockSkewMillis;
  private final Clock clock;

  public NapasWebhookVerifier(
      @Value("${bank.napas.webhook-secret}") String secret,
      @Value("${bank.napas.webhook-max-clock-skew-seconds}") long maxClockSkewSeconds) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException("NAPAS_WEBHOOK_SECRET must contain at least 32 characters");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.maxClockSkewMillis = Math.multiplyExact(maxClockSkewSeconds, 1_000L);
    this.clock = Clock.systemUTC();
  }

  public long requireTimestamp(String timestampHeader) {
    try {
      return Long.parseLong(timestampHeader);
    } catch (RuntimeException ex) {
      throw forbidden("Missing or invalid NAPAS timestamp");
    }
  }

  public void verify(long timestamp, String signatureHeader, String payload) {
    long now = clock.millis();
    if (timestamp < now - maxClockSkewMillis || timestamp > now + maxClockSkewMillis) {
      throw forbidden("Expired NAPAS callback");
    }
    byte[] supplied = decodeSignature(signatureHeader);
    byte[] expected = sign(payload);
    if (!MessageDigest.isEqual(supplied, expected)) {
      throw forbidden("Invalid NAPAS callback signature");
    }
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot verify NAPAS callback", ex);
    }
  }

  private byte[] decodeSignature(String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      return new byte[0];
    }
    String hex = signatureHeader.startsWith("sha256=")
        ? signatureHeader.substring("sha256=".length()) : signatureHeader;
    if (hex.length() != 64) {
      return new byte[0];
    }
    try {
      return HexFormat.of().parseHex(hex);
    } catch (IllegalArgumentException ex) {
      return new byte[0];
    }
  }

  private BusinessException forbidden(String message) {
    return new BusinessException("INVALID_NAPAS_SIGNATURE", message, HttpStatus.FORBIDDEN);
  }
}
