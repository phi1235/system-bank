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
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MockVirtualAccountProvider implements VirtualAccountProvider {

  private static final Logger log = LoggerFactory.getLogger(MockVirtualAccountProvider.class);
  public static final String PROVIDER_CODE = "MOCK";

  private final VirtualAccountPoolRepository poolRepository;
  private final ObjectMapper objectMapper;

  @Value("${bank.va.mock-enabled}")
  private boolean mockEnabled;

  @Value("${bank.va.mock-sandbox-key}")
  private String sandboxKey;

  @Value("${bank.va.default-bank-bin}")
  private String defaultBankBin;

  @Value("${spring.profiles.active:}")
  private String activeProfiles;

  public MockVirtualAccountProvider(
      VirtualAccountPoolRepository poolRepository,
      ObjectMapper objectMapper) {
    this.poolRepository = poolRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getProviderCode() {
    return PROVIDER_CODE;
  }

  @Override
  @Transactional
  public ProvisionedVirtualAccount provision(VirtualAccountProvisionRequest request) {
    ensureMockAllowed();
    String bankBin = request.bankBin() != null ? request.bankBin() : defaultBankBin;
    Optional<VirtualAccountPoolEntity> poolOpt = poolRepository.findActivePoolForUpdate(PROVIDER_CODE, bankBin);
    if (poolOpt.isEmpty()) {
      poolOpt = poolRepository.findFirstActiveByProviderForUpdate(PROVIDER_CODE);
    }
    VirtualAccountPoolEntity pool = poolOpt.orElseThrow(() ->
        new BusinessException("VA_POOL_EXHAUSTED", "No active virtual account pool available for provider " + PROVIDER_CODE));

    if (pool.getCurrentSeq() > pool.getEndSeq()) {
      throw new BusinessException("VA_POOL_EXHAUSTED", "Virtual account pool range reached maximum limit");
    }

    long seq = pool.getCurrentSeq();
    pool.setCurrentSeq(seq + 1);
    pool.setUpdatedAt(Instant.now());
    poolRepository.save(pool);

    String accountNumber = pool.getPrefix() + String.format("%06d", seq);
    String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png", pool.getBankBin(), accountNumber);

    log.info("[MOCK-VA-PROVIDER] Provisioned mock VA={} for org={}", accountNumber, request.organizationId());
    return new ProvisionedVirtualAccount(PROVIDER_CODE, pool.getBankBin(), accountNumber, qrUrl);
  }

  @Override
  public void close(VirtualAccountCloseRequest request) {
    ensureMockAllowed();
    log.info("[MOCK-VA-PROVIDER] Close mock VA={} for provider={}", request.accountNumber(), PROVIDER_CODE);
  }

  @Override
  public VerifiedInboundPayment verifyWebhook(String rawPayload, Map<String, String> headers) {
    ensureMockAllowed();
    verifySandboxKey(headers);
    String payloadHash = sha256(rawPayload);
    try {
      JsonNode node = objectMapper.readTree(rawPayload);
      String txId = node.hasNonNull("transactionId") ? node.get("transactionId").asText() :
          (node.hasNonNull("id") ? node.get("id").asText() : null);

      if (txId == null || txId.isBlank()) {
        throw new BusinessException("MISSING_PROVIDER_TX_ID", "Mock payload missing required transaction ID");
      }

      String vaNum = node.hasNonNull("virtualAccountNumber") ? node.get("virtualAccountNumber").asText() :
          (node.hasNonNull("accountNumber") ? node.get("accountNumber").asText() : "");
      String bankBin = node.hasNonNull("bankBin") ? node.get("bankBin").asText() : defaultBankBin;

      BigDecimal amount = BigDecimal.ZERO;
      if (node.hasNonNull("amount")) {
        amount = new BigDecimal(node.get("amount").asText());
      }
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("INVALID_AMOUNT", "Mock webhook amount must be positive");
      }

      String currency = node.hasNonNull("currency") ? node.get("currency").asText().toUpperCase() : "VND";
      String senderAccount = node.hasNonNull("senderAccount") ? node.get("senderAccount").asText() : "MOCK_SENDER";
      String senderBankBin = node.hasNonNull("senderBankBin") ? node.get("senderBankBin").asText() : defaultBankBin;
      String senderName = node.hasNonNull("senderName") ? node.get("senderName").asText() : "Mock Payer";
      String refContent = node.hasNonNull("content") ? node.get("content").asText() : "";

      return new VerifiedInboundPayment(
          true, PROVIDER_CODE, txId, vaNum, bankBin, amount, currency,
          senderAccount, senderBankBin, senderName, refContent, payloadHash, rawPayload, null
      );
    } catch (BusinessException e) {
      throw e;
    } catch (Exception ex) {
      return new VerifiedInboundPayment(
          false, PROVIDER_CODE, null, null, null, null, null,
          null, null, null, null, payloadHash, rawPayload, "Failed parsing mock webhook: " + ex.getMessage()
      );
    }
  }

  private void ensureMockAllowed() {
    if (!mockEnabled || (activeProfiles != null && activeProfiles.contains("prod"))) {
      throw new BusinessException("MOCK_PROVIDER_DISABLED", "Mock provider is strictly disabled in production");
    }
  }

  private void verifySandboxKey(Map<String, String> headers) {
    if (sandboxKey == null || sandboxKey.isBlank()) {
      throw new BusinessException("UNAUTHORIZED", "Mock VA sandbox key is not configured");
    }
    String supplied = headers.get("x-sandbox-key");
    if (supplied == null) supplied = headers.get("X-Sandbox-Key");
    if (supplied == null || !MessageDigest.isEqual(
        sandboxKey.getBytes(StandardCharsets.UTF_8), supplied.trim().getBytes(StandardCharsets.UTF_8))) {
      throw new BusinessException("UNAUTHORIZED", "Invalid mock VA sandbox key");
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
