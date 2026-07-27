package com.banksystem.account.infrastructure.feign;

public final class NotificationClientDtos {
  private NotificationClientDtos() {}

  public record CreateOpsAlertRequest(String eventId, String template, String body) {}

  /** Customer inbox notification (e.g. card approval decision). */
  public record CreateNotificationRequest(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      java.util.UUID userId,
      String audience,
      String actionType,
      String actionId,
      String actionPath) {}

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read
  ) {}
}
