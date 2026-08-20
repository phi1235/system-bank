package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForensicCaseRepository extends JpaRepository<ForensicCaseEntity, UUID> {

  Optional<ForensicCaseEntity> findBySourceTypeAndSourceReferenceId(
      String sourceType, String sourceReferenceId);

  List<ForensicCaseEntity> findByTransactionIdAndStatusInOrderByCreatedAtDesc(
      UUID transactionId, Collection<ForensicCaseStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM ForensicCaseEntity c WHERE c.id = :id")
  Optional<ForensicCaseEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      SELECT c FROM ForensicCaseEntity c
      WHERE (:hasStatus = false OR c.status = :status)
        AND (:hasPriority = false OR c.priority = :priority)
        AND (:hasAssignee = false OR c.assignedTo = :assignee)
        AND (:hasTransaction = false OR c.transactionId = :transactionId)
        AND (:hasQ = false
          OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(c.summary, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND c.createdAt >= :fromTs
        AND c.createdAt <= :toTs
      ORDER BY c.createdAt DESC
      """)
  Page<ForensicCaseEntity> search(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") ForensicCaseStatus status,
      @Param("hasPriority") boolean hasPriority,
      @Param("priority") ForensicCasePriority priority,
      @Param("hasAssignee") boolean hasAssignee,
      @Param("assignee") UUID assignee,
      @Param("hasTransaction") boolean hasTransaction,
      @Param("transactionId") UUID transactionId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant from,
      @Param("toTs") Instant to,
      Pageable pageable);
}
