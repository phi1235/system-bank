package com.banksystem.transaction.infrastructure.napas;

import com.banksystem.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * HTTP NAPAS gateway adapter. Point {@code NAPAS_BASE_URL} at a real switch or sandbox reverse-proxy.
 * Auth header is optional (API key / partner token from env only).
 */
@Service
@ConditionalOnProperty(name = "bank.napas.provider", havingValue = "http")
public class HttpNapasSwitchService implements NapasSwitchClient {

  private static final Logger log = LoggerFactory.getLogger(HttpNapasSwitchService.class);

  private final String baseUrl;
  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public HttpNapasSwitchService(
      @Value("${bank.napas.base-url}") String baseUrl,
      @Value("${bank.napas.api-key:}") String apiKey,
      ObjectMapper objectMapper) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("bank.napas.base-url (NAPAS_BASE_URL) must be set when provider=http");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey == null ? "" : apiKey;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public NapasInquiryResponse inquireAccount(String bankCode, String accountNumber) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bankCode", bankCode);
    payload.put("accountNumber", accountNumber);
    JsonNode root = postJson("/v1/inquiry", payload);
    boolean valid = root.path("valid").asBoolean(root.path("success").asBoolean(false));
    String name = text(root, "accountName");
    if (name == null) {
      name = text(root, "accountHolderName");
    }
    if (name == null) {
      name = "";
    }
    return new NapasInquiryResponse(bankCode, accountNumber, name, valid);
  }

  @Override
  public NapasPaymentResponse executePayment(
      String sourceAccountNumber,
      String targetBankCode,
      String targetAccountNumber,
      BigDecimal amount,
      String description) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sourceAccountNumber", sourceAccountNumber);
    payload.put("targetBankCode", targetBankCode);
    payload.put("targetAccountNumber", targetAccountNumber);
    payload.put("amount", amount);
    payload.put("description", description);
    JsonNode root = postJson("/v1/payment", payload);
    boolean success = root.path("success").asBoolean("00".equals(text(root, "responseCode")));
    String ref = text(root, "napasRefId");
    if (ref == null) {
      ref = text(root, "referenceId");
    }
    String code = text(root, "responseCode");
    if (code == null) {
      code = success ? "00" : "99";
    }
    String msg = text(root, "responseMessage");
    if (msg == null) {
      msg = success ? "SUCCESS" : "FAILED";
    }
    return new NapasPaymentResponse(ref, success, code, msg);
  }

  private JsonNode postJson(String path, Map<String, Object> payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      HttpRequest.Builder b =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json));
      if (!apiKey.isBlank()) {
        b.header("X-API-Key", apiKey);
        b.header("Authorization", "Bearer " + apiKey);
      }
      HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 400) {
        log.warn("NAPAS HTTP {} path={} body={}", resp.statusCode(), path, truncate(resp.body()));
        throw new BusinessException(
            "NAPAS_UPSTREAM_ERROR",
            "NAPAS gateway returned HTTP " + resp.statusCode(),
            HttpStatus.BAD_GATEWAY);
      }
      if (resp.body() == null || resp.body().isBlank()) {
        throw new BusinessException("NAPAS_EMPTY", "Empty NAPAS response", HttpStatus.BAD_GATEWAY);
      }
      return objectMapper.readTree(resp.body());
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("NAPAS HTTP call failed path={}: {}", path, ex.getMessage());
      throw new BusinessException(
          "NAPAS_UNAVAILABLE", "NAPAS gateway unavailable: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }
  }

  private static String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    String s = v.asText();
    return s == null || s.isBlank() ? null : s;
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 300 ? s.substring(0, 300) + "..." : s;
  }
}
