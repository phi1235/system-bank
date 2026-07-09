package com.banksystem.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {
  Optional<NotificationLogEntity> findByEventId(UUID eventId);

  List<NotificationLogEntity> findTop50ByOrderByCreatedAtDesc();
}
