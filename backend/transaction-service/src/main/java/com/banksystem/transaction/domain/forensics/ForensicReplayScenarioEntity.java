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
@Table(name = "forensic_replay_scenarios")
public class ForensicReplayScenarioEntity {
  @Id @Column(name = "scenario_id", length = 100) private String scenarioId;
  @Column(nullable = false, length = 200) private String title;
  @Column(name = "engine_key", nullable = false, length = 80) private String engineKey;
  @Column(name = "source_incident_id", nullable = false, length = 100) private String sourceIncidentId;
  @Column(name = "source_evidence_ref", nullable = false, length = 200) private String sourceEvidenceRef;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb") private String definitionJson;
  @Column(nullable = false) private boolean sanitized;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(name = "confirmed_by") private UUID confirmedBy;
  @Column(name = "confirmed_at") private Instant confirmedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(nullable = false) private long version;

  public String getScenarioId() { return scenarioId; }
  public String getTitle() { return title; }
  public String getEngineKey() { return engineKey; }
  public String getSourceIncidentId() { return sourceIncidentId; }
  public String getSourceEvidenceRef() { return sourceEvidenceRef; }
  public String getDefinitionJson() { return definitionJson; }
  public boolean isSanitized() { return sanitized; }
  public String getStatus() { return status; }
  public UUID getCreatedBy() { return createdBy; }
  public UUID getConfirmedBy() { return confirmedBy; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
