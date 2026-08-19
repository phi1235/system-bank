package com.banksystem.transaction.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrderEntity, UUID> {
  @Query(
      value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text), 0))",
      nativeQuery = true)
  Object lockRiskVelocity(@Param("userId") UUID userId);

  Optional<TransferOrderEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<TransferOrderEntity> findByProviderReferenceId(String providerReferenceId);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE t.status IN :statuses
        AND (t.nextReconciliationAt IS NULL OR t.nextReconciliationAt <= :now)
        AND t.reconciliationAttempts < 5
      ORDER BY t.createdAt ASC
      """)
  List<TransferOrderEntity> findReconciliationEligible(
      @Param("now") Instant now,
      @Param("statuses") List<TransferStatus> statuses);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE t.status IN ('MANUAL_REVIEW', 'REVIEW_REQUIRED')
      ORDER BY t.updatedAt DESC
      """)
  Page<TransferOrderEntity> findManualReviewOrders(Pageable pageable);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE t.status = 'COMPLETED'
        AND t.feeAmount > 0
        AND t.feeEntryRef = 'PENDING_RECON'
      ORDER BY t.createdAt ASC
      """)
  List<TransferOrderEntity> findPendingFeeGlOrders();

  List<TransferOrderEntity> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
      TransferStatus status, Instant updatedBefore);

  Slice<TransferOrderEntity> findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
      Instant fromInclusive, Instant toInclusive, Pageable pageable);

  Page<TransferOrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  long countByStatus(TransferStatus status);

  /** Reconciliation: all orders of one banking day, [from, to) in UTC instants. */
  List<TransferOrderEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
      Instant from, Instant to);

  /**
   * History search. Callers must pass non-null from/to bounds (use epoch/far-future when
   * the UI omits a range). Optional status uses a boolean flag so Postgres never sees an
   * untyped NULL enum/timestamp bind ({@code could not determine data type of parameter}).
   */
  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE t.userId = :userId
        AND (:hasStatus = false OR t.status = :status)
        AND t.createdAt >= :fromTs
        AND t.createdAt <= :toTs
      ORDER BY t.createdAt DESC
      """)
  Page<TransferOrderEntity> searchMine(
      @Param("userId") UUID userId,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") TransferStatus status,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  /**
   * Admin transfer search. Callers pass concrete from/to bounds and boolean flags so Postgres
   * never sees untyped NULL enum/UUID binds.
   */
  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE (:hasStatus = false OR t.status = :status)
        AND (:hasTransferId = false OR t.id = :transferId)
        AND (:hasQ = false
          OR LOWER(t.toAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND t.createdAt >= :fromTs
        AND t.createdAt <= :toTs
      ORDER BY t.createdAt DESC
      """)
  Page<TransferOrderEntity> adminSearch(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") TransferStatus status,
      @Param("hasTransferId") boolean hasTransferId,
      @Param("transferId") UUID transferId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE (:hasStatus = false OR t.status = :status)
        AND (:hasTransferId = false OR t.id = :transferId)
        AND (:hasAccountId = false OR t.fromAccountId = :accountId OR t.toAccountId = :accountId)
        AND (:hasRiskDecision = false OR UPPER(COALESCE(t.riskDecision, '')) = :riskDecision)
        AND (:hasQ = false
          OR LOWER(t.toAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.targetAccountName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.providerReferenceId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND t.createdAt >= :fromTs
        AND t.createdAt <= :toTs
      ORDER BY t.createdAt DESC
      """)
  Page<TransferOrderEntity> searchForensics(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") TransferStatus status,
      @Param("hasTransferId") boolean hasTransferId,
      @Param("transferId") UUID transferId,
      @Param("hasAccountId") boolean hasAccountId,
      @Param("accountId") UUID accountId,
      @Param("hasRiskDecision") boolean hasRiskDecision,
      @Param("riskDecision") String riskDecision,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE (:hasStatus = false OR t.status = :status)
        AND (:hasTransferId = false OR t.id = :transferId)
        AND (:hasQ = false
          OR LOWER(t.toAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND t.createdAt >= :fromTs
        AND t.createdAt <= :toTs
        AND (:hasLastTs = false OR t.createdAt < :lastTs)
      ORDER BY t.createdAt DESC
      """)
  Slice<TransferOrderEntity> adminSearchSlice(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") TransferStatus status,
      @Param("hasTransferId") boolean hasTransferId,
      @Param("transferId") UUID transferId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("hasLastTs") boolean hasLastTs,
      @Param("lastTs") Instant lastTs,
      Pageable pageable);

  @Query("""
      SELECT COALESCE(SUM(t.amount), 0)
      FROM TransferOrderEntity t
      WHERE t.userId = :userId
        AND t.status = :status
        AND t.createdAt >= :fromInclusive
      """)
  BigDecimal sumAmountByUserAndStatusSince(
      @Param("userId") UUID userId,
      @Param("status") TransferStatus status,
      @Param("fromInclusive") Instant fromInclusive);

  @Query("""
      SELECT COUNT(t) FROM TransferOrderEntity t
      WHERE t.userId = :userId
        AND t.createdAt >= :fromInclusive
        AND t.status NOT IN :excludedStatuses
      """)
  long countRiskVelocity(
      @Param("userId") UUID userId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("excludedStatuses") List<TransferStatus> excludedStatuses);

  @Query("""
      SELECT COALESCE(SUM(t.amount), 0) FROM TransferOrderEntity t
      WHERE t.userId = :userId
        AND t.createdAt >= :fromInclusive
        AND t.status NOT IN :excludedStatuses
      """)
  BigDecimal sumRiskVelocity(
      @Param("userId") UUID userId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("excludedStatuses") List<TransferStatus> excludedStatuses);
}
