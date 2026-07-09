package com.banksystem.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password at rest = BCrypt( material ), where material binds:
 * <ul>
 *   <li>plaintext password</li>
 *   <li>normalized username (identity)</li>
 *   <li>server-side pepper (env, never in DB)</li>
 * </ul>
 *
 * <p>Effect: if an attacker dumps {@code password_hash} and pastes it onto another row
 * (or invents a raw BCrypt of "123456" without pepper), verification fails for that username.
 * This is still a one-way hash — passwords are NOT stored encrypted/reversible.
 */
@Component
public class BoundPasswordEncoder {

  private final PasswordEncoder bcrypt;
  private final byte[] pepper;

  public BoundPasswordEncoder(
      @Value("${bank.security.password-pepper}") String pepper) {
    this.bcrypt = new BCryptPasswordEncoder(12);
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  public String encode(String rawPassword, String username) {
    return bcrypt.encode(material(rawPassword, username));
  }

  public boolean matches(String rawPassword, String username, String storedHash) {
    if (rawPassword == null || username == null || storedHash == null || storedHash.isBlank()) {
      return false;
    }
    return bcrypt.matches(material(rawPassword, username), storedHash);
  }

  /**
   * HMAC-SHA256(pepper, password || 0x00 || username) → hex.
   * Username normalized so "Admin" and "admin" share the same binding for case-insensitive policy.
   */
  String material(String rawPassword, String username) {
    String u = normalizeUsername(username);
    String payload = rawPassword + "\0" + u;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return toHex(digest);
    } catch (Exception e) {
      // Fallback should never happen on standard JRE
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(pepper);
        return toHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException ex) {
        throw new IllegalStateException("No digest algorithm", ex);
      }
    }
  }

  public static String normalizeUsername(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
