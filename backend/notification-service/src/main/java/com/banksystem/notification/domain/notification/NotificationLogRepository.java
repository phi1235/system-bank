package com.banksystem.notification.domain.notification;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

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

  Page<NotificationLogEntity> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
      UUID userId, Pageable pageable);

  Page<NotificationLogEntity> findByUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(
      UUID userId, Pageable pageable);

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
  @Query("""
      SELECT n FROM NotificationLogEntity n
      WHERE (:hasQ = false OR LOWER(n.recipient) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.body) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(n.template) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:hasChannel = false OR n.channel = :channel)
      ORDER BY n.createdAt DESC
      """)
  Page<NotificationLogEntity> searchSandbox(
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasChannel") boolean hasChannel,
      @Param("channel") String channel,
      Pageable pageable);
}
