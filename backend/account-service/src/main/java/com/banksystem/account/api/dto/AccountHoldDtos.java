package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountHoldDtos {
  private AccountHoldDtos() {}

  public record CreateHoldRequest(
      @NotNull UUID transactionId,
      @NotBlank @Size(max = 160) String commandId,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotNull @Future Instant expiresAt) {}

  public record HoldActionRequest(
      @NotBlank @Size(max = 160) String commandId,
      UUID journalId) {}

  public record HoldResponse(
      UUID id,
      UUID accountId,
      UUID transactionId,
      BigDecimal amount,
      String currency,
      String status,
      Instant expiresAt,
      UUID capturedJournalId,
      Instant createdAt,
      Instant updatedAt,
      long version) {}
}
