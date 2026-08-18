package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.CreateEvidenceExportRequest;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.EvidenceExportResponse;
import com.banksystem.transaction.application.forensics.ForensicArtifactStorage;
import com.banksystem.transaction.application.forensics.ForensicExportService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics")
public class AdminForensicExportController {
  private final ForensicExportService service;

  public AdminForensicExportController(ForensicExportService service) { this.service = service; }

  @PostMapping("/cases/{caseId}/exports")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_EVIDENCE_EXPORT)
  public ApiResponse<EvidenceExportResponse> create(
      @PathVariable UUID caseId,
      @Valid @RequestBody CreateEvidenceExportRequest request) {
    return ApiResponse.ok(service.create(caseId, request.reason(), UserContext.requireUser()));
  }

  @GetMapping("/exports/{jobId}")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_EVIDENCE_EXPORT)
  public ApiResponse<EvidenceExportResponse> get(@PathVariable UUID jobId) {
    return ApiResponse.ok(service.get(jobId, UserContext.requireUser()));
  }

  @GetMapping("/exports/{jobId}/download")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_EVIDENCE_EXPORT)
  public ResponseEntity<byte[]> download(@PathVariable UUID jobId) {
    ForensicArtifactStorage.StoredArtifact artifact =
        service.download(jobId, UserContext.requireUser());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("forensic-evidence-" + jobId + ".json").build().toString())
        .body(artifact.content());
  }
}
