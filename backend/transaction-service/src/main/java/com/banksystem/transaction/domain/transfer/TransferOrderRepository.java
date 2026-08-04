package com.banksystem.transaction.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrderEntity, UUID> {
  Optional<TransferOrderEntity> findByIdempotencyKey(String idempotencyKey);

  Page<TransferOrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  long countByStatus(TransferStatus status);

  /** Reconciliation: all orders of one banking day, [from, to) in UTC instants. */
  java.util.List<TransferOrderEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
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
}
