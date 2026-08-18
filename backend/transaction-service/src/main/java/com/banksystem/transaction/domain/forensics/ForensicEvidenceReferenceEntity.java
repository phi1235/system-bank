package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_evidence_references")
public class ForensicEvidenceReferenceEntity {
  @Id private UUID id;
  @Column(name = "case_id") private UUID caseId;
  @Column(name = "finding_id") private UUID findingId;
  @Column(name = "subject_type", nullable = false, length = 30) private String subjectType;
  @Column(name = "subject_id", nullable = false, length = 100) private String subjectId;
  @Column(nullable = false, length = 40) private String source;
  @Column(name = "source_reference_id", nullable = false, length = 160) private String sourceReferenceId;
  @Column(name = "schema_version", nullable = false) private int schemaVersion;
  @Column(name = "checksum_sha256", nullable = false, length = 64) private String checksumSha256;
  @Column(nullable = false, length = 20) private String sensitivity;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "storage_uri", length = 500) private String storageUri;
  @Column(name = "content_type", length = 100) private String contentType;
  @Column(name = "size_bytes") private Long sizeBytes;
  @Column(name = "captured_at", nullable = false) private Instant capturedAt;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public UUID getId() { return id; }
  public UUID getCaseId() { return caseId; }
  public UUID getFindingId() { return findingId; }
  public String getSubjectType() { return subjectType; }
  public String getSubjectId() { return subjectId; }
  public String getSource() { return source; }
  public String getSourceReferenceId() { return sourceReferenceId; }
  public int getSchemaVersion() { return schemaVersion; }
  public String getChecksumSha256() { return checksumSha256; }
  public String getSensitivity() { return sensitivity; }
  public String getStatus() { return status; }
  public String getStorageUri() { return storageUri; }
  public String getContentType() { return contentType; }
  public Long getSizeBytes() { return sizeBytes; }
  public Instant getCapturedAt() { return capturedAt; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
}
