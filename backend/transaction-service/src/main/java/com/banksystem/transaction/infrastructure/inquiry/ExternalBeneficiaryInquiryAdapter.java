package com.banksystem.transaction.infrastructure.inquiry;

import com.banksystem.transaction.application.transfer.BeneficiaryInquiryPort;
import com.banksystem.transaction.application.transfer.BeneficiaryProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.inquiry.provider", havingValue = "external")
public class ExternalBeneficiaryInquiryAdapter implements BeneficiaryInquiryPort {

  private record VietQrLookupRequest(long bin, String accountNumber) {}

  private static final Logger log = LoggerFactory.getLogger(ExternalBeneficiaryInquiryAdapter.class);

  private final String baseUrl;
  private final String clientId;
  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Duration requestTimeout;

  public ExternalBeneficiaryInquiryAdapter(
      @Value("${bank.inquiry.external.base-url}") String baseUrl,
      @Value("${bank.inquiry.external.client-id}") String clientId,
      @Value("${bank.inquiry.external.api-key}") String apiKey,
      @Value("${bank.inquiry.external.timeout-ms}") long timeoutMs,
      ObjectMapper objectMapper) {
    this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.clientId = clientId != null ? clientId : "";
    this.apiKey = apiKey != null ? apiKey : "";
    if (this.baseUrl == null || this.baseUrl.isBlank()) {
      throw new IllegalArgumentException("BANK_INQUIRY_EXTERNAL_BASE_URL is required");
    }
    if (this.clientId.isBlank() || this.apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "BANK_INQUIRY_EXTERNAL_CLIENT_ID and BANK_INQUIRY_EXTERNAL_API_KEY are required for external provider");
    }
    this.requestTimeout = Duration.ofMillis(Math.max(500, timeoutMs));
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(2000))
        .build();
  }

  @Override
  @RateLimiter(name = "BENEFICIARY_INQUIRY")
  @CircuitBreaker(name = "BENEFICIARY_INQUIRY")
  @Retry(name = "BENEFICIARY_INQUIRY_RETRY")
  public InquiryResult inquire(String bankBin, String accountNumber) {
    if (bankBin == null || bankBin.isBlank() || accountNumber == null || accountNumber.isBlank()) {
      return InquiryResult.failure(bankBin, accountNumber, "VIETQR", "INVALID_ACCOUNT", "Bank BIN and account number are required");
    }
    String cleanNum = accountNumber.trim();
    String cleanBin = bankBin.trim();

    try {
      long numericBin;
      try {
        numericBin = Long.parseLong(cleanBin);
      } catch (NumberFormatException ex) {
        return InquiryResult.failure(cleanBin, cleanNum, "VIETQR", "BANK_NOT_SUPPORTED", "Bank BIN must be numeric");
      }
      VietQrLookupRequest requestBody = new VietQrLookupRequest(numericBin, cleanNum);
      String jsonPayload = objectMapper.writeValueAsString(requestBody);

      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/v2/lookup"))
          .timeout(requestTimeout)
          .header("Content-Type", "application/json");

      if (clientId != null && !clientId.isBlank()) {
        reqBuilder.header("x-client-id", clientId);
      }
      if (apiKey != null && !apiKey.isBlank()) {
        reqBuilder.header("x-api-key", apiKey);
      }

      HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 429) {
        log.warn("External beneficiary lookup rate limited (429) for bin={}", cleanBin);
        return InquiryResult.failure(cleanBin, cleanNum, "VIETQR", "INQUIRY_RATE_LIMITED", "Provider rate limit reached");
      }

      if (response.statusCode() == 404) {
        return InquiryResult.failure(cleanBin, cleanNum, "VIETQR", "BENEFICIARY_NOT_FOUND", "Account not found at beneficiary bank");
      }

      if (response.statusCode() == 401 || response.statusCode() == 403) {
        log.error("External beneficiary lookup authentication failed with HTTP {}", response.statusCode());
        throw new BeneficiaryProviderException("Provider authentication failed");
      }

      if (response.statusCode() >= 400 && response.statusCode() < 500) {
        log.warn("External beneficiary lookup client error {} for bin={}", response.statusCode(), cleanBin);
        return InquiryResult.failure(cleanBin, cleanNum, "VIETQR", "INVALID_ACCOUNT_FORMAT", "Invalid account format or bank not supported");
      }

      if (response.statusCode() != 200) {
        log.error("External beneficiary lookup returned HTTP {} from provider", response.statusCode());
        throw new BeneficiaryProviderException("Provider returned HTTP " + response.statusCode());
      }

      JsonNode root = objectMapper.readTree(response.body());
      String code = root.path("code").asText("");
      if ("00".equals(code)) {
        String accountName = root.path("data").path("accountName").asText();
        if (accountName == null || accountName.isBlank()) {
          accountName = root.path("data").path("accountHolderName").asText();
        }
        if (accountName != null && !accountName.isBlank()) {
          return InquiryResult.success(cleanBin, cleanNum, accountName.trim(), "VIETQR");
        }
      }

      String desc = root.path("desc").asText("Account not found");
      return InquiryResult.failure(cleanBin, cleanNum, "VIETQR", "BENEFICIARY_NOT_FOUND", desc);

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BeneficiaryProviderException("Beneficiary inquiry was interrupted", ex);
    } catch (BeneficiaryProviderException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BeneficiaryProviderException("Beneficiary provider request failed", ex);
    }
  }

  @Override
  public boolean supports(String bankBin) {
    return bankBin != null && !bankBin.isBlank();
  }
}
