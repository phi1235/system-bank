package com.banksystem.notification.api.notification;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.notification.api.dto.NotificationDtos.CreateNotificationLogRequest;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.application.notification.NotificationLogCommandService;
import com.banksystem.notification.application.notification.NotificationLogCommandService.CreateNotificationLogCommand;
import com.banksystem.notification.application.notification.OpsAlertService;
import jakarta.validation.Valid;
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

  public InternalNotificationController(
      NotificationLogCommandService notificationLogCommandService,
      OpsAlertService opsAlertService) {
    this.notificationLogCommandService = notificationLogCommandService;
    this.opsAlertService = opsAlertService;
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
