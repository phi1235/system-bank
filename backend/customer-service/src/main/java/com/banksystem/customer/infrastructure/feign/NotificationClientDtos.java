package com.banksystem.customer.infrastructure.feign;

import java.util.UUID;

public final class NotificationClientDtos {
  private NotificationClientDtos() {}

  public record CreateOpsAlertRequest(
      String eventId,
      String template,
      String body,
      String actionType,
      String actionId,
      String actionPath
  ) {
    public CreateOpsAlertRequest(String eventId, String template, String body) {
      this(eventId, template, body, null, null, null);
    }
  }

  /** Internal customer/staff notification log (in-app inbox + optional channel). */
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
  ) {
    public CreateNotificationLogRequest(
        String channel,
        String recipient,
        String template,
        String status,
        String body,
        UUID userId,
        String audience) {
      this(channel, recipient, template, status, body, userId, audience, null, null, null);
    }
  }

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read,
      String actionType,
      String actionId,
      String actionPath
  ) {}
}
