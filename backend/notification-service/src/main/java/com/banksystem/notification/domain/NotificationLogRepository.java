package com.banksystem.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {
  Optional<NotificationLogEntity> findByEventId(UUID eventId);

  List<NotificationLogEntity> findTop50ByOrderByCreatedAtDesc();

  Page<NotificationLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<NotificationLogEntity> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      UPDATE NotificationLogEntity n
      SET n.readAt = CURRENT_TIMESTAMP
      WHERE n.userId = :userId AND n.readAt IS NULL
      """)
  int markAllRead(@Param("userId") UUID userId);

  Page<NotificationLogEntity> findByAudienceOrderByCreatedAtDesc(String audience, Pageable pageable);

  Optional<NotificationLogEntity> findByIdAndAudience(UUID id, String audience);

  long countByAudienceAndReadAtIsNull(String audience);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      UPDATE NotificationLogEntity n
      SET n.readAt = CURRENT_TIMESTAMP
      WHERE n.audience = :audience AND n.readAt IS NULL
      """)
  int markAllReadByAudience(@Param("audience") String audience);
}
