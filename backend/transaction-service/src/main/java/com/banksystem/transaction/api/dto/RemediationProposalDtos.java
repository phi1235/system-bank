package com.banksystem.transaction.api.dto;

import com.banksystem.transaction.domain.forensics.AdjustmentDirection;
import com.banksystem.transaction.domain.forensics.RemediationProposalStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class RemediationProposalDtos {

  public record CreateRemediationProposalRequest(
      @NotNull UUID caseId,
      UUID sourceTransactionId,
      @NotNull UUID targetAccountId,
      @NotNull AdjustmentDirection direction,
      @NotNull @Positive BigDecimal amount,
      @Size(max = 10) String currency,
      @NotBlank @Size(max = 2000) String reason) {}

  public record UpdateRemediationProposalRequest(
      @NotNull @Min(0) long expectedVersion,
      @NotNull UUID targetAccountId,
      @NotNull AdjustmentDirection direction,
      @NotNull @Positive BigDecimal amount,
      @Size(max = 10) String currency,
      @NotBlank @Size(max = 2000) String reason) {}

  public record RejectRemediationProposalRequest(
      @NotNull @Min(0) long expectedVersion,
      @NotBlank @Size(max = 1000) String reason) {}

  public record RemediationProposalResponse(
      String id,
      String caseId,
      int investigationCycle,
      String sourceTransactionId,
      String targetAccountId,
      String direction,
      BigDecimal amount,
      String currency,
      String reason,
      String proposalPayloadHash,
      String executionReferenceId,
      RemediationProposalStatus status,
      String proposedBy,
      String checkerId,
      Instant submittedAt,
      Instant approvedAt,
      Instant rejectedAt,
      String rejectionReason,
      String failureReason,
      Instant createdAt,
      Instant updatedAt,
      long version) {}
}
