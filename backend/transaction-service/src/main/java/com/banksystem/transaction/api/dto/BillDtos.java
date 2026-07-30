package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class BillDtos {
  private BillDtos() {}

  public record BillCategoryResponse(
      String id,
      String name,
      String iconUrl,
      String icon,
      String sampleCode,
      String themeClass,
      int displayOrder
  ) {}

  public record BillProviderResponse(
      String id,
      String categoryId,
      String name,
      String code
  ) {}

  public record BillInquiryRequest(
      @NotBlank String providerId,
      @NotBlank String customerCode
  ) {}

  public record BillInquiryResponse(
      String customerName,
      BigDecimal amount,
      String period,
      String providerId,
      String customerCode
  ) {}

  public record BillPayRequest(
      @NotBlank String providerId,
      @NotBlank String customerCode,
      @NotNull @DecimalMin("1") BigDecimal amount
  ) {}

  public record BillPayResponse(
      UUID paymentId,
      String status,
      String transactionRef,
      BigDecimal amount,
      BigDecimal fee,
      Instant createdAt
  ) {}

  public record BillPaymentHistoryResponse(
      UUID id,
      String categoryId,
      String providerId,
      String customerCode,
      String customerName,
      BigDecimal amount,
      BigDecimal fee,
      String status,
      String transactionRef,
      Instant createdAt
  ) {}
}
