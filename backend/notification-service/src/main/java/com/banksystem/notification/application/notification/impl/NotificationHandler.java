package com.banksystem.notification.application.notification.impl;

import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.application.notification.CustomerContactResolver;
import com.banksystem.notification.application.notification.NotificationDeliveryQueue;
import com.banksystem.notification.application.notification.NotificationInboxService;
import com.banksystem.notification.domain.event.ProcessedEventRepository;
import com.banksystem.notification.domain.notification.NotificationLogEntity;
import com.banksystem.notification.domain.notification.NotificationLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final NotificationDeliveryQueue deliveryQueue;
  private final ObjectMapper objectMapper;
  private final NotificationRealtimeHub realtimeHub;
  private final CustomerContactResolver contactResolver;
  private final String opsRecipient;
  private final Clock clock;

  public NotificationHandler(
      ProcessedEventRepository processedEventRepository,
      NotificationLogRepository notificationLogRepository,
      NotificationDeliveryQueue deliveryQueue,
      ObjectMapper objectMapper,
      NotificationRealtimeHub realtimeHub,
      CustomerContactResolver contactResolver,
      @Value("${bank.notification.ops-recipient}") String opsRecipient,
      Clock clock) {
    this.processedEventRepository = processedEventRepository;
    this.notificationLogRepository = notificationLogRepository;
    this.deliveryQueue = deliveryQueue;
    this.objectMapper = objectMapper;
    this.realtimeHub = realtimeHub;
    this.contactResolver = contactResolver;
    this.opsRecipient = opsRecipient;
    this.clock = clock;
  }

  @Transactional
  public void handle(String rawPayload) {
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      UUID eventId = resolveEventId(root);
      Instant now = clock.instant();
      if (processedEventRepository.claim(eventId, now) == 0) {
        log.info("Skip duplicate notification eventId={}", eventId);
        return;
      }

      String eventType = text(root, "eventType");
      JsonNode data = root.path("data");
      if (eventType != null && eventType.startsWith("FORENSIC_CASE_")) {
        handleForensicCase(eventId, eventType, data, now);
        return;
      }
      handleTransfer(eventId, eventType, root, data, now);
    } catch (Exception exception) {
      log.error("Failed to persist notification intent: {}", exception.getMessage());
      throw new IllegalStateException("Notification processing failed", exception);
    }
  }

  private void handleTransfer(
      UUID eventId, String eventType, JsonNode root, JsonNode data, Instant now) {
    UUID ownerUserId = parseUuidOrNull(firstNonBlank(text(data, "userId"), text(root, "userId")));
    String transactionId = text(data, "transactionId");
    String amount = data.path("amount").asText("0");
    String currency = text(data, "currency");
    String description = text(data, "description");
    String finalStatus = text(root, "finalStatus");
    String failureReason = text(root, "failureReason");

    String recipient = text(root, "recipientEmail");
    String recipientPhone = text(root, "recipientPhone");
    if (ownerUserId != null && (isBlank(recipient) || isBlank(recipientPhone))) {
      CustomerContactResolver.CustomerContact contact = contactResolver.find(ownerUserId);
      recipient = isBlank(recipient) ? contact.email() : recipient;
      recipientPhone = isBlank(recipientPhone) ? contact.phone() : recipientPhone;
    }

    boolean success = eventType != null && eventType.contains("COMPLETED");
    String template = success ? "TRANSFER_COMPLETED" : "TRANSFER_FAILED";
    String subject = success ? "Chuyển khoản thành công" : "Chuyển khoản thất bại";
    String body = buildBody(
        success, amount, currency, description, finalStatus, failureReason);
    boolean hasExternalDelivery = !isBlank(recipient) || !isBlank(recipientPhone);

    NotificationLogEntity notification = new NotificationLogEntity();
    notification.setId(UUID.randomUUID());
    notification.setEventId(eventId);
    notification.setChannel(!isBlank(recipient) ? "EMAIL" : "IN_APP");
    notification.setRecipient(!isBlank(recipient) ? recipient : "user:" + ownerUserId);
    notification.setTemplate(template);
    notification.setStatus(hasExternalDelivery ? "QUEUED" : "SENT");
    notification.setBody(body);
    notification.setUserId(ownerUserId);
    notification.setAudience(NotificationInboxService.AUDIENCE_CUSTOMER);
    attachTransferAction(notification, transactionId, "/customer/payments/transfer?txnId=");
    notification.setCreatedAt(now);
    notificationLogRepository.save(notification);

    if (!isBlank(recipient)) {
      deliveryQueue.enqueueEmail(eventId, recipient, subject, body, now);
    }
    if (!isBlank(recipientPhone)) {
      deliveryQueue.enqueueSms(eventId, recipientPhone, subject, body, now);
    }
    if (ownerUserId != null) {
      UUID userId = ownerUserId;
      afterCommit(() -> realtimeHub.publish(userId, toItem(notification)));
    }

    if (!success) {
      createOpsFailure(eventId, transactionId, amount, currency, ownerUserId,
          finalStatus, failureReason, now);
    }
    log.info("Notification intent persisted eventId={} template={}", eventId, template);
  }

  private void createOpsFailure(
      UUID eventId,
      String transactionId,
      String amount,
      String currency,
      UUID ownerUserId,
      String finalStatus,
      String failureReason,
      Instant now) {
    NotificationLogEntity ops = new NotificationLogEntity();
    ops.setId(UUID.randomUUID());
    ops.setEventId(namedUuid("ops:" + eventId));
    ops.setChannel("OPS");
    ops.setRecipient(opsRecipient);
    ops.setTemplate("OPS_TRANSFER_FAILED");
    ops.setStatus("OPEN");
    ops.setBody(buildOpsBody(amount, currency, finalStatus, failureReason));
    ops.setAudience(NotificationInboxService.AUDIENCE_OPS);
    attachTransferAction(ops, transactionId, "/admin/transfers?q=");
    ops.setCreatedAt(now);
    notificationLogRepository.save(ops);
    afterCommit(() -> realtimeHub.publishOps(toItem(ops)));
  }

  private void handleForensicCase(UUID eventId, String eventType, JsonNode data, Instant now) {
    String caseId = text(data, "caseId");
    String actionPath = text(data, "actionPath");
    NotificationLogEntity ops = new NotificationLogEntity();
    ops.setId(UUID.randomUUID());
    ops.setEventId(eventId);
    ops.setChannel("OPS");
    ops.setRecipient(opsRecipient);
    ops.setTemplate(eventType);
    ops.setStatus("OPEN");
    ops.setBody("Hồ sơ điều tra " + safe(text(data, "caseNumber"))
        + " · " + safe(text(data, "status"))
        + " · mức ưu tiên " + safe(text(data, "priority"))
        + " · người xử lý " + safe(text(data, "assignedTo")));
    ops.setAudience(NotificationInboxService.AUDIENCE_OPS);
    ops.setActionType("FORENSIC_CASE");
    ops.setActionId(caseId);
    ops.setActionPath(isBlank(actionPath) ? "/admin/forensics" : actionPath);
    ops.setCreatedAt(now);
    notificationLogRepository.save(ops);
    afterCommit(() -> realtimeHub.publishOps(toItem(ops)));
  }

  private static void attachTransferAction(
      NotificationLogEntity notification, String transactionId, String pathPrefix) {
    if (!isBlank(transactionId)) {
      notification.setActionType("TRANSFER");
      notification.setActionId(transactionId);
      notification.setActionPath(pathPrefix + transactionId);
    }
  }

  private UUID resolveEventId(JsonNode root) {
    String value = text(root, "eventId");
    if (isBlank(value)) {
      JsonNode data = root.path("data");
      value = safe(text(root, "eventType")) + ":" + safe(text(data, "transactionId"));
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return namedUuid(value);
    }
  }

  private static UUID namedUuid(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
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
        entity.getBody() == null ? "" : entity.getBody(), false, null, entity.getCreatedAt(),
        entity.getActionType(), entity.getActionId(), entity.getActionPath());
  }

  private String buildBody(
      boolean success,
      String amount,
      String currency,
      String description,
      String finalStatus,
      String failureReason) {
    String resolvedCurrency = isBlank(currency) ? "VND" : currency;
    String formattedAmount = NotificationMoneyFormatter.format(amount);
    if (success) {
      String heading = formattedAmount.isEmpty()
          ? "Chuyển khoản thành công."
          : "Chuyển khoản " + formattedAmount + " " + resolvedCurrency + " thành công.";
      return isBlank(description) ? heading : heading + " " + description.trim();
    }
    String status = isBlank(finalStatus) ? "FAILED" : finalStatus;
    String reason = isBlank(failureReason) ? "n/a" : failureReason.trim();
    String amountPart = formattedAmount.isEmpty()
        ? "" : " (" + formattedAmount + " " + resolvedCurrency + ")";
    return "Chuyển khoản không thành công" + amountPart
        + ". Trạng thái: " + status + ". Lý do: " + reason;
  }

  private String buildOpsBody(
      String amount, String currency, String finalStatus, String failureReason) {
    String resolvedCurrency = isBlank(currency) ? "" : currency;
    String resolvedAmount = isBlank(amount) ? "0" : NotificationMoneyFormatter.format(amount);
    String status = isBlank(finalStatus) ? "FAILED" : finalStatus;
    String reason = isBlank(failureReason) ? "n/a" : failureReason.trim();
    return "Chuyển khoản thất bại: " + resolvedAmount
        + (resolvedCurrency.isEmpty() ? "" : " " + resolvedCurrency)
        + " · trạng thái " + status + " · lý do: " + reason;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static String firstNonBlank(String first, String second) {
    return isBlank(first) ? second : first;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String safe(String value) {
    return isBlank(value) ? "n/a" : value.trim();
  }

  private static UUID parseUuidOrNull(String raw) {
    if (isBlank(raw)) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
