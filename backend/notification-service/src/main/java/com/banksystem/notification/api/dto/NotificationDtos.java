package com.banksystem.notification.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  public record SendEmailRequest(
      String recipient,
      String subject,
      String body,
      String attachmentFilename,
      String attachmentContentBase64
  ) {}

  public record QueueEmailRequest(
      @NotNull UUID eventId,
      @Email @NotBlank String recipient,
      @NotBlank @Size(max = 255) String subject,
      @NotBlank String body,
      @NotBlank @Size(max = 255) String attachmentFilename,
      @NotBlank @Size(max = 5_000_000) String attachmentContentBase64
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
