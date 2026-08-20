package com.banksystem.transaction.infrastructure.va;

import java.time.Instant;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountPoolEntity;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountPoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NapasVirtualAccountProvider implements VirtualAccountProvider {

  private static final Logger log = LoggerFactory.getLogger(NapasVirtualAccountProvider.class);
  public static final String PROVIDER_CODE = "NAPAS";
  private static final String DEFAULT_BANK_BIN = "970436"; // Vietcombank

  private final VirtualAccountPoolRepository poolRepository;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  @Value("${bank.napas.webhook-secret}")
  private String napasWebhookSecret;

  public NapasVirtualAccountProvider(
      VirtualAccountPoolRepository poolRepository,
      ObjectMapper objectMapper,
      StringRedisTemplate redisTemplate) {
    this.poolRepository = poolRepository;
    this.objectMapper = objectMapper;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public String getProviderCode() {
    return PROVIDER_CODE;
  }

  @Override
  @Transactional
  public ProvisionedVirtualAccount provision(VirtualAccountProvisionRequest request) {
    String bankBin = request.bankBin() != null ? request.bankBin() : DEFAULT_BANK_BIN;
    Optional<VirtualAccountPoolEntity> poolOpt = poolRepository.findActivePoolForUpdate(PROVIDER_CODE, bankBin);
    if (poolOpt.isEmpty()) {
      poolOpt = poolRepository.findFirstActiveByProviderForUpdate(PROVIDER_CODE);
    }
    VirtualAccountPoolEntity pool = poolOpt.orElseThrow(() ->
        new BusinessException("VA_POOL_EXHAUSTED", "No active pool for " + PROVIDER_CODE));

    if (pool.getCurrentSeq() > pool.getEndSeq()) {
      throw new BusinessException("VA_POOL_EXHAUSTED", "Pool limit exceeded for " + PROVIDER_CODE);
    }

    long seq = pool.getCurrentSeq();
    pool.setCurrentSeq(seq + 1);
    pool.setUpdatedAt(Instant.now());
    poolRepository.save(pool);

    String accountNumber = pool.getPrefix() + String.format("%06d", seq);
    String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png", pool.getBankBin(), accountNumber);

    log.info("[NAPAS-VA-PROVIDER] Provisioned VA={} for org={}", accountNumber, request.organizationId());
    return new ProvisionedVirtualAccount(PROVIDER_CODE, pool.getBankBin(), accountNumber, qrUrl);
  }

  @Override
  public void close(VirtualAccountCloseRequest request) {
    log.info("[NAPAS-VA-PROVIDER] Close VA={} for provider={}", request.accountNumber(), PROVIDER_CODE);
  }

  @Override
  public VerifiedInboundPayment verifyWebhook(String rawPayload, Map<String, String> headers) {
    String payloadHash = sha256(rawPayload);

    // Fail closed: a registered NAPAS callback is never accepted without a configured secret.
    if (napasWebhookSecret == null || napasWebhookSecret.isBlank()) {
      throw new BusinessException("UNAUTHORIZED", "NAPAS webhook secret is not configured");
    }
    {
      String signature = headers.get("x-signature");
      if (signature == null) signature = headers.get("X-Signature");

      String timestampStr = headers.get("x-timestamp");
      if (timestampStr == null) timestampStr = headers.get("X-Timestamp");

      String nonce = headers.get("x-nonce");
      if (nonce == null) nonce = headers.get("X-Nonce");

      if (signature == null || timestampStr == null || nonce == null) {
        log.warn("[NAPAS-VA-PROVIDER] Missing NAPAS signature headers");
        throw new BusinessException("UNAUTHORIZED", "Missing required NAPAS signature headers (X-Signature, X-Timestamp, X-Nonce)");
      }

      // Verify clock skew (< 300s)
      try {
        long timestamp = Long.parseLong(timestampStr);
        long now = Instant.now().toEpochMilli();
        if (Math.abs(now - timestamp) > 300_000) {
          log.warn("[NAPAS-VA-PROVIDER] Timestamp drift too high: client={}, server={}", timestamp, now);
          throw new BusinessException("UNAUTHORIZED", "NAPAS request timestamp drift exceeds 300 seconds");
        }
      } catch (NumberFormatException e) {
        throw new BusinessException("UNAUTHORIZED", "Invalid X-Timestamp header value");
      }

      // Canonical HMAC verification
      String canonical = timestampStr + "\n" + nonce + "\n" + "POST" + "\n" + "/api/v1/callbacks/collections/napas" + "\n" + payloadHash;
      String expectedSig = hmacSha256(canonical, napasWebhookSecret);
      if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
        log.warn("[NAPAS-VA-PROVIDER] Invalid NAPAS HMAC signature");
        throw new BusinessException("UNAUTHORIZED", "Invalid NAPAS HMAC signature");
      }

      // Redis Nonce Deduplication (claimed ONLY AFTER valid signature to prevent Nonce DoS)
      Boolean isNewNonce = redisTemplate.opsForValue().setIfAbsent(
          "napas:webhook:nonce:" + nonce, "1", Duration.ofMinutes(10)
      );
      if (Boolean.FALSE.equals(isNewNonce)) {
        log.warn("[NAPAS-VA-PROVIDER] Replay attack detected with nonce: {}", nonce);
        throw new BusinessException("UNAUTHORIZED", "Replay attack detected: duplicate nonce " + nonce);
      }
    }

    try {
      JsonNode node = objectMapper.readTree(rawPayload);
      String txId = null;
      if (node.hasNonNull("napasTransactionId")) {
        txId = node.get("napasTransactionId").asText();
      } else if (node.hasNonNull("transactionId")) {
        txId = node.get("transactionId").asText();
      }

      if (txId == null || txId.isBlank()) {
        throw new BusinessException("MISSING_PROVIDER_TX_ID", "NAPAS payload missing required transaction ID");
      }

      String vaNum = "";
      if (node.hasNonNull("virtualAccountNumber")) {
        vaNum = node.get("virtualAccountNumber").asText();
      } else if (node.hasNonNull("accountNumber")) {
        vaNum = node.get("accountNumber").asText();
      }

      String bankBin = node.hasNonNull("bankBin") ? node.get("bankBin").asText() : DEFAULT_BANK_BIN;

      BigDecimal amount = BigDecimal.ZERO;
      if (node.hasNonNull("amount")) {
        amount = new BigDecimal(node.get("amount").asText());
      }
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("INVALID_AMOUNT", "NAPAS webhook amount must be greater than zero");
      }

      String currency = node.hasNonNull("currency") ? node.get("currency").asText().toUpperCase() : "VND";
      String senderAccount = node.hasNonNull("senderAccount") ? node.get("senderAccount").asText() : null;
      String senderBankBin = node.hasNonNull("senderBankBin") ? node.get("senderBankBin").asText() : null;
      String senderName = node.hasNonNull("senderName") ? node.get("senderName").asText() : null;
      String refContent = node.hasNonNull("paymentRef") ? node.get("paymentRef").asText() :
          (node.hasNonNull("content") ? node.get("content").asText() : "");

      return new VerifiedInboundPayment(
          true, PROVIDER_CODE, txId, vaNum, bankBin, amount, currency,
          senderAccount, senderBankBin, senderName, refContent, payloadHash, rawPayload, null
      );
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      log.error("[NAPAS-VA-PROVIDER] Error parsing NAPAS webhook: {}", ex.getMessage());
      return new VerifiedInboundPayment(
          false, PROVIDER_CODE, null, null, null, null, null,
          null, null, null, null, payloadHash, rawPayload, "Failed parsing NAPAS webhook: " + ex.getMessage()
      );
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

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
