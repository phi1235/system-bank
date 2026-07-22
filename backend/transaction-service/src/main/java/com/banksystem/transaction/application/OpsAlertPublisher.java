package com.banksystem.transaction.application;

import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.infrastructure.feign.NotificationClient;
import com.banksystem.transaction.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort OPS alerts to notification-service. Failures are logged only so outbox / freeze paths
 * are never blocked by alert delivery.
 */
@Service
public class OpsAlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(OpsAlertPublisher.class);

  public static final String TEMPLATE_OUTBOX_DEAD = "OPS_OUTBOX_DEAD";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public OpsAlertPublisher(
      NotificationClient notificationClient,
      @Value("${bank.internal.notification-api-key}") String apiKey) {
    this.notificationClient = notificationClient;
    this.apiKey = apiKey;
  }

  public void outboxDead(OutboxEventEntity event) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-outbox-dead:" + event.getId()).getBytes(StandardCharsets.UTF_8));
    String body = "Outbox event DEAD"
        + " outboxId=" + event.getId()
        + " type=" + nullToNa(event.getEventType())
        + " aggregateId=" + event.getAggregateId()
        + " attempts=" + event.getAttemptCount()
        + " error=" + nullToNa(event.getLastError());
    publishQuietly(eventId, TEMPLATE_OUTBOX_DEAD, body);
  }

  private void publishQuietly(UUID eventId, String template, String body) {
    try {
      notificationClient.createOpsAlert(
          new CreateOpsAlertRequest(eventId.toString(), template, body),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish OPS alert template={}: {}", template, ex.getMessage());
    }
  }

  private static String nullToNa(String v) {
    return v == null || v.isBlank() ? "n/a" : v;
  }
}
