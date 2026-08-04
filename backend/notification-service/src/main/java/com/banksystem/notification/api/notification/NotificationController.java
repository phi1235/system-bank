package com.banksystem.notification.api.notification;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.application.notification.impl.NotificationRealtimeHub;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.NotificationDtos.UnreadCountResponse;
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

@RestController
@RequestMapping("/api/v1/notifications")
@RequirePermission("ib:notifications:view")
public class NotificationController {

  private final NotificationInboxService inboxService;
  private final NotificationRealtimeHub realtimeHub;

  public NotificationController(
      NotificationInboxService inboxService, NotificationRealtimeHub realtimeHub) {
    this.inboxService = inboxService;
    this.realtimeHub = realtimeHub;
  }

  /**
   * Customer inbox. {@code readFilter}: ALL (default) | UNREAD | READ.
   * {@code unreadOnly=true} is accepted for older clients and maps to UNREAD.
   */
  @GetMapping
  public ApiResponse<PageResponse<NotificationItem>> myInbox(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String readFilter,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    UUID userId = UserContext.requireUser().userId();
    String filter = readFilter;
    if ((filter == null || filter.isBlank()) && unreadOnly) {
      filter = "UNREAD";
    }
    return ApiResponse.ok(
        inboxService.myInbox(userId, page, Math.min(size, 100), filter));
  }

  @PostMapping("/findNotificationByCondition")
  public ApiResponse<PageResponse<NotificationItem>> findNotificationByCondition(
      @Valid @RequestBody NotificationSearchRequest req) {
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(
        inboxService.myInbox(userId, req.page(), req.size(), req.readFilter()));
  }

  public record NotificationSearchRequest(
      Integer page, Integer size, String readFilter) {}

  @GetMapping("/unread-count")
  public ApiResponse<UnreadCountResponse> unreadCount() {
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(new UnreadCountResponse(inboxService.unreadCount(userId)));
  }

  /** Server-sent events stream for live inbox updates (one-way push after Kafka consume). */
  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    UUID userId = UserContext.requireUser().userId();
    return realtimeHub.subscribe(userId);
  }

  @PostMapping("/{id}/read")
  public ApiResponse<NotificationItem> markRead(@PathVariable UUID id) {
    UUID userId = UserContext.requireUser().userId();
    return ApiResponse.ok(inboxService.markRead(userId, id));
  }

  @PostMapping("/read-all")
  public ApiResponse<Map<String, Integer>> markAllRead() {
    UUID userId = UserContext.requireUser().userId();
    int updated = inboxService.markAllRead(userId);
    return ApiResponse.ok(Map.of("updated", updated));
  }
}
