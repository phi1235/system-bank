package com.banksystem.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PasswordResetDtos {
  private PasswordResetDtos() {}

  /** Public: customer creates a password-reset ticket (no login). */
  public record CreateTicketRequest(
      @NotBlank String usernameOrEmail,
      String channel,
      @Size(max = 500) String note
  ) {}

  public record TicketResponse(
      String ticketId,
      String username,
      String emailMasked,
      String channel,
      String status,
      String requesterNote,
      String rejectReason,
      Instant createdAt,
      Instant fulfilledAt,
      Instant rejectedAt
  ) {}

  public record FulfillResponse(
      String ticketId,
      String status,
      String channel,
      String deliveryMasked,
      String message
  ) {}

  public record RejectRequest(@Size(max = 500) String reason) {}

  public record LockRequest(@Size(max = 255) String reason) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank String newPassword
  ) {}
}
