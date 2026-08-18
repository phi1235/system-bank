package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ForensicCopilotDtos {
  private ForensicCopilotDtos() {}
  public record CreateCopilotSessionRequest(UUID transactionId, UUID caseId) {}
  public record CopilotSessionResponse(UUID id, UUID transactionId, UUID caseId, String status, Instant createdAt, Instant expiresAt) {}
  public record CopilotMessageRequest(@NotBlank @Size(max = 2000) String question) {}
  public record CopilotCitationResponse(String sourceType, String sourceId, String label) {}
  public record CopilotAnswerResponse(UUID messageId, String answer, String status, List<String> toolCalls, List<CopilotCitationResponse> citations, Map<String, Object> validation, Instant createdAt) {}
  public record CopilotProviderHealthResponse(boolean enabled, boolean configured, String provider, String model, String status) {}
}
