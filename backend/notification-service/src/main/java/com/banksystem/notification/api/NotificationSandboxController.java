package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

  private final NotificationLogRepository repository;

  public NotificationSandboxController(NotificationLogRepository repository) {
    this.repository = repository;
  }

  @GetMapping({"/admin/notifications/sandbox", "/notifications/sandbox"})
  public ApiResponse<PageResponse<NotificationSandboxItem>> getSandboxLogs(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String channel,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    boolean hasQ = q != null && !q.isBlank();
    boolean hasChannel = channel != null && !channel.isBlank();
    PageRequest pageable = PageRequest.of(page, Math.min(size, 100));

    Page<NotificationLogEntity> result = repository.searchSandbox(
        hasQ, hasQ ? q.trim() : null,
        hasChannel, hasChannel ? channel.trim().toUpperCase() : null,
        pageable);

    Page<NotificationSandboxItem> mapped = result.map(e -> new NotificationSandboxItem(
        e.getId().toString(),
        e.getChannel(),
        e.getRecipient(),
        e.getTemplate(),
        e.getStatus(),
        e.getBody(),
        e.getUserId() != null ? e.getUserId().toString() : null,
        e.getAudience(),
        e.getCreatedAt()
    ));

    return ApiResponse.ok(new PageResponse<>(
        mapped.getContent(),
        mapped.getNumber(),
        mapped.getSize(),
        mapped.getTotalElements(),
        mapped.getTotalPages()
    ));
  }
}
