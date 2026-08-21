package com.banksystem.transaction.domain.collection;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboundPaymentEventRepository extends JpaRepository<InboundPaymentEventEntity, UUID> {

  Optional<InboundPaymentEventEntity> findByProviderAndProviderTransactionId(
      String provider, String providerTransactionId);

  boolean existsByProviderAndProviderTransactionId(String provider, String providerTransactionId);

  @Query("""
      SELECT e FROM InboundPaymentEventEntity e
      WHERE (:hasProvider = false OR e.provider = :provider)
        AND (:hasQ = false OR (
            LOWER(e.virtualAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (e.providerTransactionId IS NOT NULL AND LOWER(e.providerTransactionId) LIKE LOWER(CONCAT('%', :q, '%')))
            OR (e.referenceContent IS NOT NULL AND LOWER(e.referenceContent) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR e.status = :status)
      ORDER BY e.createdAt DESC
      """)
  List<InboundPaymentEventEntity> searchList(
      @Param("hasProvider") boolean hasProvider,
      @Param("provider") String provider,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") InboundPaymentStatus status);

  @Query("""
      SELECT e FROM InboundPaymentEventEntity e
      WHERE (:hasProvider = false OR e.provider = :provider)
        AND (:hasQ = false OR (
            LOWER(e.virtualAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (e.providerTransactionId IS NOT NULL AND LOWER(e.providerTransactionId) LIKE LOWER(CONCAT('%', :q, '%')))
            OR (e.referenceContent IS NOT NULL AND LOWER(e.referenceContent) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR e.status = :status)
      ORDER BY e.createdAt DESC
      """)
  Page<InboundPaymentEventEntity> search(
      @Param("hasProvider") boolean hasProvider,
      @Param("provider") String provider,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") InboundPaymentStatus status,
      Pageable pageable);

  long countByStatus(InboundPaymentStatus status);

  List<InboundPaymentEventEntity> findByStatusAndCreatedAtBefore(InboundPaymentStatus status, Instant cutoff);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM InboundPaymentEventEntity e WHERE e.id = :id")
  Optional<InboundPaymentEventEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query(value = """
      SELECT * FROM inbound_payment_events
      WHERE status IN ('LEDGER_PENDING', 'FINALIZE_PENDING', 'PENDING_RECOVERY')
        AND (claim_token IS NULL OR claim_expires_at IS NULL OR claim_expires_at <= :now)
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        AND retry_count < 5
      ORDER BY created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<InboundPaymentEventEntity> claimPendingEvents(@Param("now") Instant now, @Param("limit") int limit);
}
