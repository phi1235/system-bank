package com.banksystem.transaction.infrastructure.va;

import java.time.Instant;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.config.SepayProperties;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountPoolEntity;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountPoolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SepayVirtualAccountProvider implements VirtualAccountProvider {

  private static final Logger log = LoggerFactory.getLogger(SepayVirtualAccountProvider.class);
  public static final String PROVIDER_CODE = "SEPAY";

  private final VirtualAccountPoolRepository poolRepository;
  private final SepayProperties sepayProperties;
  private final ObjectMapper objectMapper;

  @Value("${bank.va.default-bank-bin}")
  private String defaultBankBin;

  public SepayVirtualAccountProvider(
      VirtualAccountPoolRepository poolRepository,
      SepayProperties sepayProperties,
      ObjectMapper objectMapper) {
    this.poolRepository = poolRepository;
    this.sepayProperties = sepayProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getProviderCode() {
    return PROVIDER_CODE;
  }

  @Override
  @Transactional
  public ProvisionedVirtualAccount provision(VirtualAccountProvisionRequest request) {
    String bankBin = request.bankBin() != null ? request.bankBin() : defaultBankBin;
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

    log.info("[SEPAY-VA-PROVIDER] Provisioned VA={} for org={}", accountNumber, request.organizationId());
    return new ProvisionedVirtualAccount(PROVIDER_CODE, pool.getBankBin(), accountNumber, qrUrl);
  }

  @Override
  public void close(VirtualAccountCloseRequest request) {
    log.info("[SEPAY-VA-PROVIDER] Close VA={} for provider={}", request.accountNumber(), PROVIDER_CODE);
  }

  @Override
  public VerifiedInboundPayment verifyWebhook(String rawPayload, Map<String, String> headers) {
    String payloadHash = sha256(rawPayload);

    // Strict SePay API Key verification with constant-time comparison.
    String configuredApiKey = sepayProperties.getApiKey();
    if (configuredApiKey == null || configuredApiKey.isBlank()) {
      throw new BusinessException("UNAUTHORIZED", "SePay webhook API key is not configured");
    }
    String authHeader = headers.get("authorization");
    if (authHeader == null) authHeader = headers.get("Authorization");
    if (authHeader == null || authHeader.isBlank()) {
      log.warn("[SEPAY-VA-PROVIDER] Missing Authorization header in SePay webhook");
      throw new BusinessException("UNAUTHORIZED", "Missing SePay webhook authorization header");
    }

    String expected = "Apikey " + configuredApiKey;
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), authHeader.trim().getBytes(StandardCharsets.UTF_8))) {
      log.warn("[SEPAY-VA-PROVIDER] SePay webhook API key mismatch");
      throw new BusinessException("UNAUTHORIZED", "Invalid SePay webhook API token");
    }

    try {
      JsonNode node = objectMapper.readTree(rawPayload);

      String txId = null;
      if (node.hasNonNull("id")) {
        txId = node.get("id").asText();
      } else if (node.hasNonNull("transactionId")) {
        txId = node.get("transactionId").asText();
      }

      if (txId == null || txId.isBlank()) {
        throw new BusinessException("MISSING_PROVIDER_TX_ID", "SePay payload missing required transaction ID");
      }

      String vaNum = "";
      if (node.hasNonNull("accountNumber")) {
        vaNum = node.get("accountNumber").asText();
      } else if (node.hasNonNull("virtualAccountNumber")) {
        vaNum = node.get("virtualAccountNumber").asText();
      }

      String bankBin = defaultBankBin;
      if (node.hasNonNull("bankBin")) {
        bankBin = node.get("bankBin").asText();
      } else if (node.hasNonNull("gateway")) {
        String gw = node.get("gateway").asText();
        if ("MBBank".equalsIgnoreCase(gw) || "MB".equalsIgnoreCase(gw)) bankBin = "970422";
        else if ("Vietcombank".equalsIgnoreCase(gw) || "VCB".equalsIgnoreCase(gw)) bankBin = "970436";
        else if ("Techcombank".equalsIgnoreCase(gw) || "TCB".equalsIgnoreCase(gw)) bankBin = "970407";
      }

      BigDecimal amount = BigDecimal.ZERO;
      if (node.hasNonNull("transferAmount")) {
        amount = new BigDecimal(node.get("transferAmount").asText());
      } else if (node.hasNonNull("amount")) {
        amount = new BigDecimal(node.get("amount").asText());
      }

      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("INVALID_AMOUNT", "SePay webhook transfer amount must be greater than zero");
      }

      String currency = "VND";
      String senderAccount = node.hasNonNull("senderAccount") ? node.get("senderAccount").asText() : null;
      String senderBankBin = node.hasNonNull("senderBankBin") ? node.get("senderBankBin").asText() : null;
      String senderName = node.hasNonNull("senderName") ? node.get("senderName").asText() : null;
      String refContent = node.hasNonNull("content") ? node.get("content").asText() : "";

      return new VerifiedInboundPayment(
          true, PROVIDER_CODE, txId, vaNum, bankBin, amount, currency,
          senderAccount, senderBankBin, senderName, refContent, payloadHash, rawPayload, null
      );
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      log.error("[SEPAY-VA-PROVIDER] Error parsing SePay webhook: {}", ex.getMessage());
      return new VerifiedInboundPayment(
          false, PROVIDER_CODE, null, null, null, null, null,
          null, null, null, null, payloadHash, rawPayload, "Failed parsing SePay webhook: " + ex.getMessage()
      );
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
