package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MerchantDtos {
  private MerchantDtos() {}

  public record ConfigureMerchantAccountRequest(
      @NotNull UUID collectionAccountId,
      @NotNull UUID escrowAccountId,
      String defaultCurrency
  ) {}

  public record MerchantAccountResponse(
      UUID id,
      UUID organizationId,
      UUID collectionAccountId,
      UUID escrowAccountId,
      UUID commissionAccountId,
      String defaultCurrency,
      String status,
      Instant createdAt
  ) {}

  public record CreateApiCredentialRequest(
      @NotBlank String name,
      Instant expiresAt
  ) {}

  public record ApiCredentialCreatedResponse(
      UUID id,
      String keyId,
      String secretKey,
      String name,
      String status,
      Instant expiresAt,
      Instant createdAt
  ) {}

  public record ApiCredentialResponse(
      UUID id,
      String keyId,
      String name,
      String status,
      Instant expiresAt,
      Instant lastUsedAt,
      Instant createdAt
  ) {}

  public record RegisterWebhookEndpointRequest(
      @NotBlank String url,
      String eventTypes
  ) {}

  public record WebhookEndpointCreatedResponse(
      UUID id,
      String url,
      String eventTypes,
      String secretKey,
      String status,
      Instant createdAt
  ) {}

  public record WebhookEndpointResponse(
      UUID id,
      String url,
      String eventTypes,
      String status,
      Instant createdAt
  ) {}

  public record BusinessDashboardSummaryResponse(
      long totalVirtualAccounts,
      long activeVirtualAccounts,
      long pendingOrdersCount,
      long paidOrdersCount,
      long reviewOrdersCount,
      BigDecimal totalCollectedToday,
      BigDecimal totalSettledToday,
      long pendingSettlementsCount,
      double autoMatchRate
  ) {}
}
