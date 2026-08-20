package com.banksystem.transaction.api.dto;

import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollectionDtos {
  private CollectionDtos() {}

  public record CreateCollectionOrderRequest(
      @NotBlank String merchantOrderId,
      UUID virtualAccountId,
      VirtualAccountMode vaMode,
      @NotNull @DecimalMin(value = "0.01") BigDecimal expectedAmount,
      String currency,
      String customerReference,
      UUID splitRuleId,
      List<SettlementDtos.SplitLegRequest> splitLegs,
      Instant expiresAt
  ) {}

  public record CollectionOrderResponse(
      UUID id,
      UUID organizationId,
      String merchantOrderId,
      UUID virtualAccountId,
      String virtualAccountNumber,
      String bankBin,
      String vietQrUrl,
      BigDecimal expectedAmount,
      BigDecimal paidAmount,
      String currency,
      CollectionOrderStatus status,
      String customerReference,
      String splitRuleSnapshot,
      Instant expiresAt,
      Instant paidAt,
      Instant createdAt
  ) {}

  public record InboundPaymentEventResponse(
      UUID id,
      String provider,
      String providerTransactionId,
      String virtualAccountNumber,
      String bankBin,
      BigDecimal amount,
      String currency,
      String senderAccount,
      String senderBankBin,
      String senderName,
      String referenceContent,
      InboundPaymentStatus status,
      String errorMessage,
      Instant processedAt,
      Instant createdAt
  ) {}

  public record WebhookProcessingResult(
      boolean success,
      String message,
      String status,
      UUID orderId,
      String orderStatus
  ) {}

  public record CollectionOrderFilterRequest(
      String q,
      CollectionOrderStatus status,
      Integer page,
      Integer size
  ) {}

  public record AdminCollectionOrderFilterRequest(
      UUID organizationId,
      String q,
      CollectionOrderStatus status,
      Integer page,
      Integer size
  ) {}

  public record InboundPaymentFilterRequest(
      String provider,
      String q,
      InboundPaymentStatus status,
      Integer page,
      Integer size
  ) {}
}
