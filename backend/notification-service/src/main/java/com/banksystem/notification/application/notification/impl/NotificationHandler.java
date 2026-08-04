package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
  private final EmailSender emailSender;
  private final SmsSender smsSender;
  private final ObjectMapper objectMapper;
  private final NotificationRealtimeHub realtimeHub;

  public NotificationHandler(
      ProcessedEventRepository processedEventRepository,
      NotificationLogRepository notificationLogRepository,
      EmailSender emailSender,
      SmsSender smsSender,
      ObjectMapper objectMapper,
      NotificationRealtimeHub realtimeHub) {
    this.processedEventRepository = processedEventRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.emailSender = emailSender;
    this.smsSender = smsSender;
    this.objectMapper = objectMapper;
    this.realtimeHub = realtimeHub;
  }

  @Transactional
  public void handle(String rawPayload) {
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      String eventIdStr = text(root, "eventId");
      if (eventIdStr == null || eventIdStr.isBlank()) {
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
      String userIdStr = text(data, "userId");
      if (userIdStr == null || userIdStr.isBlank()) {
        userIdStr = text(root, "userId");
      }
      UUID ownerUserId = parseUuidOrNull(userIdStr);
      String transactionId = text(data, "transactionId");
      String amount = data.path("amount").asText("0");
      String currency = text(data, "currency");
      String description = text(data, "description");
      String finalStatus = text(root, "finalStatus");
      String failureReason = text(root, "failureReason");

      String recipient = text(root, "recipientEmail");
      if (recipient == null || recipient.isBlank()) {
        recipient = "user-" + (userIdStr == null ? "unknown" : userIdStr) + "@bank.local";
      }

      boolean success = eventType != null && eventType.contains("COMPLETED");
      String template = success ? "TRANSFER_COMPLETED" : "TRANSFER_FAILED";
      String subject = success ? "Chuyển khoản thành công" : "Chuyển khoản thất bại";
      String body =
          buildBody(success, transactionId, amount, currency, description, finalStatus, failureReason);

      emailSender.send(recipient, subject, body);
      smsSender.send("+840****0000", subject);

      NotificationLogEntity logEntity = new NotificationLogEntity();
      logEntity.setId(UUID.randomUUID());
      logEntity.setEventId(eventId);
      logEntity.setChannel("EMAIL");
      logEntity.setRecipient(recipient);
      logEntity.setTemplate(template);
      logEntity.setStatus("SENT");
      logEntity.setBody(body);
      logEntity.setUserId(ownerUserId);
      logEntity.setAudience(NotificationInboxService.AUDIENCE_CUSTOMER);
      if (transactionId != null && !transactionId.isBlank()) {
        logEntity.setActionType("TRANSFER");
        logEntity.setActionId(transactionId);
        logEntity.setActionPath("/customer/payments/transfer?txnId=" + transactionId);
      }
      logEntity.setCreatedAt(Instant.now());
      notificationLogRepository.save(logEntity);
      if (ownerUserId != null) {
        realtimeHub.publish(ownerUserId, toItem(logEntity));
      }

      if (!success) {
        NotificationLogEntity ops = new NotificationLogEntity();
        ops.setId(UUID.randomUUID());
        ops.setEventId(UUID.nameUUIDFromBytes(("ops:" + eventId).getBytes()));
        ops.setChannel("OPS");
        ops.setRecipient("ops@bank.local");
        ops.setTemplate("OPS_TRANSFER_FAILED");
        ops.setStatus("OPEN");
        ops.setBody(buildOpsBody(transactionId, amount, currency, ownerUserId, finalStatus, failureReason));
        ops.setUserId(null);
        ops.setAudience(NotificationInboxService.AUDIENCE_OPS);
        if (transactionId != null && !transactionId.isBlank()) {
          ops.setActionType("TRANSFER");
          ops.setActionId(transactionId);
          ops.setActionPath("/admin/transfers?q=" + transactionId);
        }
        ops.setCreatedAt(Instant.now());
        notificationLogRepository.save(ops);
        realtimeHub.publishOps(toItem(ops));
      }

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

  private static NotificationItem toItem(NotificationLogEntity e) {
    return new NotificationItem(
        e.getId().toString(),
        e.getChannel(),
        e.getTemplate(),
        e.getStatus(),
        e.getBody() == null ? "" : e.getBody(),
        false,
        null,
        e.getCreatedAt(),
        e.getActionType(),
        e.getActionId(),
        e.getActionPath());
  }

  private String buildBody(
      boolean success,
      String transactionId,
      String amount,
      String currency,
      String description,
      String finalStatus,
      String failureReason) {
    String cur = currency == null || currency.isBlank() ? "VND" : currency;
    String amt = amount == null || amount.isBlank() ? "" : amount;
    if (success) {
      String head =
          amt.isEmpty()
              ? "Chuyển khoản thành công."
              : "Chuyển khoản " + amt + " " + cur + " thành công.";
      if (description != null && !description.isBlank()) {
        return head + " " + description.trim();
      }
      return head;
    }
    String status = finalStatus == null || finalStatus.isBlank() ? "FAILED" : finalStatus;
    String reason = failureReason == null || failureReason.isBlank() ? "n/a" : failureReason.trim();
    String amtPart = amt.isEmpty() ? "" : " (" + amt + " " + cur + ")";
    return "Chuyển khoản không thành công" + amtPart + ". Trạng thái: " + status + ". Lý do: " + reason;
  }

  private String buildOpsBody(
      String transactionId,
      String amount,
      String currency,
      UUID ownerUserId,
      String finalStatus,
      String failureReason) {
    String cur = currency == null || currency.isBlank() ? "" : currency;
    String amt = amount == null ? "0" : amount;
    String status = finalStatus == null || finalStatus.isBlank() ? "FAILED" : finalStatus;
    String reason = failureReason == null || failureReason.isBlank() ? "n/a" : failureReason.trim();
    return "Chuyển khoản thất bại: "
        + amt
        + (cur.isEmpty() ? "" : " " + cur)
        + " · trạng thái "
        + status
        + " · lý do: "
        + reason;
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

  private static UUID parseUuidOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
