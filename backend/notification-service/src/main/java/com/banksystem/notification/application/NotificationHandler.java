package com.banksystem.notification.application;

import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import com.banksystem.notification.domain.ProcessedEventEntity;
import com.banksystem.notification.domain.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final MockEmailSender emailSender;
  private final MockSmsSender smsSender;
  private final ObjectMapper objectMapper;

  public NotificationHandler(
      ProcessedEventRepository processedEventRepository,
      NotificationLogRepository notificationLogRepository,
      MockEmailSender emailSender,
      MockSmsSender smsSender,
      ObjectMapper objectMapper) {
    this.processedEventRepository = processedEventRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.emailSender = emailSender;
    this.smsSender = smsSender;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void handle(String rawPayload) {
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      String eventIdStr = text(root, "eventId");
      if (eventIdStr == null || eventIdStr.isBlank()) {
        // fallback: use transactionId + eventType as synthetic id for older payloads
        JsonNode data = root.path("data");
        eventIdStr = text(root, "eventType") + ":" + text(data, "transactionId");
      }
      UUID eventId;
      try {
        eventId = UUID.fromString(eventIdStr);
      } catch (IllegalArgumentException ex) {
        eventId = UUID.nameUUIDFromBytes(eventIdStr.getBytes());
      }

      if (processedEventRepository.existsById(eventId)) {
        log.info("Skip duplicate eventId={}", eventId);
        return;
      }

      String eventType = text(root, "eventType");
      JsonNode data = root.path("data");
      String userId = text(data, "userId");
      String transactionId = text(data, "transactionId");
      String amount = data.path("amount").asText("0");
      String currency = text(data, "currency");
      String description = text(data, "description");
      String finalStatus = text(root, "finalStatus");
      String failureReason = text(root, "failureReason");

      String recipient = text(root, "recipientEmail");
      if (recipient == null || recipient.isBlank()) {
        recipient = "user-" + (userId == null ? "unknown" : userId) + "@bank.local";
      }

      boolean success = eventType != null && eventType.contains("COMPLETED");
      String template = success ? "TRANSFER_COMPLETED" : "TRANSFER_FAILED";
      String subject = success ? "Transfer completed" : "Transfer failed/compensated";
      String body = buildBody(success, transactionId, amount, currency, description, finalStatus, failureReason);

      emailSender.send(recipient, subject, body);
      smsSender.send("+84000000000", subject + " txn=" + transactionId);

      NotificationLogEntity logEntity = new NotificationLogEntity();
      logEntity.setId(UUID.randomUUID());
      logEntity.setEventId(eventId);
      logEntity.setChannel("EMAIL");
      logEntity.setRecipient(recipient);
      logEntity.setTemplate(template);
      logEntity.setStatus("SENT");
      logEntity.setBody(body);
      logEntity.setCreatedAt(Instant.now());
      notificationLogRepository.save(logEntity);

      ProcessedEventEntity pe = new ProcessedEventEntity();
      pe.setEventId(eventId);
      pe.setProcessedAt(Instant.now());
      try {
        processedEventRepository.save(pe);
      } catch (DataIntegrityViolationException dup) {
        log.info("Concurrent duplicate eventId={}", eventId);
      }

      log.info("Notification SENT eventId={} template={} recipient={}", eventId, template, recipient);
    } catch (Exception e) {
      log.error("Failed to process notification payload: {}", e.getMessage());
      throw new IllegalStateException("Notification processing failed", e);
    }
  }

  private String buildBody(
      boolean success,
      String transactionId,
      String amount,
      String currency,
      String description,
      String finalStatus,
      String failureReason) {
    if (success) {
      return "Your transfer " + transactionId + " of " + amount + " " + currency
          + " completed successfully. " + (description == null ? "" : description);
    }
    return "Your transfer " + transactionId + " ended as " + (finalStatus == null ? "FAILED" : finalStatus)
        + ". Reason: " + (failureReason == null ? "n/a" : failureReason)
        + ". Amount: " + amount + " " + currency;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return v.asText();
  }
}
