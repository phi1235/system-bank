package com.banksystem.notification.domain.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository
    extends JpaRepository<NotificationDeliveryEntity, UUID> {

  @Query(value = """
      SELECT id
      FROM notification_deliveries
      WHERE status IN ('PENDING', 'PROCESSING')
        AND next_attempt_at <= :now
        AND (lease_until IS NULL OR lease_until <= :now)
      ORDER BY next_attempt_at, created_at
      FOR UPDATE SKIP LOCKED
      LIMIT :batchSize
      """, nativeQuery = true)
  List<UUID> claimDueIds(
      @Param("now") Instant now,
      @Param("batchSize") int batchSize);

  @Modifying
  @Query("""
      UPDATE NotificationDeliveryEntity d
      SET d.status = 'PROCESSING', d.claimedBy = :worker, d.leaseUntil = :leaseUntil
      WHERE d.id IN :ids
      """)
  int markProcessing(
      @Param("ids") List<UUID> ids,
      @Param("worker") String worker,
      @Param("leaseUntil") Instant leaseUntil);

  long countByEventIdAndStatusNot(UUID eventId, String status);

  @Modifying
  @Query(value = """
      INSERT INTO notification_deliveries (
        id, event_id, channel, destination, subject, body,
        attachment_filename, attachment_content, status, attempt_count,
        next_attempt_at, created_at, updated_at
      ) VALUES (
        :id, :eventId, :channel, :destination, :subject, :body,
        :attachmentFilename, :attachmentContent, 'PENDING', 0,
        :now, :now, :now
      )
      ON CONFLICT (event_id, channel) DO NOTHING
      """, nativeQuery = true)
  int enqueueIdempotently(
      @Param("id") UUID id,
      @Param("eventId") UUID eventId,
      @Param("channel") String channel,
      @Param("destination") String destination,
      @Param("subject") String subject,
      @Param("body") String body,
      @Param("attachmentFilename") String attachmentFilename,
      @Param("attachmentContent") byte[] attachmentContent,
      @Param("now") Instant now);
}
