package com.banksystem.customer.domain.kyc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
public class KycDocumentEntity {
  @Id private UUID id;
  @Column(name = "case_id", nullable = false) private UUID caseId;
  @Column(name = "customer_id", nullable = false) private UUID customerId;
  @Column(name = "document_type", nullable = false, length = 40) private String documentType;
  @Column(name = "object_key", nullable = false, unique = true, length = 300) private String objectKey;
  @Column(name = "original_name", nullable = false, length = 255) private String originalName;
  @Column(name = "content_type", nullable = false, length = 100) private String contentType;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @Column(nullable = false, length = 64) private String sha256;
  @Column(name = "scan_status", nullable = false, length = 20) private String scanStatus;
  @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCaseId() { return caseId; }
  public void setCaseId(UUID caseId) { this.caseId = caseId; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }
  public String getDocumentType() { return documentType; }
  public void setDocumentType(String documentType) { this.documentType = documentType; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
  public String getOriginalName() { return originalName; }
  public void setOriginalName(String originalName) { this.originalName = originalName; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
  public String getSha256() { return sha256; }
  public void setSha256(String sha256) { this.sha256 = sha256; }
  public String getScanStatus() { return scanStatus; }
  public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
  public Instant getUploadedAt() { return uploadedAt; }
  public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
