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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {

  public static final String AUDIENCE_CUSTOMER = "CUSTOMER";
  public static final String AUDIENCE_OPS = "OPS";

  private final NotificationLogRepository repository;

  public NotificationInboxService(NotificationLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResponse<NotificationItem> myInbox(UUID userId, int page, int size) {
    return myInbox(userId, page, size, null);
  }

  /**
   * Customer inbox with optional read-state filter.
   *
   * @param readFilter {@code null}/blank = all, {@code UNREAD}, or {@code READ}
   */
  @Transactional(readOnly = true)
  public PageResponse<NotificationItem> myInbox(
      UUID userId, Integer page, Integer size, String readFilter) {
    int pg = page != null ? page : 0;
    int sz = size != null ? Math.min(size, 100) : 20;
    Pageable pageable = PageRequest.of(pg, sz);
    String filter = readFilter == null ? "" : readFilter.trim().toUpperCase();
    Page<NotificationLogEntity> p;
    if ("UNREAD".equals(filter)) {
      p = repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable);
    } else if ("READ".equals(filter)) {
      p = repository.findByUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(userId, pageable);
    } else {
      p = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    return toPage(p);
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
            "NOTIFICATION_NOT_FOUND", "Notification not found"));
    return markReadEntity(e);
  }

  @Transactional
  public int markAllRead(UUID userId) {
    return repository.markAllRead(userId);
  }

  @Transactional(readOnly = true)
  public PageResponse<NotificationItem> opsInbox(Integer page, Integer size) {
    int p = page != null ? page : 0;
    int s = size != null ? Math.min(size, 100) : 20;
    Page<NotificationLogEntity> pp =
        repository.findByAudienceOrderByCreatedAtDesc(AUDIENCE_OPS, PageRequest.of(p, s));
    return toPage(pp);
  }

  @Transactional(readOnly = true)
  public long opsUnreadCount() {
    return repository.countByAudienceAndReadAtIsNull(AUDIENCE_OPS);
  }

  @Transactional
  public NotificationItem markOpsRead(UUID id) {
    NotificationLogEntity e = repository
        .findByIdAndAudience(id, AUDIENCE_OPS)
        .orElseThrow(() -> new BusinessException(
            "NOTIFICATION_NOT_FOUND", "Ops notification not found"));
    return markReadEntity(e);
  }

  @Transactional
  public int markAllOpsRead() {
    return repository.markAllReadByAudience(AUDIENCE_OPS);
  }

  private NotificationItem markReadEntity(NotificationLogEntity e) {
    if (e.getReadAt() == null) {
      e.setReadAt(Instant.now());
      e = repository.save(e);
    }
    return toItem(e);
  }

  private PageResponse<NotificationItem> toPage(Page<NotificationLogEntity> p) {
    List<NotificationItem> items = p.getContent().stream().map(this::toItem).toList();
    return new PageResponse<>(
        items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
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
        e.getCreatedAt(),
        e.getActionType(),
        e.getActionId(),
        e.getActionPath());
  }
}
