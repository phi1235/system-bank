package com.banksystem.notification.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {

  private final NotificationLogRepository repository;

  public NotificationInboxService(NotificationLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResponse<NotificationItem> myInbox(UUID userId, int page, int size) {
    Page<NotificationLogEntity> p =
        repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    List<NotificationItem> items = p.getContent().stream().map(this::toItem).toList();
    return new PageResponse<>(
        items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return repository.countByUserIdAndReadAtIsNull(userId);
  }

  @Transactional
  public NotificationItem markRead(UUID userId, UUID id) {
    NotificationLogEntity e = repository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(
            "NOTIFICATION_NOT_FOUND", "Notification not found", HttpStatus.NOT_FOUND));
    if (e.getReadAt() == null) {
      e.setReadAt(Instant.now());
      e = repository.save(e);
    }
    return toItem(e);
  }

  @Transactional
  public int markAllRead(UUID userId) {
    return repository.markAllRead(userId);
  }

  private NotificationItem toItem(NotificationLogEntity e) {
    return new NotificationItem(
        e.getId().toString(),
        e.getChannel(),
        e.getTemplate(),
        e.getStatus(),
        e.getBody() == null ? "" : e.getBody(),
        e.getReadAt() != null,
        e.getReadAt(),
        e.getCreatedAt());
  }
}
