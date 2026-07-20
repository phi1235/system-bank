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
      String currency
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
      Instant createdAt
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
