package com.banksystem.transaction.domain.forensics;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForensicVerificationResultRepository
    extends JpaRepository<ForensicVerificationResultEntity, UUID> {
  List<ForensicVerificationResultEntity> findByRunIdOrderByRuleCode(UUID runId);

  @Modifying
  @Query(value = """
      INSERT INTO forensic_verification_results
        (id, run_id, rule_code, outcome, severity, message, evidence_json, evaluated_at)
      VALUES
        (:id, :runId, :ruleCode, :outcome, :severity, :message,
         CAST(:evidenceJson AS jsonb), :evaluatedAt)
      """, nativeQuery = true)
  int insert(
      @Param("id") UUID id,
      @Param("runId") UUID runId,
      @Param("ruleCode") String ruleCode,
      @Param("outcome") String outcome,
      @Param("severity") String severity,
      @Param("message") String message,
      @Param("evidenceJson") String evidenceJson,
      @Param("evaluatedAt") java.time.Instant evaluatedAt);
}
