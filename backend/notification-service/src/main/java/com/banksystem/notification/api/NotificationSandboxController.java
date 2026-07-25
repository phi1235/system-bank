package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.application.NotificationLogCommandService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev Sandbox Controller for inspecting notification logs.
 * Pure presentation controller following Clean Architecture principles.
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationSandboxController {

  public record NotificationSandboxItem(
      String id,
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      String userId,
      String audience,
      Instant createdAt
  ) {}

  private final NotificationLogCommandService service;

  public NotificationSandboxController(NotificationLogCommandService service) {
    this.service = service;
  }

  @GetMapping({"/admin/notifications/sandbox", "/notifications/sandbox", "/dev/notifications/sandbox"})
  public ApiResponse<PageResponse<NotificationSandboxItem>> getSandboxLogs(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String channel,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.searchSandbox(q, channel, page, size));
  }
}
