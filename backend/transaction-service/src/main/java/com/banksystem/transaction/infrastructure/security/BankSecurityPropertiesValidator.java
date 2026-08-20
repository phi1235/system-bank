package com.banksystem.transaction.infrastructure.security;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BankSecurityPropertiesValidator {

  private static final Logger log = LoggerFactory.getLogger(BankSecurityPropertiesValidator.class);

  @Value("${bank.aes.secret-key:${AES_SECRET_KEY:}}")
  private String aesSecretKey;

  @Value("${bank.internal.auth-api-key:${AUTH_INTERNAL_API_KEY:}}")
  private String authApiKey;

  @Value("${bank.internal.account-api-key:${ACCOUNT_INTERNAL_API_KEY:}}")
  private String accountApiKey;

  @Value("${bank.napas.provider:${NAPAS_PROVIDER:mock}}")
  private String napasProvider;

  @Value("${bank.napas.webhook-secret:${NAPAS_WEBHOOK_SECRET:}}")
  private String napasWebhookSecret;

  @Value("${bank.sepay.enabled:${SEPAY_ENABLED:false}}")
  private boolean sepayEnabled;

  @Value("${bank.sepay.api-key:${SEPAY_API_KEY:}}")
  private String sepayApiKey;

  @Value("${bank.va.mock-enabled:${MOCK_VA_ENABLED:false}}")
  private boolean mockVaEnabled;

  @Value("${bank.va.mock-sandbox-key:${MOCK_VA_SANDBOX_KEY:}}")
  private String mockVaSandboxKey;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @PostConstruct
  public void validateAllSecurityProperties() {
    boolean isTestProfile = activeProfile != null && (activeProfile.contains("test") || activeProfile.contains("unit"));

    // 1. AES Key Validation (Base64 decode + 32 bytes exact for AES-256)
    if (aesSecretKey == null || aesSecretKey.isBlank()) {
      if (!isTestProfile) {
        throw new IllegalStateException("FAIL-CLOSED: AES_SECRET_KEY is mandatory and cannot be blank");
      }
    } else {
      try {
        byte[] decoded = Base64.getDecoder().decode(aesSecretKey);
        if (decoded.length != 32) {
          if (!isTestProfile) {
            throw new IllegalStateException("FAIL-CLOSED: AES_SECRET_KEY must be exactly 32 bytes (256-bit) decoded, got: " + decoded.length);
          }
        }
      } catch (IllegalArgumentException ex) {
        if (!isTestProfile) {
          throw new IllegalStateException("FAIL-CLOSED: AES_SECRET_KEY is not a valid base64 string", ex);
        }
      }
    }

    if (!isTestProfile && (authApiKey == null || authApiKey.isBlank())) {
      throw new IllegalStateException("FAIL-CLOSED: AUTH_INTERNAL_API_KEY is mandatory");
    }
    if (!isTestProfile && (accountApiKey == null || accountApiKey.isBlank())) {
      throw new IllegalStateException("FAIL-CLOSED: ACCOUNT_INTERNAL_API_KEY is mandatory");
    }

    // 2. NAPAS Provider validation
    if (!"mock".equalsIgnoreCase(napasProvider)) {
      if (napasWebhookSecret == null || napasWebhookSecret.isBlank()) {
        throw new IllegalStateException("FAIL-CLOSED: NAPAS_WEBHOOK_SECRET is mandatory when NAPAS provider is enabled");
      }
    }

    // 3. SePay validation
    if (sepayEnabled) {
      if (sepayApiKey == null || sepayApiKey.isBlank()) {
        throw new IllegalStateException("FAIL-CLOSED: SEPAY_API_KEY is mandatory when SePay is enabled");
      }
    }

    // 4. Mock Provider validation
    if (mockVaEnabled) {
      if (activeProfile != null && activeProfile.contains("prod")) {
        throw new IllegalStateException("FAIL-CLOSED: Mock VA provider cannot be enabled in production environment");
      }
      if (mockVaSandboxKey == null || mockVaSandboxKey.isBlank()) {
        throw new IllegalStateException("FAIL-CLOSED: MOCK_VA_SANDBOX_KEY is mandatory when Mock VA is enabled");
      }
    }

    log.info("[SECURITY-VALIDATOR] All required security configurations validated successfully");
  }
}
