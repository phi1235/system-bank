package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates shared staff OPS alerts (audience=OPS) and pushes SSE via {@link NotificationRealtimeHub}.
 */
@Service
public class OpsAlertServiceImpl implements OpsAlertService {

  private final NotificationLogRepository repository;
  private final NotificationRealtimeHub realtimeHub;

  public OpsAlertServiceImpl(
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
    ops.setActionType(blankToNull(req.actionType()));
    ops.setActionId(blankToNull(req.actionId()));
    ops.setActionPath(blankToNull(req.actionPath()));
    ops.setCreatedAt(Instant.now());

    try {
      ops = repository.save(ops);
    } catch (DataIntegrityViolationException dup) {
      return repository.findByEventId(eventId)
          .map(this::toItem)
          .orElseThrow(() -> new BusinessException(
              "OPS_ALERT_CONFLICT", "Ops alert conflict"));
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
          "INVALID_EVENT_ID", "eventId must be a UUID");
    }
  }

  private static String blankToNull(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    return v.trim();
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
