package com.banksystem.transaction.infrastructure.feign;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountClientDtos {
  private AccountClientDtos() {}

  public record AccountView(
      String id,
      String userId,
      String accountNumber,
      String accountType,
      String currency,
      BigDecimal balance,
      String status
  ) {
    public UUID idUuid() {
      return UUID.fromString(id);
    }

    public UUID userIdUuid() {
      return UUID.fromString(userId);
    }
  }

  public record MoneyCommand(
      BigDecimal amount,
      String referenceId,
      String description,
      String commandId
  ) {}

  public record MoneyResult(String ledgerEntryId, BigDecimal balanceAfter) {}
}
