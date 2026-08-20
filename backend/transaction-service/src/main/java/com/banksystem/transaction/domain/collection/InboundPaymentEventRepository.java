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
      WHERE (:provider IS NULL OR e.provider = :provider)
        AND (:q IS NULL OR LOWER(e.virtualAccountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(e.providerTransactionId) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(e.referenceContent) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:status IS NULL OR e.status = :status)
      ORDER BY e.createdAt DESC
      """)
  Page<InboundPaymentEventEntity> search(
      @Param("provider") String provider,
      @Param("q") String q,
      @Param("status") InboundPaymentStatus status,
      Pageable pageable);

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
