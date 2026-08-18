package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ForensicOperationDtos {
  private ForensicOperationDtos() {}

  public record CreateEvidenceExportRequest(
      @NotBlank @Size(max = 500) String reason) {}

  public record EvidenceExportResponse(
      UUID id,
      UUID caseId,
      String status,
      String sensitivity,
      String packageSha256,
      String errorDetail,
      Instant createdAt,
      Instant completedAt,
      Instant expiresAt) {}

  public record CreateTwinForkRequest(
      @NotNull UUID transactionId,
      @Min(5) @Max(1440) Integer ttlMinutes) {}

  public record TwinForkResponse(
      UUID id,
      UUID transactionId,
      String status,
      String snapshotSha256,
      int schemaVersion,
      Instant createdAt,
      Instant expiresAt) {}

  public record CreateReplayRequest(
      @NotNull UUID forkId,
      @NotBlank @Size(max = 100) String scenarioId,
      long seed,
      @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{7,64}$") String targetCommitSha) {}

  public record ReplayRunResponse(
      UUID id,
      UUID forkId,
      String scenarioId,
      long seed,
      String targetCommitSha,
      String status,
      String resultSha256,
      String errorDetail,
      Instant createdAt,
      Instant startedAt,
      Instant completedAt,
      Instant expiresAt) {}
}
