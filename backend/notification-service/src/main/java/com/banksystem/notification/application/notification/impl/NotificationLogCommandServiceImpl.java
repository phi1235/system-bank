package com.banksystem.notification.application.notification.impl;

import java.time.Instant;
import com.banksystem.notification.application.notification.NotificationInboxService;
import com.banksystem.notification.application.notification.NotificationLogCommandService;
import com.banksystem.notification.application.notification.NotificationLogCommandService.CreateNotificationLogCommand;
import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.notification.NotificationSandboxController.NotificationSandboxItem;
import com.banksystem.notification.domain.notification.NotificationLogEntity;
import com.banksystem.notification.domain.notification.NotificationLogRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for notification log creation, querying, and real-time SSE dispatch.
 */
@Service
public class NotificationLogCommandServiceImpl implements NotificationLogCommandService {
  private final NotificationLogRepository repository;
  private final NotificationRealtimeHub realtimeHub;

  public NotificationLogCommandServiceImpl(
      NotificationLogRepository repository,
      NotificationRealtimeHub realtimeHub) {
    this.repository = repository;
    this.realtimeHub = realtimeHub;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listLogs(UUID eventId) {
    List<NotificationLogEntity> rows = eventId == null
        ? repository.findTop50ByOrderByCreatedAtDesc()
        : repository.findByEventId(eventId).stream().toList();
    return rows.stream().map(this::mapToViewMap).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<NotificationSandboxItem> searchSandbox(
      String q, String channel, Integer page, Integer size) {
    int pg = page != null ? page : 0;
    int sz = size != null ? Math.min(size, 100) : 20;
    boolean hasQ = q != null && !q.isBlank();
    boolean hasChannel = channel != null && !channel.isBlank();
    PageRequest pageable = PageRequest.of(pg, sz);

    Page<NotificationLogEntity> result = repository.searchSandbox(
        hasQ, hasQ ? q.trim() : null,
        hasChannel, hasChannel ? channel.trim().toUpperCase() : null,
        pageable);

    Page<NotificationSandboxItem> mapped =
        result.map(e -> new NotificationSandboxItem(
            e.getId().toString(),
            e.getChannel(),
            e.getRecipient(),
            e.getTemplate(),
            e.getStatus(),
            e.getBody(),
            e.getUserId() != null ? e.getUserId().toString() : null,
            e.getAudience(),
            e.getCreatedAt()
        ));

    return new PageResponse<>(
        mapped.getContent(),
        mapped.getNumber(),
        mapped.getSize(),
        mapped.getTotalElements(),
        mapped.getTotalPages()
    );
  }

  @Transactional
  public NotificationItem createNotificationLog(CreateNotificationLogCommand cmd) {
    NotificationLogEntity e = new NotificationLogEntity();
    e.setId(UUID.randomUUID());
    e.setEventId(UUID.randomUUID());
    e.setChannel(cmd.channel() == null || cmd.channel().isBlank() ? "EMAIL" : cmd.channel());
    e.setRecipient(cmd.recipient());
    e.setTemplate(cmd.template() == null || cmd.template().isBlank() ? "PASSWORD_RESET" : cmd.template());
    e.setStatus(cmd.status() == null || cmd.status().isBlank() ? "SENT" : cmd.status());
    e.setBody(cmd.body() == null ? "" : cmd.body());
    e.setUserId(cmd.userId());

    String audience = cmd.audience() == null || cmd.audience().isBlank()
        ? NotificationInboxService.AUDIENCE_CUSTOMER
        : cmd.audience().trim().toUpperCase();
    e.setAudience(audience);
    e.setActionType(blankToNull(cmd.actionType()));
    e.setActionId(blankToNull(cmd.actionId()));
    e.setActionPath(blankToNull(cmd.actionPath()));
    e.setCreatedAt(Instant.now());

    repository.save(e);

    NotificationItem item = new NotificationItem(
        e.getId().toString(),
        e.getChannel(),
        e.getTemplate(),
        e.getStatus(),
        e.getBody(),
        false,
        null,
        e.getCreatedAt(),
        e.getActionType(),
        e.getActionId(),
        e.getActionPath());

    dispatchRealtime(e, audience, item);
    return item;
  }

  private void dispatchRealtime(NotificationLogEntity e, String audience, NotificationItem item) {
    if (e.getUserId() != null && NotificationInboxService.AUDIENCE_CUSTOMER.equals(audience)) {
      realtimeHub.publish(e.getUserId(), item);
    } else if (NotificationInboxService.AUDIENCE_OPS.equals(audience)) {
      realtimeHub.publishOps(item);
    }
  }

  private Map<String, Object> mapToViewMap(NotificationLogEntity e) {
    return Map.of(
        "id", e.getId().toString(),
        "eventId", e.getEventId().toString(),
        "channel", e.getChannel(),
        "recipient", e.getRecipient(),
        "template", e.getTemplate(),
        "status", e.getStatus(),
        "body", e.getBody() == null ? "" : e.getBody(),
        "userId", e.getUserId() == null ? "" : e.getUserId().toString(),
        "readAt", e.getReadAt() == null ? "" : e.getReadAt().toString(),
        "createdAt", e.getCreatedAt().toString()
    );
  }

  private static String blankToNull(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    return v.trim();
  }
}
