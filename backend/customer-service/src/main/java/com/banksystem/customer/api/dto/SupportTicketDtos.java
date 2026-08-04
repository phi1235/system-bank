package com.banksystem.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SupportTicketDtos {
  private SupportTicketDtos() {}

  public record CreateSupportTicketRequest(
      @NotBlank @Size(max = 40) String category,
      @NotBlank @Size(max = 200) String subject,
      @NotBlank @Size(max = 4000) String body,
      @Size(max = 20) String priority
  ) {}

  public record ResolveTicketRequest(@Size(max = 2000) String resolutionNote) {}

  public record RejectTicketRequest(@NotBlank @Size(max = 2000) String reason) {}

  /** Staff asks customer for more info → WAITING_CUSTOMER + message. */
  public record RequestInfoRequest(@NotBlank @Size(max = 4000) String message) {}

  /** Customer or staff posts a message on the ticket thread. */
  public record PostMessageRequest(@NotBlank @Size(max = 4000) String body) {}

  public record SupportTicketMessageResponse(
      String id,
      String ticketId,
      String authorUserId,
      String authorRole,
      String body,
      Instant createdAt
  ) {}

  public record SupportTicketResponse(
      String id,
      String userId,
      String category,
      String subject,
      String body,
      String priority,
      String status,
      String requesterEmail,
      String resolutionNote,
      String rejectReason,
      String assignedTo,
      Instant createdAt,
      Instant updatedAt,
      Instant resolvedAt,
      String resolvedBy,
      Instant rejectedAt,
      String rejectedBy,
      List<SupportTicketMessageResponse> messages
  ) {}

  public record AdminTicketFilterRequest(
      Integer page,
      Integer size,
      String status,
      String category,
      String q
  ) {}

  public record MyTicketFilterRequest(
      Integer page,
      Integer size
  ) {}
}
