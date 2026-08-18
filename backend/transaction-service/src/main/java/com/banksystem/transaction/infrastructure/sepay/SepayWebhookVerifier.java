package com.banksystem.transaction.infrastructure.sepay;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.config.SepayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SepayWebhookVerifier {

  private final SepayProperties properties;

  public SepayWebhookVerifier(SepayProperties properties) {
    this.properties = properties;
  }

  public void verify(String authHeader) {
    String configuredKey = properties.getApiKey();
    if (configuredKey == null || configuredKey.isBlank()) {
      return;
    }

    if (authHeader == null || authHeader.isBlank()) {
      throw new BusinessException("UNAUTHORIZED", "Missing SePay Authorization header", HttpStatus.UNAUTHORIZED);
    }

    String token = authHeader.trim();
    if (token.startsWith("Apikey ")) {
      token = token.substring(7).trim();
    } else if (token.startsWith("Bearer ")) {
      token = token.substring(7).trim();
    }

    byte[] supplied = token.getBytes(StandardCharsets.UTF_8);
    byte[] expected = configuredKey.trim().getBytes(StandardCharsets.UTF_8);

    if (!MessageDigest.isEqual(supplied, expected)) {
      throw new BusinessException("UNAUTHORIZED", "Invalid SePay API Key", HttpStatus.UNAUTHORIZED);
    }
  }
}
