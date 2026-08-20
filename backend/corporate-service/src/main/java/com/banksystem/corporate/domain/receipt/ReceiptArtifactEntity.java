package com.banksystem.corporate.domain.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "receipt_artifacts")
public class ReceiptArtifactEntity {

  @Id
  private UUID id;

  @Column(name = "corporate_id", nullable = false)
  private UUID corporateId;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "artifact_type", nullable = false, length = 30)
  private String artifactType; // INDIVIDUAL_PAYOUT_RECEIPT, CONSOLIDATED_BATCH_REPORT

  @Column(name = "file_key", nullable = false)
  private String fileKey;

  @Column(name = "file_sha256", nullable = false, length = 64)
  private String fileSha256;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "email_sent", nullable = false)
  private boolean emailSent = false;

  @Column(name = "email_sent_at")
  private Instant emailSentAt;

  @Column(name = "email_recipient", length = 160)
  private String emailRecipient;

  @Column(name = "email_status", nullable = false, length = 20)
  private String emailStatus = "NOT_REQUIRED";

  @Column(name = "email_retry_count", nullable = false)
  private int emailRetryCount;

  @Column(name = "email_next_attempt_at")
  private Instant emailNextAttemptAt;

  @Column(name = "email_claimed_by", length = 100)
  private String emailClaimedBy;

  @Column(name = "email_lease_until")
  private Instant emailLeaseUntil;

  @Column(name = "email_last_error", length = 500)
  private String emailLastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public UUID getBatchId() { return batchId; }
  public void setBatchId(UUID batchId) { this.batchId = batchId; }
  public UUID getItemId() { return itemId; }
  public void setItemId(UUID itemId) { this.itemId = itemId; }
  public String getArtifactType() { return artifactType; }
  public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
  public String getFileKey() { return fileKey; }
  public void setFileKey(String fileKey) { this.fileKey = fileKey; }
  public String getFileSha256() { return fileSha256; }
  public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
  public long getFileSizeBytes() { return fileSizeBytes; }
  public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
  public boolean isEmailSent() { return emailSent; }
  public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
  public Instant getEmailSentAt() { return emailSentAt; }
  public void setEmailSentAt(Instant emailSentAt) { this.emailSentAt = emailSentAt; }
  public String getEmailRecipient() { return emailRecipient; }
  public void setEmailRecipient(String emailRecipient) { this.emailRecipient = emailRecipient; }
  public String getEmailStatus() { return emailStatus; }
  public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }
  public int getEmailRetryCount() { return emailRetryCount; }
  public void setEmailRetryCount(int emailRetryCount) { this.emailRetryCount = emailRetryCount; }
  public Instant getEmailNextAttemptAt() { return emailNextAttemptAt; }
  public void setEmailNextAttemptAt(Instant emailNextAttemptAt) { this.emailNextAttemptAt = emailNextAttemptAt; }
  public String getEmailClaimedBy() { return emailClaimedBy; }
  public void setEmailClaimedBy(String emailClaimedBy) { this.emailClaimedBy = emailClaimedBy; }
  public Instant getEmailLeaseUntil() { return emailLeaseUntil; }
  public void setEmailLeaseUntil(Instant emailLeaseUntil) { this.emailLeaseUntil = emailLeaseUntil; }
  public String getEmailLastError() { return emailLastError; }
  public void setEmailLastError(String emailLastError) { this.emailLastError = emailLastError; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
