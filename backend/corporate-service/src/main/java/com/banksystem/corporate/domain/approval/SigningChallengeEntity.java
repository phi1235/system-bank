package com.banksystem.corporate.domain.approval;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signing_challenges")
public class SigningChallengeEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "task_id", nullable = false)
  private ApprovalTaskEntity task;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private PayoutBatchEntity batch;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "challenge_type", nullable = false, length = 30)
  private String challengeType; // TOTP_STEPUP, DIGITAL_SIGNATURE_CA

  @Column(nullable = false, unique = true, length = 64)
  private String nonce;

  @Column(name = "payload_hash", nullable = false, length = 64)
  private String payloadHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount = 0;

  @Column(nullable = false)
  private boolean verified = false;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public ApprovalTaskEntity getTask() { return task; }
  public void setTask(ApprovalTaskEntity task) { this.task = task; }
  public PayoutBatchEntity getBatch() { return batch; }
  public void setBatch(PayoutBatchEntity batch) { this.batch = batch; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getChallengeType() { return challengeType; }
  public void setChallengeType(String challengeType) { this.challengeType = challengeType; }
  public String getNonce() { return nonce; }
  public void setNonce(String nonce) { this.nonce = nonce; }
  public String getPayloadHash() { return payloadHash; }
  public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public int getAttemptCount() { return attemptCount; }
  public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
  public boolean isVerified() { return verified; }
  public void setVerified(boolean verified) { this.verified = verified; }
  public Instant getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
