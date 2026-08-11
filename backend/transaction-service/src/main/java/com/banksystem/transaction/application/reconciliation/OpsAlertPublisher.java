package com.banksystem.transaction.application.reconciliation;

import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.infrastructure.feign.NotificationClient;
import com.banksystem.transaction.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Best-effort OPS alerts to notification-service. Failures are logged only so outbox / freeze paths
 * are never blocked by alert delivery.
 */
@Service
public class OpsAlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(OpsAlertPublisher.class);

  public static final String TEMPLATE_OUTBOX_DEAD = "OPS_OUTBOX_DEAD";
  public static final String TEMPLATE_RISK_DETECTED = "OPS_RISK_DETECTED";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public OpsAlertPublisher(
      @Lazy NotificationClient notificationClient,
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

  public void riskDetected(
      TransferOrderEntity order, String decision, int score, List<String> matchedRules, String reason) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-risk:" + order.getId() + ":" + decision).getBytes(StandardCharsets.UTF_8));
    String body = "Risk " + decision
        + " transferId=" + order.getId()
        + " score=" + score
        + " rules=" + String.join(",", matchedRules)
        + " reason=" + nullToNa(reason);
    publishQuietly(eventId, TEMPLATE_RISK_DETECTED, body);
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
