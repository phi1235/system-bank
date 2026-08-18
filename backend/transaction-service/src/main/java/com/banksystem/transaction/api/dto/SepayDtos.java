package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SepayDtos {

  private SepayDtos() {}

  public record CreateTopUpRequest(
      @NotBlank(message = "Account number is required")
      String accountNumber,

      @NotNull(message = "Amount is required")
      @DecimalMin(value = "1000.00", message = "Minimum top-up amount is 1,000 VND")
      BigDecimal amount,

      String note
  ) {}

  public record TopUpOrderResponse(
      UUID id,
      String orderCode,
      String accountNumber,
      BigDecimal amount,
      String status,
      String vietQrUrl,
      String bankName,
      String bankAccount,
      String accountName,
      String transferContent,
      Instant createdAt,
      Instant expiresAt,
      Instant completedAt
  ) {}

  public record SepayWebhookPayload(
      Long id,
      String gateway,
      String transactionDate,
      String accountNumber,
      String code,
      String content,
      String transferType,
      BigDecimal transferAmount,
      BigDecimal accumulated,
      String subAccount,
      String referenceCode,
      String description
  ) {}

  public record SepayWebhookResponse(
      boolean success,
      String message
  ) {}

  public record SepayOrderStatusResponse(
      String orderCode,
      String status,
      BigDecimal amount,
      String accountNumber,
      Instant completedAt
  ) {}
}
