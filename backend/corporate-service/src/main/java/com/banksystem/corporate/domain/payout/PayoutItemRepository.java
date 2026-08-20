package com.banksystem.corporate.domain.payout;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutItemRepository extends JpaRepository<PayoutItemEntity, UUID> {
  Optional<PayoutItemEntity> findByBatchIdAndRowNumber(UUID batchId, int rowNumber);
  Optional<PayoutItemEntity> findByIdempotencyKey(String idempotencyKey);

  List<PayoutItemEntity> findByBatchIdOrderByRowNumberAsc(UUID batchId);
  List<PayoutItemEntity> findByBatchIdAndStatus(UUID batchId, String status);
  Page<PayoutItemEntity> findByBatchIdOrderByRowNumberAsc(UUID batchId, Pageable pageable);
  Page<PayoutItemEntity> findByBatchIdAndStatusOrderByRowNumberAsc(UUID batchId, String status, Pageable pageable);

  long countByBatchIdAndStatus(UUID batchId, String status);

  @Query(value = """
      SELECT id FROM payout_items
      WHERE batch_id = :batchId
        AND status IN ('QUEUED', 'RETRY_WAIT', 'CLAIMED')
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        AND (lease_until IS NULL OR lease_until <= :now)
      ORDER BY row_number ASC
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimBatchItems(
      @Param("batchId") UUID batchId,
      @Param("now") Instant now,
      @Param("limit") int limit);

  @Query(value = """
      SELECT id FROM payout_items
      WHERE batch_id = :batchId
        AND status = 'RECONCILING'
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        AND (lease_until IS NULL OR lease_until <= :now)
      ORDER BY row_number ASC
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimReconciliationItems(
      @Param("batchId") UUID batchId,
      @Param("now") Instant now,
      @Param("limit") int limit);

  @Modifying
  @Query("""
      UPDATE PayoutItemEntity i
      SET i.status = 'CLAIMED',
          i.claimedBy = :claimedBy,
          i.claimedAt = :now,
          i.leaseUntil = :leaseUntil,
          i.updatedAt = :now
      WHERE i.id IN :ids
      """)
  int markItemsClaimed(
      @Param("ids") List<UUID> ids,
      @Param("claimedBy") String claimedBy,
      @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil);

  @Query(value = """
      SELECT id FROM payout_items
      WHERE status = 'SUCCESS' AND receipt_artifact_id IS NULL
      ORDER BY processed_at NULLS LAST, row_number
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> findSuccessfulItemIdsMissingReceipt(@Param("limit") int limit);

  @Modifying
  @Query("""
      UPDATE PayoutItemEntity i
      SET i.status = 'QUEUED', i.updatedAt = :now
      WHERE i.batchId = :batchId AND i.status = 'VALID'
      """)
  int queueValidItems(@Param("batchId") UUID batchId, @Param("now") Instant now);
}
