package com.banksystem.transaction.domain.settlement;

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

public interface B2bPayoutRepository extends JpaRepository<B2bPayoutEntity, UUID> {
  Optional<B2bPayoutEntity> findBySettlementLegId(UUID settlementLegId);
  Optional<B2bPayoutEntity> findByClientRequestId(String clientRequestId);
  List<B2bPayoutEntity> findByOrganizationId(UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM B2bPayoutEntity p WHERE p.id = :id")
  Optional<B2bPayoutEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query(value = """
      SELECT * FROM b2b_payouts
      WHERE status = 'READY'
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
      ORDER BY created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<B2bPayoutEntity> claimReadyPayouts(@Param("now") Instant now, @Param("limit") int limit);

  @Query(value = """
      SELECT * FROM b2b_payouts
      WHERE status = 'DISPATCHING'
        AND claim_expires_at <= :now
      ORDER BY claim_expires_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<B2bPayoutEntity> claimExpiredDispatchingPayouts(@Param("now") Instant now, @Param("limit") int limit);

  @Query(value = """
      SELECT * FROM b2b_payouts
      WHERE (status = 'PENDING_RECON' OR status = 'SWITCH_SUCCESS_LEDGER_PENDING')
        AND (claim_token IS NULL OR claim_expires_at IS NULL OR claim_expires_at <= :now)
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
      ORDER BY created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<B2bPayoutEntity> claimPendingReconPayouts(@Param("now") Instant now, @Param("limit") int limit);

  @Query("""
      SELECT p FROM B2bPayoutEntity p
      WHERE (:orgId IS NULL OR p.organizationId = :orgId)
        AND (:status IS NULL OR p.status = :status)
      ORDER BY p.createdAt DESC
      """)
  Page<B2bPayoutEntity> search(
      @Param("orgId") UUID orgId,
      @Param("status") B2bPayoutStatus status,
      Pageable pageable);

  List<B2bPayoutEntity> findByStatusAndCreatedAtBefore(B2bPayoutStatus status, Instant cutoff);
}
