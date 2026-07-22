package com.banksystem.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrderEntity, UUID> {
  Optional<TransferOrderEntity> findByIdempotencyKey(String idempotencyKey);

  Page<TransferOrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

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

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE (:status IS NULL OR t.status = :status)
      ORDER BY t.createdAt DESC
      """)
  Page<TransferOrderEntity> adminSearch(@Param("status") TransferStatus status, Pageable pageable);

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
