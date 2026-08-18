package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForensicReplayScenarioRepository
    extends JpaRepository<ForensicReplayScenarioEntity, String> {
  List<ForensicReplayScenarioEntity> findByStatusOrderByUpdatedAtDesc(String status);
  Optional<ForensicReplayScenarioEntity> findByScenarioIdAndStatus(String scenarioId, String status);

  @Modifying
  @Query(value = """
      INSERT INTO forensic_replay_scenarios
        (scenario_id, title, engine_key, source_incident_id, source_evidence_ref,
         definition_json, sanitized, status, created_by, created_at, updated_at, version)
      VALUES (:id, :title, :engineKey, :incidentId, :evidenceRef,
              CAST(:definition AS jsonb), :sanitized, 'DRAFT', :actor, :now, :now, 0)
      """, nativeQuery = true)
  int insertDraft(
      @Param("id") String id, @Param("title") String title,
      @Param("engineKey") String engineKey, @Param("incidentId") String incidentId,
      @Param("evidenceRef") String evidenceRef, @Param("definition") String definition,
      @Param("sanitized") boolean sanitized, @Param("actor") UUID actor,
      @Param("now") Instant now);

  @Modifying
  @Query(value = """
      UPDATE forensic_replay_scenarios
      SET status = 'CONFIRMED', confirmed_by = :actor, confirmed_at = :now,
          updated_at = :now, version = version + 1
      WHERE scenario_id = :id AND status = 'DRAFT' AND version = :version
        AND created_by <> :actor AND sanitized = TRUE
      """, nativeQuery = true)
  int confirm(
      @Param("id") String id, @Param("actor") UUID actor,
      @Param("now") Instant now, @Param("version") long version);
}
