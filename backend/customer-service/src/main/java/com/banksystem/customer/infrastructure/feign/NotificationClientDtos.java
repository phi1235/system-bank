package com.banksystem.customer.infrastructure.feign;

public final class NotificationClientDtos {
  private NotificationClientDtos() {}

  public record CreateOpsAlertRequest(String eventId, String template, String body) {}

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read
  ) {}
}
