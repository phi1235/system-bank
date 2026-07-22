package com.banksystem.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OpsAlertDtos {
  private OpsAlertDtos() {}

  /**
   * Internal create request for shared OPS inbox alerts.
   * {@code eventId} is optional; when present it is used for idempotency ({@code notification_logs.event_id} unique).
   */
  public record CreateOpsAlertRequest(
      @Size(max = 36) String eventId,
      @NotBlank @Size(max = 50) String template,
      @NotBlank @Size(max = 4000) String body
  ) {}
}
