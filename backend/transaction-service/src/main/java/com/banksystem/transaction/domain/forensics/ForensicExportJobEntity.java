package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_export_jobs")
public class ForensicExportJobEntity {
  @Id private UUID id;
  @Column(name = "case_id", nullable = false) private UUID caseId;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(nullable = false, length = 500) private String reason;
  @Column(nullable = false, length = 20) private String sensitivity;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "storage_uri", length = 500) private String storageUri;
  @Column(name = "package_sha256", length = 64) private String packageSha256;
  @Column(name = "error_detail", length = 500) private String errorDetail;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;

  public static ForensicExportJobEntity pending(
      UUID id, UUID caseId, UUID actor, String reason, Instant now, Instant expiresAt) {
    ForensicExportJobEntity entity = new ForensicExportJobEntity();
    entity.id = id;
    entity.caseId = caseId;
    entity.requestedBy = actor;
    entity.reason = reason;
    entity.sensitivity = "RESTRICTED";
    entity.status = "PENDING";
    entity.createdAt = now;
    entity.expiresAt = expiresAt;
    return entity;
  }

  public void running() { status = "RUNNING"; }
  public void complete(String uri, String checksum, Instant now) {
    status = "COMPLETED";
    storageUri = uri;
    packageSha256 = checksum;
    completedAt = now;
  }
  public void fail(String detail, Instant now) {
    status = "FAILED";
    errorDetail = detail;
    completedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getCaseId() { return caseId; }
  public UUID getRequestedBy() { return requestedBy; }
  public String getReason() { return reason; }
  public String getSensitivity() { return sensitivity; }
  public String getStatus() { return status; }
  public String getStorageUri() { return storageUri; }
  public String getPackageSha256() { return packageSha256; }
  public String getErrorDetail() { return errorDetail; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}
