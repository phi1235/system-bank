package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "manual_review_audit_logs")
public class ManualReviewAuditLogEntity {

  @Id
  private UUID id;

  @Column(name = "transfer_id", nullable = false)
  private UUID transferId;

  @Column(name = "admin_user_id", nullable = false)
  private UUID adminUserId;

  @Column(name = "action", nullable = false, length = 32)
  private String action;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public ManualReviewAuditLogEntity() {}

  public ManualReviewAuditLogEntity(
      UUID id,
      UUID transferId,
      UUID adminUserId,
      String action,
      String reason) {
    this.id = id != null ? id : UUID.randomUUID();
    this.transferId = transferId;
    this.adminUserId = adminUserId;
    this.action = action;
    this.reason = reason;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public void setTransferId(UUID transferId) {
    this.transferId = transferId;
  }

  public UUID getAdminUserId() {
    return adminUserId;
  }

  public void setAdminUserId(UUID adminUserId) {
    this.adminUserId = adminUserId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
