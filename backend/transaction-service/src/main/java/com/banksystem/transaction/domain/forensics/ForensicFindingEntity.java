package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "forensic_findings")
public class ForensicFindingEntity {
  @Id private UUID id;
  @Column(name = "finding_key", nullable = false, unique = true, length = 160) private String findingKey;
  @Column(name = "case_id") private UUID caseId;
  @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "rule_code", nullable = false, length = 80) private String ruleCode;
  @Column(name = "subject_type", nullable = false, length = 30) private String subjectType;
  @Column(name = "subject_id", nullable = false, length = 100) private String subjectId;
  @Column(nullable = false, length = 20) private String outcome;
  @Column(nullable = false, length = 20) private String severity;
  @Column(nullable = false, length = 30) private String disposition;
  @Column(nullable = false, length = 200) private String title;
  @Column(length = 2000) private String detail;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb") private String evidenceJson;
  @Column(name = "evidence_hash", nullable = false, length = 64) private String evidenceHash;
  @Column(name = "occurrence_count", nullable = false) private int occurrenceCount;
  @Column(name = "detected_at", nullable = false) private Instant detectedAt;
  @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
  @Column(name = "reviewed_by") private UUID reviewedBy;
  @Column(name = "reviewed_at") private Instant reviewedAt;
  @Column(name = "acknowledged_by") private UUID acknowledgedBy;
  @Column(name = "acknowledged_at") private Instant acknowledgedAt;
  @Column(name = "resolution_reason", length = 1000) private String resolutionReason;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "resolution_evidence", columnDefinition = "jsonb") private String resolutionEvidence;
  @Column(name = "resolved_by") private UUID resolvedBy;
  @Column(name = "resolved_at") private Instant resolvedAt;
  @Version @Column(nullable = false) private long version;

  public static ForensicFindingEntity create(
      String key, UUID transactionId, String ruleCode, String outcome, String severity,
      String title, String detail, String evidenceJson, String evidenceHash, Instant now) {
    ForensicFindingEntity entity = new ForensicFindingEntity();
    entity.id = UUID.randomUUID();
    entity.findingKey = key;
    entity.transactionId = transactionId;
    entity.ruleCode = ruleCode;
    entity.subjectType = "TRANSACTION";
    entity.subjectId = transactionId.toString();
    entity.outcome = outcome;
    entity.severity = severity;
    entity.disposition = "UNREVIEWED";
    entity.title = title;
    entity.detail = detail;
    entity.evidenceJson = evidenceJson;
    entity.evidenceHash = evidenceHash;
    entity.occurrenceCount = 1;
    entity.detectedAt = now;
    entity.lastSeenAt = now;
    return entity;
  }

  public void attachToCase(UUID caseId) { this.caseId = caseId; }
  public void seenAgain(String evidenceJson, String evidenceHash, Instant now) {
    this.evidenceJson = evidenceJson;
    this.evidenceHash = evidenceHash;
    this.lastSeenAt = now;
    this.occurrenceCount++;
  }

  public UUID getId() { return id; }
  public String getFindingKey() { return findingKey; }
  public UUID getCaseId() { return caseId; }
  public UUID getTransactionId() { return transactionId; }
  public String getRuleCode() { return ruleCode; }
  public String getSubjectType() { return subjectType; }
  public String getSubjectId() { return subjectId; }
  public String getOutcome() { return outcome; }
  public String getSeverity() { return severity; }
  public String getDisposition() { return disposition; }
  public String getTitle() { return title; }
  public String getDetail() { return detail; }
  public String getEvidenceJson() { return evidenceJson; }
  public String getEvidenceHash() { return evidenceHash; }
  public int getOccurrenceCount() { return occurrenceCount; }
  public Instant getDetectedAt() { return detectedAt; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public UUID getAcknowledgedBy() { return acknowledgedBy; }
  public Instant getAcknowledgedAt() { return acknowledgedAt; }
  public String getResolutionReason() { return resolutionReason; }
  public String getResolutionEvidence() { return resolutionEvidence; }
  public UUID getResolvedBy() { return resolvedBy; }
  public Instant getResolvedAt() { return resolvedAt; }
  public long getVersion() { return version; }
}
