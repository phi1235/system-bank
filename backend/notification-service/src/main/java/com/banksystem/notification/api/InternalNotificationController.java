package com.banksystem.notification.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.application.NotificationInboxService;
import com.banksystem.notification.application.NotificationRealtimeHub;
import com.banksystem.notification.application.OpsAlertService;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

  private final NotificationLogRepository repository;
  private final OpsAlertService opsAlertService;
  private final NotificationRealtimeHub realtimeHub;
  private final String apiKey;

  public InternalNotificationController(
      NotificationLogRepository repository,
      OpsAlertService opsAlertService,
      NotificationRealtimeHub realtimeHub,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.repository = repository;
    this.opsAlertService = opsAlertService;
    this.realtimeHub = realtimeHub;
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

  public record CreateNotificationLogRequest(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      UUID userId,
      String audience
  ) {}

  /** Create arbitrary notification log (password reset, OTP, custom alert). */
  @PostMapping
  public ApiResponse<NotificationItem> createNotificationLog(
      @RequestBody CreateNotificationLogRequest req,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    NotificationLogEntity e = new NotificationLogEntity();
    e.setId(UUID.randomUUID());
    e.setEventId(UUID.randomUUID());
    e.setChannel(req.channel() == null || req.channel().isBlank() ? "EMAIL" : req.channel());
    e.setRecipient(req.recipient());
    e.setTemplate(req.template() == null || req.template().isBlank() ? "PASSWORD_RESET" : req.template());
    e.setStatus(req.status() == null || req.status().isBlank() ? "SENT" : req.status());
    e.setBody(req.body() == null ? "" : req.body());
    e.setUserId(req.userId());
    String audience =
        req.audience() == null || req.audience().isBlank()
            ? NotificationInboxService.AUDIENCE_CUSTOMER
            : req.audience().trim().toUpperCase();
    e.setAudience(audience);
    e.setCreatedAt(java.time.Instant.now());
    repository.save(e);
    NotificationItem item =
        new NotificationItem(
            e.getId().toString(),
            e.getChannel(),
            e.getTemplate(),
            e.getStatus(),
            e.getBody(),
            false,
            null,
            e.getCreatedAt());
    // Fan-out to customer SSE when user-scoped CUSTOMER inbox entry.
    if (e.getUserId() != null && NotificationInboxService.AUDIENCE_CUSTOMER.equals(audience)) {
      realtimeHub.publish(e.getUserId(), item);
    } else if (NotificationInboxService.AUDIENCE_OPS.equals(audience)) {
      realtimeHub.publishOps(item);
    }
    return ApiResponse.ok(item);
  }

  /** Staff OPS alert from other services (outbox DEAD, freeze, KYC, ...). */
  @PostMapping("/ops-alerts")
  public ApiResponse<NotificationItem> createOpsAlert(
      @Valid @RequestBody CreateOpsAlertRequest request,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(opsAlertService.create(request));
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
