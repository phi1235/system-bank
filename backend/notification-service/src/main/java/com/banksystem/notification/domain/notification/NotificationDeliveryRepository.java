package com.banksystem.notification.domain.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository
    extends JpaRepository<NotificationDeliveryEntity, UUID> {

  @Query(value = """
      SELECT *
      FROM notification_deliveries
      WHERE status = 'PENDING' AND next_attempt_at <= :now
      ORDER BY next_attempt_at, created_at
      FOR UPDATE SKIP LOCKED
      LIMIT :batchSize
      """, nativeQuery = true)
  List<NotificationDeliveryEntity> findDueForUpdate(
      @Param("now") Instant now,
      @Param("batchSize") int batchSize);

  long countByEventIdAndStatusNot(UUID eventId, String status);
}
