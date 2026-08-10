package com.banksystem.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class KycDtos {
  private KycDtos() {}

  public record CheckerDecisionRequest(
      @NotBlank String decision,
      @Size(max = 500) String reason) {}

  public record KycDocumentResponse(
      String id,
      String documentType,
      String originalName,
      String contentType,
      long sizeBytes,
      String sha256,
      String scanStatus,
      Instant uploadedAt) {}

  public record KycHistoryResponse(
      String id,
      String actorId,
      String action,
      String fromStatus,
      String toStatus,
      String note,
      Instant createdAt) {}

  public record KycCaseResponse(
      String id,
      String customerId,
      String status,
      String makerId,
      String makerRecommendation,
      String makerNote,
      Instant makerAt,
      String checkerId,
      String decision,
      String decisionReason,
      Instant submittedAt,
      Instant decidedAt,
      List<KycDocumentResponse> documents,
      List<KycHistoryResponse> history,
      Instant createdAt,
      Instant updatedAt) {}
}
