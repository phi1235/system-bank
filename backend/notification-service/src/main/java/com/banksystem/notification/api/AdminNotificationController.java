package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.NotificationDtos.UnreadCountResponse;
import com.banksystem.notification.application.NotificationInboxService;
import com.banksystem.notification.application.NotificationRealtimeHub;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Shared staff ops alerts (transfer failures, etc.). Not user-scoped.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequirePermission("notifications:ops:view")
public class AdminNotificationController {

  private final NotificationInboxService inboxService;
  private final NotificationRealtimeHub realtimeHub;

  public AdminNotificationController(
      NotificationInboxService inboxService, NotificationRealtimeHub realtimeHub) {
    this.inboxService = inboxService;
    this.realtimeHub = realtimeHub;
  }

  @GetMapping
  public ApiResponse<PageResponse<NotificationItem>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(inboxService.opsInbox(page, Math.min(size, 100)));
  }

  @PostMapping("/search")
  public ApiResponse<PageResponse<NotificationItem>> search(
      @Valid @RequestBody PageFilterRequest req) {
    return ApiResponse.ok(inboxService.opsInbox(req.page(), req.size()));
  }

  public record PageFilterRequest(Integer page, Integer size) {}

  @GetMapping("/unread-count")
  public ApiResponse<UnreadCountResponse> unreadCount() {
    return ApiResponse.ok(new UnreadCountResponse(inboxService.opsUnreadCount()));
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return realtimeHub.subscribeOps();
  }

  @PostMapping("/{id}/read")
  public ApiResponse<NotificationItem> markRead(@PathVariable UUID id) {
    return ApiResponse.ok(inboxService.markOpsRead(id));
  }

  @PostMapping("/read-all")
  public ApiResponse<Map<String, Integer>> markAllRead() {
    int updated = inboxService.markAllOpsRead();
    return ApiResponse.ok(Map.of("updated", updated));
  }
}
