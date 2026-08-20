package com.banksystem.corporate.domain.receipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptArtifactRepository extends JpaRepository<ReceiptArtifactEntity, UUID> {
  List<ReceiptArtifactEntity> findByBatchId(UUID batchId);
  List<ReceiptArtifactEntity> findByCorporateIdAndBatchId(UUID corporateId, UUID batchId);
  Optional<ReceiptArtifactEntity> findByCorporateIdAndId(UUID corporateId, UUID id);
  Optional<ReceiptArtifactEntity> findByItemId(UUID itemId);
  Optional<ReceiptArtifactEntity> findByBatchIdAndArtifactType(UUID batchId, String artifactType);
  List<ReceiptArtifactEntity> findByEmailSentFalse();

  @Query(value = """
      SELECT id FROM receipt_artifacts
      WHERE email_status IN ('PENDING', 'SENDING')
        AND email_next_attempt_at <= :now
        AND (email_lease_until IS NULL OR email_lease_until <= :now)
      ORDER BY created_at
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimPendingEmailIds(@Param("now") Instant now, @Param("limit") int limit);

  @Modifying
  @Query("""
      UPDATE ReceiptArtifactEntity r
      SET r.emailStatus = 'SENDING',
          r.emailClaimedBy = :worker,
          r.emailLeaseUntil = :leaseUntil
      WHERE r.id IN :ids
      """)
  int markEmailSending(
      @Param("ids") List<UUID> ids,
      @Param("worker") String worker,
      @Param("leaseUntil") Instant leaseUntil);
}
