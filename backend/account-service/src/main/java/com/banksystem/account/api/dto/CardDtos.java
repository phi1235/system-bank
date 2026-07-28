package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Virtual debit card API payloads. PAN is returned masked except on explicit reveal. */
public final class CardDtos {

  private CardDtos() {}

  public record CardResponse(
      String id,
      String accountId,
      String accountNumber,
      /** Masked (9704 **** **** 1234); NULL while the request awaits approval. */
      String maskedPan,
      String brand,
      String status,
      BigDecimal dailyLimit,
      LocalDate expiresOn,
      /** Set only when status is REJECTED. */
      String rejectReason,
      Instant createdAt) {}

  public record RejectCardRequest(@NotBlank @Size(max = 255) String reason) {}

  /** Approval-queue row enriched with owner name and account number for staff. */
  public record AdminCardRow(
      String id,
      String userId,
      String ownerName,
      String accountId,
      String accountNumber,
      String status,
      BigDecimal dailyLimit,
      String rejectReason,
      Instant requestedAt) {}

  /** Owner-only, ACTIVE card only; the single place the full PAN leaves the service. */
  public record CardRevealResponse(String id, String pan, LocalDate expiresOn) {}

  public record UpdateCardLimitRequest(
      @NotNull @DecimalMin("0") BigDecimal dailyLimit) {}

  public record BatchApproveRequest(@NotNull @Size(min = 1, max = 100) java.util.List<java.util.UUID> ids) {}

  public record BatchApproveResult(int approvedCount, int failedCount, java.util.List<String> errors) {}
}
