package com.banksystem.notification.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates shared staff OPS alerts (audience=OPS) and pushes SSE via {@link NotificationRealtimeHub}.
 */
@Service
public class OpsAlertService {

  private final NotificationLogRepository repository;
  private final NotificationRealtimeHub realtimeHub;

  public OpsAlertService(
      NotificationLogRepository repository,
      NotificationRealtimeHub realtimeHub) {
    this.repository = repository;
    this.realtimeHub = realtimeHub;
  }

  @Transactional
  public NotificationItem create(CreateOpsAlertRequest req) {
    UUID eventId = resolveEventId(req.eventId());
    Optional<NotificationLogEntity> existing = repository.findByEventId(eventId);
    if (existing.isPresent()) {
      return toItem(existing.get());
    }

    NotificationLogEntity ops = new NotificationLogEntity();
    ops.setId(UUID.randomUUID());
    ops.setEventId(eventId);
    ops.setChannel("OPS");
    ops.setRecipient("ops@bank.local");
    ops.setTemplate(req.template().trim());
    ops.setStatus("OPEN");
    ops.setBody(req.body().trim());
    ops.setUserId(null);
    ops.setAudience(NotificationInboxService.AUDIENCE_OPS);
    ops.setCreatedAt(Instant.now());

    try {
      ops = repository.save(ops);
    } catch (DataIntegrityViolationException dup) {
      return repository.findByEventId(eventId)
          .map(this::toItem)
          .orElseThrow(() -> new BusinessException(
              "OPS_ALERT_CONFLICT", "Ops alert conflict", HttpStatus.CONFLICT));
    }

    NotificationItem item = toItem(ops);
    realtimeHub.publishOps(item);
    return item;
  }

  private static UUID resolveEventId(String raw) {
    if (raw == null || raw.isBlank()) {
      return UUID.randomUUID();
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_EVENT_ID", "eventId must be a UUID", HttpStatus.BAD_REQUEST);
    }
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
