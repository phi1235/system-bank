package com.banksystem.customer.infrastructure.feign;

import java.util.UUID;

public final class NotificationClientDtos {
  private NotificationClientDtos() {}

  public record CreateOpsAlertRequest(String eventId, String template, String body) {}

  /** Internal customer/staff notification log (in-app inbox + optional channel). */
  public record CreateNotificationLogRequest(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      UUID userId,
      String audience
  ) {}

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read
  ) {}
}
