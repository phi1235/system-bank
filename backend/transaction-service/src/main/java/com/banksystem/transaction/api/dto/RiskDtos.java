package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class RiskDtos {
  private RiskDtos() {}

  public record RiskRuleRequest(
      @NotBlank String code,
      @NotBlank String ruleType,
      @NotBlank String action,
      @NotNull Boolean enabled,
      @NotNull Integer priority,
      BigDecimal thresholdAmount,
      Long windowSeconds,
      Long maxCount,
      BigDecimal maxTotalAmount,
      @Size(max = 255) String description) {}

  public record RiskRuleResponse(
      String id, String code, String ruleType, String action, boolean enabled, int priority,
      BigDecimal thresholdAmount, Long windowSeconds, Long maxCount, BigDecimal maxTotalAmount,
      String description, Instant createdAt, Instant updatedAt) {}

  public record BlacklistRequest(
      @NotBlank String subjectType,
      @NotBlank String subjectValue,
      @NotBlank @Size(max = 500) String reason,
      Instant expiresAt) {}

  public record BlacklistResponse(
      String id, String subjectType, String subjectValue, String reason, boolean active,
      Instant expiresAt, String createdBy, Instant createdAt, Instant updatedAt) {}

  public record RiskDecisionRequest(@Size(max = 500) String note) {}
}
