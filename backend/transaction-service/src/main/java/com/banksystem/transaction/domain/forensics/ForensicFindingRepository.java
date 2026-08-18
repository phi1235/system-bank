package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ForensicFindingRepository extends JpaRepository<ForensicFindingEntity, UUID> {
  Optional<ForensicFindingEntity> findByFindingKey(String findingKey);
  List<ForensicFindingEntity> findByTransactionIdOrderByDetectedAtDesc(UUID transactionId);
  List<ForensicFindingEntity> findByCaseIdOrderByDetectedAtDesc(UUID caseId);
  long countByCaseId(UUID caseId);
  long countByCaseIdAndDispositionNot(UUID caseId, String disposition);

  @Query("""
      SELECT f FROM ForensicFindingEntity f
      WHERE (:hasDisposition = false OR f.disposition = :disposition)
        AND (:hasSeverity = false OR f.severity = :severity)
        AND (:hasRule = false OR f.ruleCode = :ruleCode)
        AND (:hasTransaction = false OR f.transactionId = :transactionId)
        AND f.lastSeenAt >= :since
      ORDER BY f.lastSeenAt DESC
      """)
  Page<ForensicFindingEntity> searchViolations(
      @Param("hasDisposition") boolean hasDisposition,
      @Param("disposition") String disposition,
      @Param("hasSeverity") boolean hasSeverity,
      @Param("severity") String severity,
      @Param("hasRule") boolean hasRule,
      @Param("ruleCode") String ruleCode,
      @Param("hasTransaction") boolean hasTransaction,
      @Param("transactionId") UUID transactionId,
      @Param("since") Instant since,
      Pageable pageable);

  @Modifying
  @Query(value = """
      UPDATE forensic_findings
      SET disposition = 'ACKNOWLEDGED', acknowledged_by = :actor,
          acknowledged_at = :now, detail = COALESCE(detail, '') || :note,
          version = version + 1
      WHERE id = :id AND version = :expectedVersion AND disposition = 'UNREVIEWED'
      """, nativeQuery = true)
  int acknowledge(
      @Param("id") UUID id, @Param("actor") UUID actor, @Param("note") String note,
      @Param("now") Instant now, @Param("expectedVersion") long expectedVersion);

  @Modifying
  @Query(value = """
      UPDATE forensic_findings
      SET disposition = 'RESOLVED', resolution_reason = :reason,
          resolution_evidence = CAST(:evidence AS jsonb), resolved_by = :actor,
          resolved_at = :now, reviewed_by = :actor, reviewed_at = :now,
          version = version + 1
      WHERE id = :id AND version = :expectedVersion AND disposition = 'ACKNOWLEDGED'
      """, nativeQuery = true)
  int resolve(
      @Param("id") UUID id, @Param("actor") UUID actor, @Param("reason") String reason,
      @Param("evidence") String evidence, @Param("now") Instant now,
      @Param("expectedVersion") long expectedVersion);

  @Modifying
  @Query(value = """
      INSERT INTO forensic_findings (
        id, finding_key, case_id, transaction_id, rule_code, subject_type, subject_id,
        outcome, severity, disposition, title, detail, evidence_json, evidence_hash,
        occurrence_count, detected_at, last_seen_at, version)
      VALUES (
        :id, :findingKey, :caseId, :transactionId, :ruleCode, 'TRANSACTION', :subjectId,
        :outcome, :severity, 'UNREVIEWED', :title, :detail, CAST(:evidenceJson AS jsonb),
        :evidenceHash, 1, :now, :now, 0)
      ON CONFLICT (finding_key) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("findingKey") String findingKey,
      @Param("caseId") UUID caseId,
      @Param("transactionId") UUID transactionId,
      @Param("ruleCode") String ruleCode,
      @Param("subjectId") String subjectId,
      @Param("outcome") String outcome,
      @Param("severity") String severity,
      @Param("title") String title,
      @Param("detail") String detail,
      @Param("evidenceJson") String evidenceJson,
      @Param("evidenceHash") String evidenceHash,
      @Param("now") Instant now);

  @Modifying
  @Query(value = """
      UPDATE forensic_findings
      SET occurrence_count = occurrence_count + 1,
          last_seen_at = :now,
          evidence_json = CAST(:evidenceJson AS jsonb),
          evidence_hash = :evidenceHash,
          case_id = COALESCE(case_id, :caseId),
          version = version + 1
      WHERE finding_key = :findingKey
      """, nativeQuery = true)
  int markSeen(
      @Param("findingKey") String findingKey,
      @Param("caseId") UUID caseId,
      @Param("evidenceJson") String evidenceJson,
      @Param("evidenceHash") String evidenceHash,
      @Param("now") Instant now);
}
