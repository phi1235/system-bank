package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "forensic_verification_results")
public class ForensicVerificationResultEntity {
  @Id private UUID id;
  @Column(name = "run_id", nullable = false) private UUID runId;
  @Column(name = "rule_code", nullable = false, length = 80) private String ruleCode;
  @Column(nullable = false, length = 20) private String outcome;
  @Column(nullable = false, length = 20) private String severity;
  @Column(nullable = false, length = 500) private String message;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb") private String evidenceJson;
  @Column(name = "evaluated_at", nullable = false) private Instant evaluatedAt;

  public static ForensicVerificationResultEntity of(
      UUID runId, String ruleCode, String outcome, String severity, String message,
      String evidenceJson, Instant evaluatedAt) {
    ForensicVerificationResultEntity entity = new ForensicVerificationResultEntity();
    entity.id = UUID.randomUUID();
    entity.runId = runId;
    entity.ruleCode = ruleCode;
    entity.outcome = outcome;
    entity.severity = severity;
    entity.message = message;
    entity.evidenceJson = evidenceJson;
    entity.evaluatedAt = evaluatedAt;
    return entity;
  }

  public UUID getId() { return id; }
  public String getRuleCode() { return ruleCode; }
  public String getOutcome() { return outcome; }
  public String getSeverity() { return severity; }
  public String getMessage() { return message; }
  public String getEvidenceJson() { return evidenceJson; }
  public Instant getEvaluatedAt() { return evaluatedAt; }
}
