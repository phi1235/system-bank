package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransferDtos {
  private TransferDtos() {}

  public record TransferRequest(
      @NotNull UUID fromAccountId,
      @NotBlank String toAccountNumber,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      String description,
      String currency,
      String transferType,
      String targetBankCode,
      String targetAccountName
  ) {}

  public record TransferResponse(
      String transactionId,
      String status,
      String fromAccountId,
      String toAccountId,
      String toAccountNumber,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency,
      String description,
      String failureReason,
      Instant createdAt,
      String transferType,
      String targetBankCode,
      String targetAccountName
  ) {}

  /**
   * Pre-transfer quote for customer UX: fee preview + remaining daily limit.
   * Does not create an order; read-only.
   *
   * <p>Fee breakdown (flat + percent + min/max clamp) is exposed so the customer app can
   * show how the fee was computed without hardcoding formula on the FE.
   */
  public record TransferQuoteResponse(
      BigDecimal amount,
      BigDecimal feeAmount,
      BigDecimal totalDebit,
      BigDecimal maxPerTransaction,
      BigDecimal dailyLimit,
      BigDecimal spentToday,
      BigDecimal remainingToday,
      String currency,
      String dailyLimitZone,
      boolean feeEnabled,
      /** Config flat fee component (VND). */
      BigDecimal feeFlat,
      /** Config percent of principal (e.g. 0.1 = 0.1%). */
      BigDecimal feePercent,
      /** Computed percent portion for this amount (before min/max clamp). */
      BigDecimal feePercentAmount,
      BigDecimal feeMin,
      BigDecimal feeMax,
      /** flat + percentAmount before min/max clamp. */
      BigDecimal feeRawBeforeClamp,
      boolean feeCappedByMin,
      boolean feeCappedByMax
  ) {}

  /** Single saga step log line for transfer lifecycle visibility. */
  public record SagaStepResponse(
      String id,
      String step,
      String status,
      String detail,
      Instant createdAt
  ) {}

  /** Transfer order + ordered saga steps (ops / customer detail). */
  public record TransferDetailResponse(
      TransferResponse transfer,
      List<SagaStepResponse> steps
  ) {}

  public record AuditResponse(
      String id,
      String actorUserId,
      String action,
      String resourceType,
      String resourceId,
      String ip,
      String metadata,
      Instant createdAt
  ) {}
}
