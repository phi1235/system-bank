package com.banksystem.transaction.application.merchant;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import com.banksystem.transaction.api.dto.MerchantDtos.ApiCredentialCreatedResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.ApiCredentialResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.ConfigureMerchantAccountRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.CreateApiCredentialRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.MerchantAccountResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.RegisterWebhookEndpointRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.WebhookEndpointCreatedResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.WebhookEndpointResponse;
import com.banksystem.transaction.domain.merchant.MerchantAccountEntity;
import com.banksystem.transaction.domain.merchant.MerchantAccountRepository;
import com.banksystem.transaction.domain.merchant.MerchantApiCredentialEntity;
import com.banksystem.transaction.domain.merchant.MerchantApiCredentialRepository;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointEntity;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.security.SsrfSafeHttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessDeveloperService {

  private static final Logger log = LoggerFactory.getLogger(BusinessDeveloperService.class);

  private final MerchantAccountRepository accountRepository;
  private final MerchantApiCredentialRepository credentialRepository;
  private final MerchantWebhookEndpointRepository webhookRepository;
  private final AccountClient accountClient;
  private final SsrfSafeHttpClient ssrfSafeHttpClient;
  private final MerchantAccountTransactionService merchantAccountTransactionService;
  private final SecureRandom secureRandom = new SecureRandom();
  @Value("${bank.aes.secret-key}")
  private String aesSecretKey;

  @Value("${bank.internal.account-api-key}")
  private String accountApiKey;

  @Value("${bank.merchant.platform-commission-account-id}")
  private String platformCommissionAccountId;

  public BusinessDeveloperService(
      MerchantAccountRepository accountRepository,
      MerchantApiCredentialRepository credentialRepository,
      MerchantWebhookEndpointRepository webhookRepository,
      AccountClient accountClient,
      SsrfSafeHttpClient ssrfSafeHttpClient,
      MerchantAccountTransactionService merchantAccountTransactionService) {
    this.accountRepository = accountRepository;
    this.credentialRepository = credentialRepository;
    this.webhookRepository = webhookRepository;
    this.accountClient = accountClient;
    this.ssrfSafeHttpClient = ssrfSafeHttpClient;
    this.merchantAccountTransactionService = merchantAccountTransactionService;
  }

  @Transactional(readOnly = true)
  public MerchantAccountResponse getMerchantAccount(UUID businessId) {
    return accountRepository.findByOrganizationId(businessId)
        .map(this::toAccountResponse)
        .orElse(null);
  }

  public MerchantAccountResponse configureMerchantAccount(
      UUID businessId, ConfigureMerchantAccountRequest request) {
    String currency = request.defaultCurrency() == null
        ? "VND" : request.defaultCurrency().trim().toUpperCase();
    UUID commissionAccountId = configuredPlatformCommissionAccountId();

    validateAccount(request.collectionAccountId(), currency, "Collection");
    validateAccount(request.escrowAccountId(), currency, "Escrow");
    validateAccount(commissionAccountId, currency, "Platform commission");

    MerchantAccountEntity entity = merchantAccountTransactionService.upsert(
        businessId, request.collectionAccountId(), request.escrowAccountId(),
        commissionAccountId, currency);
    log.info("[MERCHANT-CONFIG] Configured bank-validated accounts for organization {}", businessId);
    return toAccountResponse(entity);
  }

  @Transactional(readOnly = true)
  public List<ApiCredentialResponse> listCredentials(UUID businessId) {
    return credentialRepository.findByOrganizationId(businessId).stream()
        .map(c -> new ApiCredentialResponse(
            c.getId(), c.getKeyId(), c.getName(), c.getStatus(), c.getExpiresAt(),
            c.getLastUsedAt(), c.getCreatedAt()))
        .toList();
  }

  @Transactional
  public ApiCredentialCreatedResponse createCredential(
      UUID businessId, CreateApiCredentialRequest request) {
    String keyId = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String rawSecret = generateSecret("sec_");
    Instant now = Instant.now();
    MerchantApiCredentialEntity entity = MerchantApiCredentialEntity.create(
        keyId, businessId, request.name().trim(), sha256(rawSecret), encryptSecret(rawSecret),
        request.expiresAt(), now);
    credentialRepository.save(entity);
    return new ApiCredentialCreatedResponse(
        entity.getId(), entity.getKeyId(), rawSecret, entity.getName(), entity.getStatus(),
        entity.getExpiresAt(), entity.getCreatedAt());
  }

  @Transactional
  public void revokeCredential(UUID businessId, UUID credentialId) {
    MerchantApiCredentialEntity entity = credentialRepository.findById(credentialId).orElseThrow(() ->
        new BusinessException("CREDENTIAL_NOT_FOUND", "Credential not found"));
    requireOrganization(businessId, entity.getOrganizationId());
    entity.setStatus("REVOKED");
    entity.setUpdatedAt(Instant.now());
    credentialRepository.save(entity);
  }

  @Transactional(readOnly = true)
  public List<WebhookEndpointResponse> listWebhooks(UUID businessId) {
    return webhookRepository.findByOrganizationId(businessId).stream()
        .map(w -> new WebhookEndpointResponse(
            w.getId(), w.getUrl(), w.getEventTypes(), w.getStatus(), w.getCreatedAt()))
        .toList();
  }

  public WebhookEndpointCreatedResponse registerWebhook(
      UUID businessId, RegisterWebhookEndpointRequest request) {
    String url = request.url().trim();
    try {
      ssrfSafeHttpClient.validateUrl(url);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException("INVALID_WEBHOOK_URL", ex.getMessage());
    }
    String rawSecret = generateSecret("whsec_");
    Instant now = Instant.now();
    MerchantWebhookEndpointEntity entity = MerchantWebhookEndpointEntity.create(
        businessId, url, request.eventTypes(), sha256(rawSecret), encryptSecret(rawSecret), now);
    webhookRepository.save(entity);
    return new WebhookEndpointCreatedResponse(
        entity.getId(), entity.getUrl(), entity.getEventTypes(), rawSecret,
        entity.getStatus(), entity.getCreatedAt());
  }

  @Transactional
  public void deactivateWebhook(UUID businessId, UUID endpointId) {
    MerchantWebhookEndpointEntity entity = webhookRepository.findById(endpointId).orElseThrow(() ->
        new BusinessException("WEBHOOK_NOT_FOUND", "Webhook endpoint not found"));
    requireOrganization(businessId, entity.getOrganizationId());
    entity.setStatus("INACTIVE");
    entity.setUpdatedAt(Instant.now());
    webhookRepository.save(entity);
  }

  private void validateAccount(UUID accountId, String expectedCurrency, String label) {
    try {
      ApiResponse<AccountView> response = accountClient.getById(accountId, accountApiKey);
      if (response == null || response.data() == null) {
        throw new BusinessException("INVALID_ACCOUNT", label + " account was not found");
      }
      AccountView account = response.data();
      if (!"ACTIVE".equalsIgnoreCase(account.status())) {
        throw new BusinessException("ACCOUNT_INACTIVE", label + " account is not active");
      }
      if (!expectedCurrency.equalsIgnoreCase(account.currency())) {
        throw new BusinessException("CURRENCY_MISMATCH", label + " account currency does not match");
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(
          "SERVICE_UNAVAILABLE", "Account validation service is unavailable",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private UUID configuredPlatformCommissionAccountId() {
    try {
      return UUID.fromString(platformCommissionAccountId);
    } catch (RuntimeException ex) {
      throw new BusinessException(
          "SECURITY_ERROR", "Platform commission account configuration is invalid",
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private String generateSecret(String prefix) {
    byte[] secretBytes = new byte[32];
    secureRandom.nextBytes(secretBytes);
    return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
  }

  private String encryptSecret(String rawSecret) {
    if (aesSecretKey == null || aesSecretKey.isBlank()) {
      throw new BusinessException("SECURITY_ERROR", "Credential encryption key is not configured");
    }
    try {
      return CryptoUtils.encrypt(rawSecret, aesSecretKey);
    } catch (RuntimeException ex) {
      throw new BusinessException("SECURITY_ERROR", "Failed to encrypt merchant credential");
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

  private void requireOrganization(UUID expected, UUID actual) {
    if (!expected.equals(actual)) {
      throw new BusinessException("FORBIDDEN", "Resource belongs to another organization");
    }
  }

  private MerchantAccountResponse toAccountResponse(MerchantAccountEntity entity) {
    return new MerchantAccountResponse(
        entity.getId(), entity.getOrganizationId(), entity.getCollectionAccountId(),
        entity.getEscrowAccountId(), entity.getCommissionAccountId(), entity.getDefaultCurrency(),
        entity.getStatus(), entity.getCreatedAt());
  }
}
