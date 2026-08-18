package com.banksystem.notification.application.notification.impl;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.application.notification.OpsAlertService;
import com.banksystem.notification.domain.notification.NotificationLogEntity;
import com.banksystem.notification.domain.notification.NotificationLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OpsAlertServiceImpl implements OpsAlertService {

  private final NotificationLogRepository repository;
  private final NotificationRealtimeHub realtimeHub;
  private final String opsRecipient;
  private final Clock clock;

  public OpsAlertServiceImpl(
      NotificationLogRepository repository,
      NotificationRealtimeHub realtimeHub,
      @Value("${bank.notification.ops-recipient}") String opsRecipient,
      Clock clock) {
    this.repository = repository;
    this.realtimeHub = realtimeHub;
    this.opsRecipient = opsRecipient;
    this.clock = clock;
  }

  @Override
  @Transactional
  public NotificationItem create(CreateOpsAlertRequest request) {
    UUID eventId = resolveEventId(request.eventId());
    Instant now = clock.instant();
    repository.insertOpsAlert(
        UUID.randomUUID(),
        eventId,
        opsRecipient,
        request.template().trim(),
        request.body().trim(),
        blankToNull(request.actionType()),
        blankToNull(request.actionId()),
        blankToNull(request.actionPath()),
        now);

    NotificationLogEntity entity = repository.findByEventId(eventId)
        .orElseThrow(() -> new BusinessException(
            "OPS_ALERT_PERSISTENCE_FAILED", "Ops alert could not be persisted"));
    NotificationItem item = toItem(entity);
    afterCommit(() -> realtimeHub.publishOps(item));
    return item;
  }

  private static UUID resolveEventId(String raw) {
    if (raw == null || raw.isBlank()) {
      return UUID.randomUUID();
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("INVALID_EVENT_ID", "eventId must be a UUID");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }

  private static NotificationItem toItem(NotificationLogEntity entity) {
    return new NotificationItem(
        entity.getId().toString(), entity.getChannel(), entity.getTemplate(), entity.getStatus(),
        entity.getBody() == null ? "" : entity.getBody(), entity.getReadAt() != null,
        entity.getReadAt(), entity.getCreatedAt(), entity.getActionType(), entity.getActionId(),
        entity.getActionPath());
  }
}
