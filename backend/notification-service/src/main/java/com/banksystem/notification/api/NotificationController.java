package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.NotificationDtos.UnreadCountResponse;
import com.banksystem.notification.application.NotificationInboxService;
import com.banksystem.notification.config.UserContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationInboxService inboxService;

  public NotificationController(NotificationInboxService inboxService) {
    this.inboxService = inboxService;
  }

  @GetMapping
  public ApiResponse<PageResponse<NotificationItem>> myInbox(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    UserContext.requirePermission("ib:notifications:view");
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(inboxService.myInbox(userId, page, Math.min(size, 100)));
  }

  @GetMapping("/unread-count")
  public ApiResponse<UnreadCountResponse> unreadCount() {
    UserContext.requirePermission("ib:notifications:view");
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(new UnreadCountResponse(inboxService.unreadCount(userId)));
  }

  @PostMapping("/{id}/read")
  public ApiResponse<NotificationItem> markRead(@PathVariable UUID id) {
    UserContext.requirePermission("ib:notifications:view");
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(inboxService.markRead(userId, id));
  }

  @PostMapping("/read-all")
  public ApiResponse<Map<String, Integer>> markAllRead() {
    UserContext.requirePermission("ib:notifications:view");
    UUID userId = UserContext.requireUser().userId();
    int updated = inboxService.markAllRead(userId);
    return ApiResponse.ok(Map.of("updated", updated));
  }
}
