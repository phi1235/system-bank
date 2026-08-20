package com.banksystem.corporate.application.signature;

import com.banksystem.common.exception.BusinessException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "bank.ca.provider", havingValue = "http")
public class HttpCaSignatureProvider implements TransactionSignatureProvider {

  private final RestClient client;

  public HttpCaSignatureProvider(
      RestClient.Builder builder,
      @Value("${bank.ca.http.base-url}") String baseUrl,
      @Value("${bank.ca.http.api-key}") String apiKey) {
    this.client = builder
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .build();
  }

  @Override
  public SignatureChallengeResult createChallenge(String payloadHash) {
    try {
      CaChallengeResponse response = client.post()
          .uri("/v1/signature-challenges")
          .body(new CaChallengeRequest(payloadHash))
          .retrieve()
          .body(CaChallengeResponse.class);
      if (response == null || response.nonce() == null || response.nonce().isBlank()) {
        throw unavailable("CA provider returned an empty challenge");
      }
      return new SignatureChallengeResult(
          response.nonce(), payloadHash, Math.max(1, response.ttlSeconds()));
    } catch (RestClientException exception) {
      throw unavailable(exception.getMessage());
    }
  }

  @Override
  public SignatureVerificationResult verifySignature(
      String challengeNonce,
      String signatureToken,
      String payloadHash) {
    try {
      CaVerificationResponse response = client.post()
          .uri("/v1/signature-verifications")
          .body(new CaVerificationRequest(challengeNonce, signatureToken, payloadHash))
          .retrieve()
          .body(CaVerificationResponse.class);
      if (response == null) {
        throw unavailable("CA provider returned an empty verification response");
      }
      return new SignatureVerificationResult(
          response.valid(),
          response.certificateSubject(),
          response.certificateIssuer(),
          response.certificateSerial(),
          response.signatureAlgorithm(),
          response.failureReason());
    } catch (RestClientException exception) {
      throw unavailable(exception.getMessage());
    }
  }

  private BusinessException unavailable(String detail) {
    return new BusinessException(
        "CA_PROVIDER_UNAVAILABLE",
        "Digital signature provider is unavailable: " + Objects.toString(detail, "unknown error"));
  }

  private record CaChallengeRequest(String payloadHash) {}
  private record CaChallengeResponse(String nonce, long ttlSeconds) {}
  private record CaVerificationRequest(
      String challengeNonce,
      String signatureToken,
      String payloadHash) {}
  private record CaVerificationResponse(
      boolean valid,
      String certificateSubject,
      String certificateIssuer,
      String certificateSerial,
      String signatureAlgorithm,
      String failureReason) {}
}
