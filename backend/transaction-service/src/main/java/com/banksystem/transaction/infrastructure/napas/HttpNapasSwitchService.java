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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
  private final Duration requestTimeout;

  public HttpNapasSwitchService(
      @Value("${bank.napas.base-url}") String baseUrl,
      @Value("${bank.napas.api-key}") String apiKey,
      @Value("${bank.napas.connect-timeout-ms}") long connectTimeoutMs,
      @Value("${bank.napas.request-timeout-ms}") long requestTimeoutMs,
      ObjectMapper objectMapper) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("bank.napas.base-url (NAPAS_BASE_URL) must be set when provider=http");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey == null ? "" : apiKey;
    this.objectMapper = objectMapper;
    this.requestTimeout = Duration.ofMillis(Math.max(500, requestTimeoutMs));
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
        .build();
  }

  @Override
  @Retry(name = "NAPAS_READ")
  @CircuitBreaker(name = "NAPAS")
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
  @CircuitBreaker(name = "NAPAS")
  public NapasPaymentResponse executePayment(
      String sourceAccountNumber,
      String targetBankCode,
      String targetAccountNumber,
      BigDecimal amount,
      String description,
      String clientRequestId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sourceAccountNumber", sourceAccountNumber);
    payload.put("targetBankCode", targetBankCode);
    payload.put("targetAccountNumber", targetAccountNumber);
    payload.put("amount", amount);
    payload.put("description", description);
    payload.put("clientRequestId", clientRequestId);
    JsonNode root = postJson("/v1/payment", payload);
    return paymentResponse(root);
  }

  @Override
  @Retry(name = "NAPAS_READ")
  @CircuitBreaker(name = "NAPAS")
  public NapasPaymentResponse inquirePayment(String clientRequestId, String napasRefId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clientRequestId", clientRequestId);
    payload.put("napasRefId", napasRefId);
    return paymentResponse(postJson("/v1/payment/status", payload));
  }

  private NapasPaymentResponse paymentResponse(JsonNode root) {
    String rawStatus = text(root, "status");
    String code = text(root, "responseCode");
    boolean success = root.path("success").asBoolean("00".equals(code));
    String ref = text(root, "napasRefId");
    if (ref == null) {
      ref = text(root, "referenceId");
    }
    if (code == null) {
      code = success ? "00" : "UNKNOWN";
    }
    String msg = text(root, "responseMessage");
    if (msg == null) {
      msg = success ? "SUCCESS" : "FAILED";
    }
    ProviderOutcome outcome = mapOutcome(rawStatus, code, success);
    return new NapasPaymentResponse(ref, outcome, code, msg);
  }

  private static ProviderOutcome mapOutcome(String status, String code, boolean success) {
    if (success || "00".equals(code) || "SUCCESS".equalsIgnoreCase(status)
        || "COMPLETED".equalsIgnoreCase(status)) {
      return ProviderOutcome.SUCCESS;
    }
    if ("PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)
        || "ACCEPTED".equalsIgnoreCase(status)) {
      return ProviderOutcome.PENDING;
    }
    if ("FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)
        || (code != null && !code.isBlank() && !"UNKNOWN".equalsIgnoreCase(code))) {
      return ProviderOutcome.FAILED;
    }
    return ProviderOutcome.UNKNOWN;
  }

  private JsonNode postJson(String path, Map<String, Object> payload) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      HttpRequest.Builder b =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .timeout(requestTimeout)
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
