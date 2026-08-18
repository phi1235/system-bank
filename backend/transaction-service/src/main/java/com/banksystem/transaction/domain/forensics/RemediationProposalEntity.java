package com.banksystem.transaction.domain.forensics;

import com.banksystem.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "remediation_proposals")
public class RemediationProposalEntity {

  @Id private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "investigation_cycle", nullable = false)
  private int investigationCycle;

  @Column(name = "source_transaction_id")
  private UUID sourceTransactionId;

  @Column(name = "target_account_id", nullable = false)
  private UUID targetAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private AdjustmentDirection direction;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency;

  @Column(name = "reason", nullable = false, length = 2000)
  private String reason;

  @Column(name = "proposal_payload_hash", length = 64)
  private String proposalPayloadHash;

  @Column(name = "execution_reference_id", length = 100)
  private String executionReferenceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private RemediationProposalStatus status;

  @Column(name = "proposed_by", nullable = false)
  private UUID proposedBy;

  @Column(name = "checker_id")
  private UUID checkerId;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "failure_reason", length = 1000)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected RemediationProposalEntity() {}

  public static RemediationProposalEntity createDraft(
      UUID id,
      UUID caseId,
      int investigationCycle,
      UUID sourceTransactionId,
      UUID targetAccountId,
      AdjustmentDirection direction,
      BigDecimal amount,
      String currency,
      String reason,
      UUID proposedBy,
      Instant now) {
    if (id == null) id = UUID.randomUUID();
    if (caseId == null) throw new BusinessException("CASE_ID_REQUIRED", "Case ID is required for proposal", HttpStatus.BAD_REQUEST);
    if (targetAccountId == null) throw new BusinessException("ACCOUNT_ID_REQUIRED", "Target account ID is required", HttpStatus.BAD_REQUEST);
    if (direction == null) throw new BusinessException("DIRECTION_REQUIRED", "Direction is required", HttpStatus.BAD_REQUEST);
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be strictly positive", HttpStatus.BAD_REQUEST);
    }
    if (currency == null || currency.isBlank()) currency = "VND";
    currency = currency.trim().toUpperCase();
    BigDecimal validAmount = CanonicalProposalPayload.normalizeAmount(amount, currency);
    if (reason == null || reason.isBlank()) {
      throw new BusinessException("REASON_REQUIRED", "Reason is required for remediation proposal", HttpStatus.BAD_REQUEST);
    }
    if (proposedBy == null) throw new BusinessException("PROPOSER_REQUIRED", "Proposer user ID is required", HttpStatus.BAD_REQUEST);

    RemediationProposalEntity proposal = new RemediationProposalEntity();
    proposal.id = id;
    proposal.caseId = caseId;
    proposal.investigationCycle = investigationCycle > 0 ? investigationCycle : 1;
    proposal.sourceTransactionId = sourceTransactionId;
    proposal.targetAccountId = targetAccountId;
    proposal.direction = direction;
    proposal.amount = validAmount;
    proposal.currency = currency;
    proposal.reason = reason.trim();
    proposal.executionReferenceId = "REM-" + caseId + "-C" + proposal.investigationCycle + "-ADJ-" + id;
    proposal.status = RemediationProposalStatus.DRAFT;
    proposal.proposedBy = proposedBy;
    proposal.createdAt = now;
    proposal.updatedAt = now;
    return proposal;
  }

  // Domain State Machine Transitions
  public void submit(String payloadHash, Instant now) {
    requireStatus(RemediationProposalStatus.DRAFT);
    if (payloadHash == null || payloadHash.isBlank()) {
      throw new BusinessException("HASH_REQUIRED", "Payload hash is required upon submission", HttpStatus.BAD_REQUEST);
    }
    this.proposalPayloadHash = payloadHash;
    this.status = RemediationProposalStatus.PENDING_APPROVAL;
    this.submittedAt = now;
    this.updatedAt = now;
  }

  public void approve(UUID checkerUserId, Instant now) {
    requireStatus(RemediationProposalStatus.PENDING_APPROVAL);
    if (checkerUserId == null) {
      throw new BusinessException("CHECKER_REQUIRED", "Checker ID is required for approval", HttpStatus.BAD_REQUEST);
    }
    if (checkerUserId.equals(this.proposedBy)) {
      throw new BusinessException("MAKER_CHECKER_SAME_USER", "Maker and Checker must be different users", HttpStatus.CONFLICT);
    }
    this.checkerId = checkerUserId;
    this.status = RemediationProposalStatus.APPROVED;
    this.approvedAt = now;
    this.updatedAt = now;
  }

  public void reject(UUID checkerUserId, String reason, Instant now) {
    requireStatus(RemediationProposalStatus.PENDING_APPROVAL);
    if (checkerUserId == null) {
      throw new BusinessException("CHECKER_REQUIRED", "Checker ID is required for rejection", HttpStatus.BAD_REQUEST);
    }
    if (checkerUserId.equals(this.proposedBy)) {
      throw new BusinessException("MAKER_CHECKER_SAME_USER", "Maker and Checker must be different users", HttpStatus.CONFLICT);
    }
    this.checkerId = checkerUserId;
    this.rejectionReason = reason;
    this.status = RemediationProposalStatus.REJECTED;
    this.rejectedAt = now;
    this.updatedAt = now;
  }

  public void markExecutionPending(Instant now) {
    requireStatus(RemediationProposalStatus.APPROVED);
    this.status = RemediationProposalStatus.EXECUTION_PENDING;
    this.updatedAt = now;
  }

  public void markExecuting(Instant now) {
    requireStatus(RemediationProposalStatus.APPROVED, RemediationProposalStatus.EXECUTION_PENDING);
    this.status = RemediationProposalStatus.EXECUTING;
    this.updatedAt = now;
  }

  public void markPosted(Instant now) {
    requireStatus(RemediationProposalStatus.EXECUTING, RemediationProposalStatus.EXECUTION_PENDING, RemediationProposalStatus.APPROVED);
    this.status = RemediationProposalStatus.POSTED;
    this.updatedAt = now;
  }

  public void markVerified(Instant now) {
    requireStatus(RemediationProposalStatus.POSTED);
    this.status = RemediationProposalStatus.VERIFIED;
    this.updatedAt = now;
  }

  public void markExecutionFailed(String reason, Instant now) {
    requireStatus(RemediationProposalStatus.APPROVED, RemediationProposalStatus.EXECUTION_PENDING, RemediationProposalStatus.EXECUTING);
    this.status = RemediationProposalStatus.EXECUTION_FAILED;
    this.failureReason = reason;
    this.updatedAt = now;
  }

  public void markVerificationFailed(String reason, Instant now) {
    requireStatus(RemediationProposalStatus.POSTED);
    this.status = RemediationProposalStatus.VERIFICATION_FAILED;
    this.failureReason = reason;
    this.updatedAt = now;
  }

  public void cancel(Instant now) {
    requireStatus(RemediationProposalStatus.DRAFT, RemediationProposalStatus.PENDING_APPROVAL);
    this.status = RemediationProposalStatus.CANCELLED;
    this.updatedAt = now;
  }

  private void requireStatus(RemediationProposalStatus... allowed) {
    for (RemediationProposalStatus candidate : allowed) {
      if (candidate == this.status) {
        return;
      }
    }
    throw new BusinessException(
        "INVALID_PROPOSAL_TRANSITION",
        "Cannot transition proposal from " + this.status + " in current state",
        HttpStatus.CONFLICT);
  }

  // Immutable Getters
  public UUID getId() { return id; }
  public UUID getCaseId() { return caseId; }
  public int getInvestigationCycle() { return investigationCycle; }
  public UUID getSourceTransactionId() { return sourceTransactionId; }
  public UUID getTargetAccountId() { return targetAccountId; }
  public AdjustmentDirection getDirection() { return direction; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public String getReason() { return reason; }
  public String getProposalPayloadHash() { return proposalPayloadHash; }
  public String getExecutionReferenceId() { return executionReferenceId; }
  public RemediationProposalStatus getStatus() { return status; }
  public UUID getProposedBy() { return proposedBy; }
  public UUID getCheckerId() { return checkerId; }
  public Instant getSubmittedAt() { return submittedAt; }
  public Instant getApprovedAt() { return approvedAt; }
  public Instant getRejectedAt() { return rejectedAt; }
  public String getRejectionReason() { return rejectionReason; }
  public String getFailureReason() { return failureReason; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
