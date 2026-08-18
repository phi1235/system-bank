package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseDetailResponse;
import com.banksystem.transaction.domain.forensics.ForensicExportJobEntity;
import com.banksystem.transaction.domain.forensics.ForensicExportJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ForensicExportWorker {
  private final ForensicExportJobRepository repository;
  private final ForensicCaseQueryService caseQueryService;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final ForensicEvidenceSanitizer sanitizer;
  private final ForensicArtifactCodec codec;
  private final ForensicArtifactStorage storage;
  private final Clock clock;

  public ForensicExportWorker(
      ForensicExportJobRepository repository,
      ForensicCaseQueryService caseQueryService,
      ForensicInvestigationQueryService investigationQueryService,
      ForensicEvidenceSanitizer sanitizer,
      ForensicArtifactCodec codec,
      ForensicArtifactStorage storage,
      Clock clock) {
    this.repository = repository;
    this.caseQueryService = caseQueryService;
    this.investigationQueryService = investigationQueryService;
    this.sanitizer = sanitizer;
    this.codec = codec;
    this.storage = storage;
    this.clock = clock;
  }

  @Async
  public void generate(UUID jobId) {
    ForensicExportJobEntity job = repository.findById(jobId).orElse(null);
    if (job == null || !"PENDING".equals(job.getStatus())) return;
    job.running();
    repository.save(job);
    try {
      ForensicCaseDetailResponse forensicCase = caseQueryService.get(job.getCaseId());
      JsonNode investigation = forensicCase.forensicCase().transactionId() == null
          ? null : sanitizer.sanitize(investigationQueryService.get(
              UUID.fromString(forensicCase.forensicCase().transactionId())));
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("case", sanitizer.sanitize(forensicCase));
      evidence.put("investigation", investigation);
      byte[] evidenceBytes = codec.write(evidence);
      String evidenceHash = codec.sha256(evidenceBytes);
      Instant generatedAt = clock.instant();
      Map<String, Object> manifest = Map.of(
          "schemaVersion", 1,
          "sensitivity", "RESTRICTED",
          "generatedAt", generatedAt,
          "requestedBy", job.getRequestedBy().toString(),
          "reason", job.getReason(),
          "evidenceSha256", evidenceHash);
      Map<String, Object> packageBody = Map.of("manifest", manifest, "evidence", evidence);
      byte[] content = codec.write(packageBody);
      String checksum = codec.sha256(content);
      String uri = storage.put(
          "exports/" + job.getCaseId() + "/" + jobId + ".json", content, "application/json");
      job.complete(uri, checksum, generatedAt);
    } catch (RuntimeException exception) {
      String detail = exception.getMessage() == null
          ? exception.getClass().getSimpleName() : exception.getMessage();
      job.fail(detail.substring(0, Math.min(detail.length(), 500)), clock.instant());
    }
    repository.save(job);
  }
}
