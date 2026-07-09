package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class AccountDtos {
  private AccountDtos() {}

  public record OpenAccountRequest(String accountType) {}

  public record AccountResponse(
      String id,
      String userId,
      String accountNumber,
      String accountType,
      String currency,
      BigDecimal balance,
      String status
  ) {}

  public record MoneyCommand(
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank String referenceId,
      String description,
      String commandId
  ) {}

  public record MoneyResult(String ledgerEntryId, BigDecimal balanceAfter) {}
}
