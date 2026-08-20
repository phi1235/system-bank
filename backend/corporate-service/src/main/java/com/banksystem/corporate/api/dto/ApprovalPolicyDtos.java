package com.banksystem.corporate.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApprovalPolicyDtos {
  private ApprovalPolicyDtos() {}

  public record CreateApprovalStepTemplateRequest(
      int stepOrder,
      @NotBlank String stepName,
      @NotBlank String requiredRole,
      int minApprovals,
      String authMethod,
      Integer deadlineHours
  ) {}

  public record CreateApprovalTierRequest(
      @NotBlank String tierName,
      @NotNull @DecimalMin("0.00") BigDecimal minAmount,
      BigDecimal maxAmount,
      int priorityOrder,
      @NotEmpty List<@Valid CreateApprovalStepTemplateRequest> steps
  ) {}

  public record CreateApprovalPolicyRequest(
      @NotBlank String policyName,
      String currency,
      boolean allowSelfApproval,
      boolean requireRoleSeparation,
      Instant effectiveFrom,
      Instant effectiveTo,
      @NotEmpty List<@Valid CreateApprovalTierRequest> tiers
  ) {}

  public record StepTemplateResponse(
      UUID id,
      int stepOrder,
      String stepName,
      String requiredRole,
      int minApprovals,
      String authMethod,
      Integer deadlineHours
  ) {}

  public record TierResponse(
      UUID id,
      String tierName,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      int priorityOrder,
      List<StepTemplateResponse> steps
  ) {}

  public record ApprovalPolicyResponse(
      UUID id,
      UUID corporateId,
      String policyName,
      int versionNumber,
      String status,
      String currency,
      boolean allowSelfApproval,
      boolean requireRoleSeparation,
      Instant effectiveFrom,
      Instant effectiveTo,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      List<TierResponse> tiers
  ) {}

  public record SimulateApprovalPlanRequest(
      @NotNull UUID corporateId,
      @NotNull @DecimalMin("0.01") BigDecimal totalAmount,
      String currency
  ) {}

  public record SimulatedStep(
      int stepOrder,
      String stepName,
      String requiredRole,
      int minApprovals,
      String authMethod,
      List<UUID> eligibleUserIds
  ) {}

  public record SimulateApprovalPlanResponse(
      UUID policyId,
      int policyVersion,
      String policyName,
      String tierName,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      List<SimulatedStep> steps
  ) {}
}
