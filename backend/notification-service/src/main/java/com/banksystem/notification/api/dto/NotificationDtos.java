package com.banksystem.notification.api.dto;

import java.time.Instant;

public final class NotificationDtos {
  private NotificationDtos() {}

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read,
      Instant readAt,
      Instant createdAt
  ) {}

  public record UnreadCountResponse(long unread) {}
}
