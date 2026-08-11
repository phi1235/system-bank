package com.banksystem.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
  private NotificationDtos() {}

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

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read,
      Instant readAt,
      Instant createdAt,
      String actionType,
      String actionId,
      String actionPath
  ) {}

  public record UnreadCountResponse(long unread) {}
}
