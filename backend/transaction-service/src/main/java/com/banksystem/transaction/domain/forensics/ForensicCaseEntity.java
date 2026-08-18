package com.banksystem.transaction.domain.forensics;

import com.banksystem.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "forensic_cases")
public class ForensicCaseEntity {

  @Id private UUID id;
  @Column(name = "case_number", nullable = false, unique = true, length = 32) private String caseNumber;
  @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "account_id") private UUID accountId;
  @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
  @Column(name = "source_reference_id", length = 100) private String sourceReferenceId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ForensicCaseStatus status;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ForensicCasePriority priority;
  @Column(nullable = false, length = 200) private String title;
  @Column(length = 2000) private String summary;
  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_completeness", nullable = false, length = 20)
  private EvidenceCompleteness evidenceCompleteness;
  @Column(name = "assigned_to") private UUID assignedTo;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(name = "submitted_by") private UUID submittedBy;
  @Column(name = "checker_id") private UUID checkerId;
  @Enumerated(EnumType.STRING)
  @Column(name = "resolution_code", length = 30)
  private ForensicResolutionCode resolutionCode;
  @Column(name = "resolution_note", length = 2000) private String resolutionNote;
  @Enumerated(EnumType.STRING)
  @Column(name = "investigation_stage", nullable = false, length = 40)
  private InvestigationStage investigationStage = InvestigationStage.INITIALIZED;
  @Column(name = "remediation_status", nullable = false, length = 32)
  private String remediationStatus = "NOT_REQUIRED";
  @Column(name = "remediation_json", columnDefinition = "TEXT") private String remediationJson;
  @Column(name = "narrative_json", columnDefinition = "TEXT") private String narrativeJson;
  @Column(nullable = false) private boolean systemic;
  @Column(name = "submitted_at") private Instant submittedAt;
  @Column(name = "resolved_at") private Instant resolvedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(nullable = false) private long version;

  public static ForensicCaseEntity create(
      UUID id,
      String caseNumber,
      UUID transactionId,
      UUID accountId,
      String sourceType,
      String sourceReferenceId,
      ForensicCasePriority priority,
      String title,
      String summary,
      UUID actor,
      Instant now) {
    ForensicCaseEntity entity = new ForensicCaseEntity();
    entity.id = id;
    entity.caseNumber = caseNumber;
    entity.transactionId = transactionId;
    entity.accountId = accountId;
    entity.sourceType = sourceType;
    entity.sourceReferenceId = sourceReferenceId;
    entity.status = ForensicCaseStatus.OPEN;
    entity.investigationStage = InvestigationStage.INITIALIZED;
    entity.priority = priority;
    entity.title = title;
    entity.summary = summary;
    entity.evidenceCompleteness = EvidenceCompleteness.EMPTY;
    entity.createdBy = actor;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void assign(UUID assignee, Instant now) {
    requireStatus(ForensicCaseStatus.OPEN, ForensicCaseStatus.ASSIGNED, ForensicCaseStatus.REOPENED);
    assignedTo = assignee;
    status = ForensicCaseStatus.ASSIGNED;
    updatedAt = now;
  }

  public void start(UUID actor, boolean administrator, Instant now) {
    requireStatus(ForensicCaseStatus.ASSIGNED, ForensicCaseStatus.REOPENED);
    if (!administrator && !actor.equals(assignedTo)) {
      throw forbidden("Only the assignee can start this investigation");
    }
    status = ForensicCaseStatus.INVESTIGATING;
    updatedAt = now;
  }

  public void submit(UUID actor, boolean administrator, EvidenceCompleteness completeness, Instant now) {
    requireStatus(ForensicCaseStatus.INVESTIGATING);
    if (!administrator && !actor.equals(assignedTo)) {
      throw forbidden("Only the assignee can submit this investigation");
    }
    if (completeness == EvidenceCompleteness.EMPTY || completeness == EvidenceCompleteness.CORRUPTED) {
      throw conflict("FORENSIC_EVIDENCE_INCOMPLETE", "Usable evidence is required before submission");
    }
    if (investigationStage != InvestigationStage.ROOT_CAUSE_CONFIRMED
        && investigationStage != InvestigationStage.REPLAY_VERIFIED) {
      throw conflict("FORENSIC_STAGE_INCOMPLETE",
          "Root cause must be confirmed before submitting to checker. Current stage: " + investigationStage);
    }
    evidenceCompleteness = completeness;
    submittedBy = actor;
    submittedAt = now;
    checkerId = null;
    status = ForensicCaseStatus.PENDING_CHECKER;
    updatedAt = now;
  }

  public void approve(
      UUID checker,
      ForensicResolutionCode resolution,
      String note,
      boolean systemicFlag,
      Instant now) {
    requireStatus(ForensicCaseStatus.PENDING_CHECKER);
    if (checker.equals(submittedBy) || checker.equals(createdBy)) {
      throw conflict("MAKER_CHECKER_SAME_USER", "Maker and checker must be different users");
    }
    if ((resolution == ForensicResolutionCode.CONFIRMED_ISSUE || resolution == ForensicResolutionCode.DATA_GAP)
        && "PENDING".equals(remediationStatus)) {
      throw conflict("FORENSIC_REMEDIATION_REQUIRED",
          "All remediation actions must be completed before resolving a confirmed issue");
    }
    if ((resolution == ForensicResolutionCode.CONFIRMED_ISSUE || resolution == ForensicResolutionCode.DATA_GAP)
        && "IN_PROGRESS".equals(remediationStatus)) {
      throw conflict("FORENSIC_REMEDIATION_REQUIRED",
          "Remediation is still in progress. Complete all actions before approving.");
    }
    checkerId = checker;
    resolutionCode = resolution;
    resolutionNote = note;
    systemic = systemicFlag;
    resolvedAt = now;
    investigationStage = InvestigationStage.INVESTIGATION_CONCLUDED;
    status = switch (resolution) {
      case DUPLICATE -> ForensicCaseStatus.DUPLICATE;
      case FALSE_POSITIVE, EXPECTED_BEHAVIOR -> ForensicCaseStatus.DISMISSED;
      case CONFIRMED_ISSUE, DATA_GAP -> ForensicCaseStatus.RESOLVED;
    };
    updatedAt = now;
  }

  public void initiateRemediation(Instant now) {
    if ("NOT_REQUIRED".equals(remediationStatus) || "COMPLETED".equals(remediationStatus)) {
      remediationStatus = "PENDING";
      updatedAt = now;
    }
  }

  public void recordRemediation(String updatedJson, Instant now) {
    remediationJson = updatedJson;
    remediationStatus = "IN_PROGRESS";
    updatedAt = now;
  }

  public void completeRemediation(String finalJson, Instant now) {
    remediationJson = finalJson;
    remediationStatus = "COMPLETED";
    updatedAt = now;
  }

  public void reject(UUID checker, String reason, Instant now) {
    requireStatus(ForensicCaseStatus.PENDING_CHECKER);
    if (checker.equals(submittedBy) || checker.equals(createdBy)) {
      throw conflict("MAKER_CHECKER_SAME_USER", "Maker and checker must be different users");
    }
    checkerId = checker;
    resolutionNote = reason;
    status = ForensicCaseStatus.INVESTIGATING;
    updatedAt = now;
  }

  @Column(name = "investigation_cycle", nullable = false)
  private int investigationCycle = 1;

  public void reopen(Instant now) {
    requireStatus(ForensicCaseStatus.RESOLVED, ForensicCaseStatus.DISMISSED, ForensicCaseStatus.DUPLICATE);
    status = ForensicCaseStatus.REOPENED;
    checkerId = null;
    submittedBy = null;
    resolutionCode = null;
    resolutionNote = null;
    remediationStatus = "PENDING";
    systemic = false;
    submittedAt = null;
    resolvedAt = null;
    investigationCycle++;
    investigationStage = InvestigationStage.CAUSAL_GRAPH_ATTACHED;
    updatedAt = now;
  }

  public void markViolationDetected(Instant now) {
    this.investigationStage = InvestigationStage.VIOLATION_DETECTED;
    this.updatedAt = now;
  }

  public void attachCausalGraph(Instant now) {
    if (this.investigationStage == InvestigationStage.INITIALIZED
        || this.investigationStage == InvestigationStage.VIOLATION_DETECTED) {
      this.investigationStage = InvestigationStage.CAUSAL_GRAPH_ATTACHED;
    }
    this.updatedAt = now;
  }

  public void confirmRootCause(Instant now) {
    this.investigationStage = InvestigationStage.ROOT_CAUSE_CONFIRMED;
    this.updatedAt = now;
  }

  public void verifyReplay(Instant now) {
    this.investigationStage = InvestigationStage.REPLAY_VERIFIED;
    this.updatedAt = now;
  }

  private void requireStatus(ForensicCaseStatus... allowed) {
    for (ForensicCaseStatus candidate : allowed) {
      if (candidate == status) {
        return;
      }
    }
    throw conflict("FORENSIC_CASE_INVALID_TRANSITION", "Invalid transition from " + status);
  }

  private BusinessException conflict(String code, String message) {
    return new BusinessException(code, message, HttpStatus.CONFLICT);
  }

  private BusinessException forbidden(String message) {
    return new BusinessException("FORENSIC_CASE_FORBIDDEN", message, HttpStatus.FORBIDDEN);
  }

  public UUID getId() { return id; }
  public String getCaseNumber() { return caseNumber; }
  public UUID getTransactionId() { return transactionId; }
  public UUID getAccountId() { return accountId; }
  public String getSourceType() { return sourceType; }
  public String getSourceReferenceId() { return sourceReferenceId; }
  public ForensicCaseStatus getStatus() { return status; }
  public InvestigationStage getInvestigationStage() { return investigationStage; }
  public ForensicCasePriority getPriority() { return priority; }
  public String getTitle() { return title; }
  public String getSummary() { return summary; }
  public EvidenceCompleteness getEvidenceCompleteness() { return evidenceCompleteness; }
  public UUID getAssignedTo() { return assignedTo; }
  public UUID getCreatedBy() { return createdBy; }
  public UUID getSubmittedBy() { return submittedBy; }
  public UUID getCheckerId() { return checkerId; }
  public ForensicResolutionCode getResolutionCode() { return resolutionCode; }
  public String getResolutionNote() { return resolutionNote; }
  public String getRemediationStatus() { return remediationStatus; }
  public String getRemediationJson() { return remediationJson; }
  public String getNarrativeJson() { return narrativeJson; }

  public void updateNarrative(String narrativeJson, Instant now) {
    this.narrativeJson = narrativeJson;
    this.updatedAt = now;
  }

  public boolean isSystemic() { return systemic; }
  public Instant getSubmittedAt() { return submittedAt; }
  public Instant getResolvedAt() { return resolvedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public int getInvestigationCycle() { return investigationCycle; }
  public long getVersion() { return version; }
}
