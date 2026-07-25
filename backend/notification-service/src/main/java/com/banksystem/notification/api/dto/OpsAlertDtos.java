package com.banksystem.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OpsAlertDtos {
  private OpsAlertDtos() {}

  /**
   * Internal create request for shared OPS inbox alerts.
   * {@code eventId} is optional; when present it is used for idempotency ({@code notification_logs.event_id} unique).
   * Optional action* fields enable FE deep-link click-through.
   */
  public record CreateOpsAlertRequest(
      @Size(max = 36) String eventId,
      @NotBlank @Size(max = 50) String template,
      @NotBlank @Size(max = 4000) String body,
      @Size(max = 40) String actionType,
      @Size(max = 64) String actionId,
      @Size(max = 300) String actionPath
  ) {
    /** Back-compat ctor used by older callers / tests without deep-link. */
    public CreateOpsAlertRequest(String eventId, String template, String body) {
      this(eventId, template, body, null, null, null);
    }
  }
}
