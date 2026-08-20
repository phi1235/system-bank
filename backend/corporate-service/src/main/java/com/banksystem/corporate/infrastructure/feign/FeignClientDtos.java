package com.banksystem.corporate.infrastructure.feign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FeignClientDtos {
  private FeignClientDtos() {}

  public record AccountDto(
      String id,
      String userId,
      String ownerType,
      String ownerId,
      String accountNumber,
      String accountType,
      String currency,
      BigDecimal balance,
      String status
  ) {}

  public record AccountOwnershipDto(
      UUID id,
      String accountNumber,
      String ownerType,
      UUID ownerId,
      String status,
      String currency
  ) {}

  public record CreateCorporateAccountReq(
      UUID commandId,
      UUID corporateId,
      UUID createdByUserId,
      String accountType,
      String currency
  ) {}

  public record CreateBatchHoldReq(
      UUID batchId,
      String commandId,
      BigDecimal amount,
      String currency,
      Instant expiresAt
  ) {}

  public record PartialCaptureHoldReq(
      String commandId,
      BigDecimal amount
  ) {}

  public record HoldActionReq(
      String commandId,
      UUID journalId
  ) {}

  public record HoldResult(
      UUID id,
      UUID accountId,
      UUID transactionId,
      UUID batchId,
      BigDecimal amount,
      BigDecimal originalAmount,
      BigDecimal capturedAmount,
      BigDecimal releasedAmount,
      String currency,
      String status,
      Instant expiresAt
  ) {}

  public record CorporatePayoutTransferReq(
      UUID corporateId,
      UUID batchId,
      UUID batchItemId,
      UUID holdId,
      UUID initiatedBy,
      int executionVersion,
      String idempotencyKey,
      UUID fromAccountId,
      String toAccountNumber,
      BigDecimal amount,
      String description,
      String currency,
      String transferType,
      String targetBankCode,
      String targetAccountName
  ) {}

  public record TransferResult(
      String transactionId,
      String status,
      String failureReason
  ) {}

  public record VerifyTotpReq(
      String code
  ) {}

  public record VerifyTotpRes(
      boolean valid
  ) {}

  public record CreateNotificationLogRequest(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      UUID userId,
      String audience,
      String actionType,
      String actionId,
      String actionPath
  ) {}

  public record SendEmailRequest(
      String recipient,
      String subject,
      String body,
      String attachmentFilename,
      String attachmentContentBase64
  ) {}

  public record QueueEmailRequest(
      UUID eventId,
      String recipient,
      String subject,
      String body,
      String attachmentFilename,
      String attachmentContentBase64
  ) {}
}
