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
@Table(name = "approval_actions")
public class ApprovalActionEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "task_id", nullable = false)
  private ApprovalTaskEntity task;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private PayoutBatchEntity batch;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "actor_role", nullable = false, length = 50)
  private String actorRole;

  @Column(nullable = false, length = 30)
  private String action; // APPROVE, REJECT, RETURN

  @Column(length = 500)
  private String comments;

  @Column(name = "challenge_id")
  private UUID challengeId;

  @Column(name = "signature_reference", length = 255)
  private String signatureReference;

  @Column(name = "action_timestamp", nullable = false)
  private Instant actionTimestamp = Instant.now();

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public ApprovalTaskEntity getTask() { return task; }
  public void setTask(ApprovalTaskEntity task) { this.task = task; }
  public PayoutBatchEntity getBatch() { return batch; }
  public void setBatch(PayoutBatchEntity batch) { this.batch = batch; }
  public UUID getActorId() { return actorId; }
  public void setActorId(UUID actorId) { this.actorId = actorId; }
  public String getActorRole() { return actorRole; }
  public void setActorRole(String actorRole) { this.actorRole = actorRole; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getComments() { return comments; }
  public void setComments(String comments) { this.comments = comments; }
  public UUID getChallengeId() { return challengeId; }
  public void setChallengeId(UUID challengeId) { this.challengeId = challengeId; }
  public String getSignatureReference() { return signatureReference; }
  public void setSignatureReference(String signatureReference) { this.signatureReference = signatureReference; }
  public Instant getActionTimestamp() { return actionTimestamp; }
  public void setActionTimestamp(Instant actionTimestamp) { this.actionTimestamp = actionTimestamp; }
  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
