package com.banksystem.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

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
      String rejectedBy
  ) {}
}
