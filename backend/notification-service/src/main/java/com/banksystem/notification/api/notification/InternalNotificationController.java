package com.banksystem.notification.api.notification;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.notification.api.dto.NotificationDtos.CreateNotificationLogRequest;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.NotificationDtos.QueueEmailRequest;
import com.banksystem.notification.api.dto.NotificationDtos.SendEmailRequest;
import com.banksystem.notification.application.notification.EmailSender;
import com.banksystem.notification.application.notification.NotificationDeliveryQueue;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.application.notification.NotificationLogCommandService;
import com.banksystem.notification.application.notification.NotificationLogCommandService.CreateNotificationLogCommand;
import com.banksystem.notification.application.notification.OpsAlertService;
import jakarta.validation.Valid;
import java.util.Base64;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
@RequireInternalApiKey
public class InternalNotificationController {

  private final NotificationLogCommandService notificationLogCommandService;
  private final OpsAlertService opsAlertService;
  private final EmailSender emailSender;
  private final NotificationDeliveryQueue deliveryQueue;

  public InternalNotificationController(
      NotificationLogCommandService notificationLogCommandService,
      OpsAlertService opsAlertService,
      EmailSender emailSender,
      NotificationDeliveryQueue deliveryQueue) {
    this.notificationLogCommandService = notificationLogCommandService;
    this.opsAlertService = opsAlertService;
    this.emailSender = emailSender;
    this.deliveryQueue = deliveryQueue;
  }

  @PostMapping("/email/queue")
  public ApiResponse<Boolean> queueEmail(@Valid @RequestBody QueueEmailRequest request) {
    deliveryQueue.enqueueEmailWithAttachment(
        request.eventId(),
        request.recipient(),
        request.subject(),
        request.body(),
        request.attachmentFilename(),
        Base64.getDecoder().decode(request.attachmentContentBase64()),
        Instant.now());
    return ApiResponse.ok(Boolean.TRUE);
  }

  @PostMapping("/email")
  public ApiResponse<Boolean> sendEmail(@RequestBody SendEmailRequest request) {
    if (request.attachmentContentBase64() != null && !request.attachmentContentBase64().isBlank()) {
      emailSender.sendWithAttachment(request.recipient(), request.subject(), request.body(),
          request.attachmentFilename(), Base64.getDecoder().decode(request.attachmentContentBase64()));
    } else {
      emailSender.send(request.recipient(), request.subject(), request.body());
    }
    return ApiResponse.ok(Boolean.TRUE);
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(required = false) UUID eventId) {
    return ApiResponse.ok(notificationLogCommandService.listLogs(eventId));
  }

  @PostMapping
  public ApiResponse<NotificationItem> createNotificationLog(
      @RequestBody CreateNotificationLogRequest request) {
    CreateNotificationLogCommand command = new CreateNotificationLogCommand(
        request.channel(),
        request.recipient(),
        request.template(),
        request.status(),
        request.body(),
        request.userId(),
        request.audience(),
        request.actionType(),
        request.actionId(),
        request.actionPath());
    return ApiResponse.ok(notificationLogCommandService.createNotificationLog(command));
  }

  @PostMapping("/ops-alerts")
  public ApiResponse<NotificationItem> createOpsAlert(
      @Valid @RequestBody CreateOpsAlertRequest request) {
    return ApiResponse.ok(opsAlertService.create(request));
  }
}
