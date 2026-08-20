package com.banksystem.transaction.infrastructure.security;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import com.banksystem.transaction.domain.merchant.MerchantApiCredentialEntity;
import com.banksystem.transaction.domain.merchant.MerchantApiCredentialRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MerchantApiAuthInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(MerchantApiAuthInterceptor.class);
  public static final String ATTR_ORGANIZATION_ID = "merchantOrganizationId";
  private static final Pattern NONCE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{8,64}$");

  private final MerchantApiCredentialRepository credentialRepository;
  private final StringRedisTemplate redisTemplate;

  @Value("${bank.aes.secret-key}")
  private String aesSecretKey;

  public MerchantApiAuthInterceptor(
      MerchantApiCredentialRepository credentialRepository,
      StringRedisTemplate redisTemplate) {
    this.credentialRepository = credentialRepository;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String uri = request.getRequestURI();
    if (!uri.startsWith("/api/v1/merchant/")) {
      return true;
    }

    String keyId = request.getHeader("X-Merchant-Key-Id");
    String timestampStr = request.getHeader("X-Merchant-Timestamp");
    String nonce = request.getHeader("X-Merchant-Nonce");
    String signature = request.getHeader("X-Merchant-Signature");

    if (keyId == null || timestampStr == null || nonce == null || signature == null) {
      log.warn("[MERCHANT-API-AUTH] Missing required signature headers on {}", uri);
      throw new BusinessException("UNAUTHORIZED", "Missing required Merchant API headers (X-Merchant-Key-Id, X-Merchant-Timestamp, X-Merchant-Nonce, X-Merchant-Signature)");
    }

    // 1. Nonce format validation
    if (!NONCE_PATTERN.matcher(nonce).matches()) {
      throw new BusinessException("UNAUTHORIZED", "Invalid X-Merchant-Nonce format (alphanumeric, 8-64 chars)");
    }

    // 2. Clock skew verification (< 300s)
    long timestamp;
    try {
      timestamp = Long.parseLong(timestampStr);
      long now = Instant.now().toEpochMilli();
      if (Math.abs(now - timestamp) > 300_000) {
        throw new BusinessException("UNAUTHORIZED", "Request timestamp drift exceeds 300 seconds");
      }
    } catch (NumberFormatException e) {
      throw new BusinessException("UNAUTHORIZED", "Invalid X-Merchant-Timestamp format");
    }

    // 3. Load active credential
    Optional<MerchantApiCredentialEntity> credOpt = credentialRepository.findByKeyId(keyId);
    if (credOpt.isEmpty()) {
      throw new BusinessException("UNAUTHORIZED", "Invalid API key ID");
    }

    MerchantApiCredentialEntity cred = credOpt.get();
    if (!"ACTIVE".equalsIgnoreCase(cred.getStatus())) {
      throw new BusinessException("UNAUTHORIZED", "API key is inactive or revoked");
    }

    Instant now = Instant.now();
    if (cred.getExpiresAt() != null && cred.getExpiresAt().isBefore(now)) {
      throw new BusinessException("UNAUTHORIZED", "API key has expired");
    }

    // 4. Decrypt secret (Fail-closed, no fallback)
    String rawSecret;
    if (aesSecretKey != null && !aesSecretKey.isBlank()) {
      try {
        rawSecret = CryptoUtils.decrypt(cred.getEncryptedSecret(), aesSecretKey);
      } catch (Exception e) {
        log.error("[MERCHANT-API-AUTH] Failed to decrypt secret for keyId={}: {}", keyId, e.getMessage());
        throw new BusinessException("SECURITY_ERROR", "Failed to decrypt API credential");
      }
    } else {
      throw new BusinessException("SECURITY_ERROR", "Server encryption key not configured");
    }

    // 5. Compute SHA256 of raw body bytes
    byte[] bodyBytes = new byte[0];
    if (request instanceof CachedBodyHttpServletRequest cachedReq) {
      bodyBytes = cachedReq.getCachedBody();
    }
    String bodySha256 = sha256Hex(bodyBytes);

    // 6. Build canonical sorted query string
    String canonicalQuery = buildCanonicalQueryString(request);

    // 7. Compute Canonical String & Expected HMAC
    String method = request.getMethod().toUpperCase();
    String canonical = method + "\n" + uri + "\n" + canonicalQuery + "\n" + timestampStr + "\n" + nonce + "\n" + bodySha256;
    String expectedSig = hmacSha256(canonical, rawSecret);

    if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
      log.warn("[MERCHANT-API-AUTH] Invalid HMAC signature for keyId={}", keyId);
      throw new BusinessException("UNAUTHORIZED", "Invalid HMAC signature");
    }

    // 8. Replay attack protection (claim nonce ONLY AFTER valid HMAC to prevent Nonce DoS attacks)
    final Boolean isNewNonce;
    try {
      isNewNonce = redisTemplate.opsForValue().setIfAbsent(
          "merchant:nonce:" + keyId + ":" + nonce, "1", Duration.ofMinutes(10));
    } catch (DataAccessException ex) {
      throw new BusinessException(
          "SERVICE_UNAVAILABLE", "Replay protection is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    if (Boolean.FALSE.equals(isNewNonce)) {
      log.warn("[MERCHANT-API-AUTH] Replay attack detected for keyId={} nonce={}", keyId, nonce);
      throw new BusinessException("UNAUTHORIZED", "Replay attack detected: duplicate nonce " + nonce);
    }

    // Update last used timestamp
    cred.setLastUsedAt(now);
    credentialRepository.save(cred);

    request.setAttribute(ATTR_ORGANIZATION_ID, cred.getOrganizationId());
    return true;
  }

  private String buildCanonicalQueryString(HttpServletRequest request) {
    Map<String, String[]> paramMap = request.getParameterMap();
    if (paramMap == null || paramMap.isEmpty()) {
      return "";
    }
    List<String> sortedKeys = new ArrayList<>(paramMap.keySet());
    Collections.sort(sortedKeys);

    StringBuilder sb = new StringBuilder();
    for (String key : sortedKeys) {
      String[] values = paramMap.get(key);
      if (values == null) continue;
      for (String val : values) {
        if (!sb.isEmpty()) sb.append("&");
        sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
          .append("=")
          .append(URLEncoder.encode(val != null ? val : "", StandardCharsets.UTF_8));
      }
    }
    return sb.toString();
  }

  private String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes != null ? bytes : new byte[0]);
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to calculate SHA-256", e);
    }
  }

  private String hmacSha256(String data, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(rawHmac);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
    }
  }
}
