package com.banksystem.notification.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for notification log creation, querying, and real-time SSE dispatch.
 */
@Service
public class NotificationLogCommandService {

  public record CreateNotificationLogCommand(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      UUID userId,
      String audience,
      String actionType,
      String actionId,
      String actionPath
  ) {}

  private final NotificationLogRepository repository;
  private final NotificationRealtimeHub realtimeHub;
  private final String apiKey;

  public NotificationLogCommandService(
      NotificationLogRepository repository,
      NotificationRealtimeHub realtimeHub,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.repository = repository;
    this.realtimeHub = realtimeHub;
    this.apiKey = apiKey;
  }

  public void verifyInternalKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listLogs(UUID eventId) {
    List<NotificationLogEntity> rows = eventId == null
        ? repository.findTop50ByOrderByCreatedAtDesc()
        : repository.findByEventId(eventId).stream().toList();
    return rows.stream().map(this::mapToViewMap).toList();
  }

  @Transactional(readOnly = true)
  public com.banksystem.common.api.PageResponse<com.banksystem.notification.api.NotificationSandboxController.NotificationSandboxItem> searchSandbox(
      String q, String channel, int page, int size) {
    boolean hasQ = q != null && !q.isBlank();
    boolean hasChannel = channel != null && !channel.isBlank();
    org.springframework.data.domain.PageRequest pageable =
        org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));

    org.springframework.data.domain.Page<NotificationLogEntity> result = repository.searchSandbox(
        hasQ, hasQ ? q.trim() : null,
        hasChannel, hasChannel ? channel.trim().toUpperCase() : null,
        pageable);

    org.springframework.data.domain.Page<com.banksystem.notification.api.NotificationSandboxController.NotificationSandboxItem> mapped =
        result.map(e -> new com.banksystem.notification.api.NotificationSandboxController.NotificationSandboxItem(
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

    return new com.banksystem.common.api.PageResponse<>(
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
