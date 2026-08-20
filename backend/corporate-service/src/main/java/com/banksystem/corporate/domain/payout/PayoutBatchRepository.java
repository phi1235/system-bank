package com.banksystem.corporate.domain.payout;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatchEntity, UUID> {
  Optional<PayoutBatchEntity> findByCorporateIdAndId(UUID corporateId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM PayoutBatchEntity b WHERE b.corporateId = :corporateId AND b.id = :id")
  Optional<PayoutBatchEntity> findByCorporateIdAndIdForUpdate(
      @Param("corporateId") UUID corporateId,
      @Param("id") UUID id);

  Optional<PayoutBatchEntity> findByCorporateIdAndSourceAccountIdAndFileSha256(
      UUID corporateId, UUID sourceAccountId, String fileSha256);

  List<PayoutBatchEntity> findByCorporateIdOrderByCreatedAtDesc(UUID corporateId);

  Page<PayoutBatchEntity> findByCorporateIdOrderByCreatedAtDesc(UUID corporateId, Pageable pageable);

  Page<PayoutBatchEntity> findByCorporateIdAndStatusOrderByCreatedAtDesc(
      UUID corporateId, String status, Pageable pageable);

  List<PayoutBatchEntity> findByStatusOrderByUpdatedAtAsc(String status);

  @Query("""
      SELECT COALESCE(SUM(b.totalAmount + b.totalFee), 0)
      FROM PayoutBatchEntity b
      WHERE b.corporateId = :corporateId
        AND b.sourceAccountId = :accountId
        AND b.id <> :excludedBatchId
        AND b.submittedAt >= :dayStart
        AND b.status IN ('PENDING_APPROVAL', 'APPROVED', 'RESERVING_FUNDS', 'PROCESSING', 'COMPLETED', 'PARTIALLY_COMPLETED')
      """)
  BigDecimal sumCommittedPayoutForDay(
      @Param("corporateId") UUID corporateId,
      @Param("accountId") UUID accountId,
      @Param("excludedBatchId") UUID excludedBatchId,
      @Param("dayStart") Instant dayStart);

  @Query("SELECT b FROM PayoutBatchEntity b WHERE b.status IN ('PROCESSING', 'RESERVING_FUNDS') ORDER BY b.updatedAt ASC")
  List<PayoutBatchEntity> findActiveProcessingBatches();

  @Query(value = """
      SELECT id FROM payout_batches
      WHERE status IN ('APPROVED', 'RESERVING_FUNDS')
        AND (hold_next_retry_at IS NULL OR hold_next_retry_at <= :now)
        AND (worker_lease_until IS NULL OR worker_lease_until <= :now)
      ORDER BY updated_at
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimReservableBatchIds(@Param("now") Instant now, @Param("limit") int limit);

  @Query(value = """
      SELECT id FROM payout_batches
      WHERE status = 'PROCESSING'
        AND (worker_lease_until IS NULL OR worker_lease_until <= :now)
      ORDER BY updated_at
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimProcessingBatchIds(@Param("now") Instant now, @Param("limit") int limit);

  @Modifying
  @Query("""
      UPDATE PayoutBatchEntity b
      SET b.status = :status,
          b.workerClaimedBy = :worker,
          b.workerLeaseUntil = :leaseUntil,
          b.updatedAt = :now
      WHERE b.id IN :ids
      """)
  int markBatchesClaimed(
      @Param("ids") List<UUID> ids,
      @Param("status") String status,
      @Param("worker") String worker,
      @Param("leaseUntil") Instant leaseUntil,
      @Param("now") Instant now);

  @Query(value = """
      SELECT b.id FROM payout_batches b
      WHERE b.status IN ('COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED')
        AND NOT EXISTS (
          SELECT 1 FROM receipt_artifacts r
          WHERE r.batch_id = b.id
            AND r.artifact_type = 'CONSOLIDATED_BATCH_REPORT'
        )
      ORDER BY b.completed_at NULLS LAST
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> findCompletedBatchIdsMissingReport(@Param("limit") int limit);
}
