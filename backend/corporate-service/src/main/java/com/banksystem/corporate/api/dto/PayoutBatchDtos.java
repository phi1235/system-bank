package com.banksystem.corporate.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PayoutBatchDtos {
  private PayoutBatchDtos() {}

  public record CreateBatchRequest(
      @NotNull UUID sourceAccountId,
      @NotBlank String sourceAccountNumber,
      @NotBlank String batchName,
      String currency
  ) {}

  public record PayoutPageRequest(
      @Min(0) Integer page,
      @Min(1) @Max(200) Integer size
  ) {}

  public record CancelBatchRequest(@NotBlank String reason) {}

  public record BatchSummaryResponse(
      UUID id,
      UUID corporateId,
      UUID sourceAccountId,
      String sourceAccountNumber,
      String batchName,
      int totalItems,
      int validItems,
      int invalidItems,
      int processedItems,
      int successfulItems,
      int failedItems,
      BigDecimal totalAmount,
      BigDecimal totalFee,
      String currency,
      String status,
      String fileSha256,
      UUID policyId,
      Integer policyVersion,
      String canonicalPayloadHash,
      UUID holdId,
      UUID createdBy,
      UUID submittedBy,
      Instant submittedAt,
      Instant approvedAt,
      Instant startedAt,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record PayoutItemResponse(
      UUID id,
      UUID batchId,
      int rowNumber,
      String employeeCode,
      String beneficiaryName,
      String accountNumber,
      String bankCode,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency,
      String description,
      String employeeEmail,
      String payrollPeriod,
      String status,
      String validationError,
      UUID transactionId,
      String idempotencyKey,
      int executionVersion,
      int retryCount,
      String failureReason,
      UUID receiptArtifactId,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record BatchValidationSummaryResponse(
      UUID batchId,
      int totalRows,
      int validRows,
      int invalidRows,
      BigDecimal totalAmount,
      boolean canSubmit,
      List<String> blockingErrors
  ) {}

  public record BatchProgressResponse(
      UUID batchId,
      String status,
      int totalItems,
      int processedItems,
      int successfulItems,
      int failedItems,
      double progressPercentage,
      BigDecimal processedAmount,
      BigDecimal totalAmount
  ) {}

  public record ReceiptArtifactResponse(
      UUID id,
      UUID corporateId,
      UUID batchId,
      UUID itemId,
      String artifactType,
      String fileKey,
      String fileSha256,
      long fileSizeBytes,
      boolean emailSent,
      Instant emailSentAt,
      Instant createdAt
  ) {}
}
