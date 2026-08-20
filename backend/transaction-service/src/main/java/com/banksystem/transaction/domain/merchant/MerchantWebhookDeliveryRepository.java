package com.banksystem.transaction.domain.merchant;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantWebhookDeliveryRepository extends JpaRepository<MerchantWebhookDeliveryEntity, UUID> {

  Optional<MerchantWebhookDeliveryEntity> findByEndpointIdAndEventId(UUID endpointId, UUID eventId);

  boolean existsByEndpointIdAndEventId(UUID endpointId, UUID eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT d FROM MerchantWebhookDeliveryEntity d WHERE d.id = :id")
  Optional<MerchantWebhookDeliveryEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query(value = """
      SELECT * FROM merchant_webhook_deliveries
      WHERE (status IN ('PENDING', 'RETRYING')
             OR (status = 'SENDING' AND claim_expires_at <= :now))
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        AND retry_count < 5
      ORDER BY created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<MerchantWebhookDeliveryEntity> claimPendingDeliveries(
      @Param("now") Instant now,
      @Param("limit") int limit);
}
