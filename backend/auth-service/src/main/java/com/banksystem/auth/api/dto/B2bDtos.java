package com.banksystem.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class B2bDtos {

  private B2bDtos() {}

  /* ── OAuth2 / FAPI DTOs ── */

  public record OAuth2TokenRequest(
      @JsonProperty("grant_type") String grantType,
      @JsonProperty("client_assertion_type") String clientAssertionType,
      @JsonProperty("client_assertion") String clientAssertion,
      @JsonProperty("client_id") String clientId,
      String scope) {}

  public record OAuth2TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn,
      String scope,
      Map<String, String> cnf) {}

  public record JwksKeyDto(
      String kty,
      String use,
      String alg,
      String kid,
      String n,
      String e) {}

  public record JwksResponse(
      List<JwksKeyDto> keys) {}

  /* ── B2B Client Application Management DTOs ── */

  public record B2bClientCreateRequest(
      @NotBlank @Size(max = 64) String clientId,
      @NotBlank @Size(max = 255) String clientName,
      @NotBlank @Size(max = 32) String organizationTaxCode,
      String allowedScopes,
      String publicKeyPem,
      String clientCertThumbprintSha256,
      String webhookCallbackUrl,
      String webhookSecret,
      @Min(1) @Max(10000) Integer rateLimitRpm) {}

  public record B2bClientUpdateRequest(
      @Size(max = 255) String clientName,
      @Size(max = 32) String organizationTaxCode,
      String status,
      String allowedScopes,
      String publicKeyPem,
      String clientCertThumbprintSha256,
      String webhookCallbackUrl,
      String webhookSecret,
      @Min(1) @Max(10000) Integer rateLimitRpm) {}

  public record B2bClientResponse(
      UUID id,
      String clientId,
      String clientName,
      String organizationTaxCode,
      String status,
      String allowedGrantTypes,
      String allowedScopes,
      String tokenEndpointAuthMethod,
      String jwksUri,
      String publicKeyPem,
      String clientCertThumbprintSha256,
      String webhookCallbackUrl,
      int rateLimitRpm,
      Instant createdAt,
      Instant updatedAt) {}

  public record B2bClientFilterRequest(
      String q,
      String status,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size) {}

  /* ── B2B Account Consent DTOs ── */

  public record B2bConsentCreateRequest(
      @NotBlank String clientId,
      @NotBlank String accountNumber,
      UUID customerId,
      String permissions,
      Instant validUntil) {}

  public record B2bConsentResponse(
      UUID id,
      String clientId,
      String accountNumber,
      UUID customerId,
      String permissions,
      String status,
      Instant validUntil,
      Instant createdAt) {}

  public record B2bConsentFilterRequest(
      String clientId,
      UUID customerId,
      String status,
      String accountNumber,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size) {}

  public record B2bConsentCheckRequest(
      @NotBlank String clientId,
      @NotBlank String accountNumber,
      String permission) {}

  /* ── B2B Sandbox & Metrics DTOs ── */

  public record B2bSandboxExecuteRequest(
      @NotBlank String clientId,
      @NotBlank String messageType,
      String format,
      @NotBlank String payload) {}

  public record B2bSandboxExecuteResponse(
      String messageId,
      String status,
      String responsePayload,
      long executionTimeMs,
      boolean fapiVerified) {}

  public record B2bMetricSummary(
      String clientId,
      long totalRequests,
      long successfulRequests,
      long failedRequests,
      double avgLatencyMs,
      int rateLimitRpm) {}
}
