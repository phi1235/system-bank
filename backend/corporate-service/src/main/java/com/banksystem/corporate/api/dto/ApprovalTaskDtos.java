package com.banksystem.corporate.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApprovalTaskDtos {
  private ApprovalTaskDtos() {}

  public record ApprovalTaskResponse(
      UUID id,
      UUID instanceId,
      UUID batchId,
      String batchName,
      UUID corporateId,
      int stepOrder,
      String stepName,
      String requiredRole,
      int minApprovals,
      int currentApprovals,
      String authMethod,
      String status,
      Instant deadline,
      BigDecimal totalAmount,
      int totalItems,
      String currency,
      Instant createdAt
  ) {}

  public record ApprovalActionRequest(
      String comments,
      String challengeNonce,
      String authCode, // TOTP code or CA signature token
      String signatureReference
  ) {}

  public record RejectActionRequest(
      @NotBlank String reason
  ) {}

  public record ReturnActionRequest(
      @NotBlank String reason
  ) {}

  public record CreateChallengeResponse(
      UUID challengeId,
      String nonce,
      String challengeType,
      String payloadHash,
      Instant expiresAt
  ) {}

  public record ApprovalActionResponse(
      UUID id,
      UUID taskId,
      UUID batchId,
      UUID actorId,
      String actorRole,
      String action,
      String comments,
      Instant actionTimestamp
  ) {}

  public record ApprovalInstanceDetailResponse(
      UUID id,
      UUID batchId,
      int policyVersion,
      int totalSteps,
      int currentStep,
      String status,
      List<ApprovalTaskResponse> tasks,
      List<ApprovalActionResponse> actions
  ) {}
}
