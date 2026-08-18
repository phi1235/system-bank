package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class ForensicCaseDtos {
  private ForensicCaseDtos() {}

  public record ForensicBusinessNarrativeResponse(
      String summary,
      String impactAnalysis,
      String rootCauseNarrative,
      String suggestedRemediationNarrative,
      List<String> groundedEvidenceKeys,
      String generatedBy,
      Instant generatedAt) {}

  public record ForensicCaseFilterRequest(
      String q,
      String status,
      String priority,
      UUID assignedTo,
      UUID transactionId,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size) {}

  public record CreateForensicCaseRequest(
      UUID transactionId,
      UUID accountId,
      @NotBlank @Size(max = 30) String sourceType,
      @Size(max = 100) String sourceReferenceId,
      @NotBlank @Size(max = 20) String priority,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 2000) String summary) {}

  public record AssignForensicCaseRequest(
      @NotNull UUID assignee,
      @Min(0) long expectedVersion,
      @Size(max = 2000) String note) {}

  public record VersionedForensicCaseRequest(@Min(0) long expectedVersion) {}

  public record SubmitForensicCaseRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 2000) String recommendation) {}

  public record ApproveForensicResolutionRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 30) String resolutionCode,
      @NotBlank @Size(max = 2000) String resolutionNote,
      boolean systemic) {}

  public record RejectForensicResolutionRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 2000) String reason) {}

  public record ReopenForensicCaseRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 2000) String reason) {}

  public record ConfirmRootCauseRequest(
      @Min(0) long expectedVersion,
      @Size(max = 2000) String note) {}

  public record VerifyReplayRequest(
      @Min(0) long expectedVersion,
      UUID replayRunId,
      @Size(max = 2000) String note) {}

  public record CreateForensicFindingRequest(
      @NotBlank @Size(max = 80) String ruleCode,
      @NotBlank @Size(max = 20) String severity,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 2000) String detail,
      Map<String, Object> evidence) {}

  public record ForensicViolationFilterRequest(
      String disposition,
      String severity,
      String ruleCode,
      UUID transactionId,
      Instant since,
      @Min(0) Integer page,
      @Min(1) @jakarta.validation.constraints.Max(100) Integer size) {}

  public record AcknowledgeForensicViolationRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 1000) String note) {}

  public record ResolveForensicViolationRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 1000) String reason,
      Map<String, Object> evidence) {}

  public record ForensicCaseResponse(
      String id,
      String caseNumber,
      String transactionId,
      String accountId,
      String sourceType,
      String sourceReferenceId,
      String status,
      String investigationStage,
      String priority,
      String title,
      String summary,
      String evidenceCompleteness,
      String assignedTo,
      String createdBy,
      String submittedBy,
      String checkerId,
      String resolutionCode,
      String resolutionNote,
      String remediationStatus,
      List<RemediationActionResponse> remediationActions,
      ForensicBusinessNarrativeResponse businessNarrative,
      boolean systemic,
      int investigationCycle,
      long version,
      Instant submittedAt,
      Instant resolvedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public record RecordRemediationRequest(
      @Min(0) long expectedVersion,
      @NotBlank @Size(max = 30) String actionType,
      @Size(max = 200) String referenceId,
      @NotBlank @Size(max = 500) String description,
      boolean completed) {}

  public record RemediationActionResponse(
      String actionType,
      String referenceId,
      String description,
      boolean completed,
      Instant completedAt) {}

  public record ForensicFindingResponse(
      String id,
      String findingKey,
      String ruleCode,
      String outcome,
      String severity,
      String disposition,
      String title,
      String detail,
      Map<String, Object> evidence,
      String evidenceHash,
      int occurrenceCount,
      Instant detectedAt,
      Instant lastSeenAt,
      String acknowledgedBy,
      Instant acknowledgedAt,
      String resolutionReason,
      Map<String, Object> resolutionEvidence,
      String resolvedBy,
      Instant resolvedAt,
      long version) {}

  public record ForensicCaseDetailResponse(
      ForensicCaseResponse forensicCase,
      List<ForensicFindingResponse> findings) {}

  public record ForensicCaseHistoryResponse(
      String id,
      String actorUserId,
      String action,
      String fromStatus,
      String toStatus,
      String decision,
      String note,
      long caseVersion,
      Instant createdAt) {}

  public record ForensicEvidenceReferenceResponse(
      String id,
      String subjectType,
      String subjectId,
      String source,
      String sourceReferenceId,
      int schemaVersion,
      String checksumSha256,
      String sensitivity,
      String status,
      String contentType,
      Instant capturedAt) {}

  public record ExecuteAdjustmentRemediationRequest(
      UUID targetAccountId,
      UUID transactionId,
      UUID caseId,
      @NotNull BigDecimal amount,
      @NotBlank String reason) {}

  public record ExecuteHoldRemediationRequest(
      UUID targetAccountId,
      UUID caseId,
      @NotNull BigDecimal amount,
      @NotBlank String reason) {}
}
