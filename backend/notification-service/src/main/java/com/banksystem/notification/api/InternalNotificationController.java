package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

  private final NotificationLogRepository repository;
  private final String apiKey;

  public InternalNotificationController(
      NotificationLogRepository repository,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.repository = repository;
    this.apiKey = apiKey;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(required = false) UUID eventId,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    List<NotificationLogEntity> rows = eventId == null
        ? repository.findTop50ByOrderByCreatedAtDesc()
        : repository.findByEventId(eventId).stream().toList();
    return ApiResponse.ok(rows.stream().map(this::map).toList());
  }

  private Map<String, Object> map(NotificationLogEntity e) {
    return Map.of(
        "id", e.getId().toString(),
        "eventId", e.getEventId().toString(),
        "channel", e.getChannel(),
        "recipient", e.getRecipient(),
        "template", e.getTemplate(),
        "status", e.getStatus(),
        "body", e.getBody() == null ? "" : e.getBody(),
        "userId", e.getUserId() == null ? "" : e.getUserId().toString(),
        "readAt", e.getReadAt() == null ? "" : e.getReadAt().toString(),
        "createdAt", e.getCreatedAt().toString()
    );
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
