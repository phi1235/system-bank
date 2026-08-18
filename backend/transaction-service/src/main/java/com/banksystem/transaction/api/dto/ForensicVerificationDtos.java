package com.banksystem.transaction.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ForensicVerificationDtos {

  private ForensicVerificationDtos() {}

  public record VerificationRuleResultResponse(
      String id,
      String ruleCode,
      String outcome,
      String severity,
      String message,
      Map<String, Object> evidence,
      Instant evaluatedAt) {}

  public record VerificationRunResponse(
      String id,
      String transactionId,
      String ruleSetVersion,
      String status,
      String outcome,
      Instant sourceWatermark,
      Instant startedAt,
      Instant completedAt,
      List<VerificationRuleResultResponse> results) {}
}
