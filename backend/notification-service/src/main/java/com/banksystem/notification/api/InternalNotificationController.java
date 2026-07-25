package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.application.NotificationLogCommandService;
import com.banksystem.notification.application.NotificationLogCommandService.CreateNotificationLogCommand;
import com.banksystem.notification.application.OpsAlertService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for internal notification operations.
 * Pure presentation layer: Handles HTTP routing & header extraction; delegates all domain/application logic to Services.
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

  public record CreateNotificationLogRequest(
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

  private final NotificationLogCommandService notificationLogCommandService;
  private final OpsAlertService opsAlertService;

  public InternalNotificationController(
      NotificationLogCommandService notificationLogCommandService,
      OpsAlertService opsAlertService) {
    this.notificationLogCommandService = notificationLogCommandService;
    this.opsAlertService = opsAlertService;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(required = false) UUID eventId,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    notificationLogCommandService.verifyInternalKey(key);
    return ApiResponse.ok(notificationLogCommandService.listLogs(eventId));
  }

  @PostMapping
  public ApiResponse<NotificationItem> createNotificationLog(
      @RequestBody CreateNotificationLogRequest req,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    notificationLogCommandService.verifyInternalKey(key);
    CreateNotificationLogCommand cmd = new CreateNotificationLogCommand(
        req.channel(),
        req.recipient(),
        req.template(),
        req.status(),
        req.body(),
        req.userId(),
        req.audience(),
        req.actionType(),
        req.actionId(),
        req.actionPath());
    return ApiResponse.ok(notificationLogCommandService.createNotificationLog(cmd));
  }

  @PostMapping("/ops-alerts")
  public ApiResponse<NotificationItem> createOpsAlert(
      @Valid @RequestBody CreateOpsAlertRequest request,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    notificationLogCommandService.verifyInternalKey(key);
    return ApiResponse.ok(opsAlertService.create(request));
  }
}
