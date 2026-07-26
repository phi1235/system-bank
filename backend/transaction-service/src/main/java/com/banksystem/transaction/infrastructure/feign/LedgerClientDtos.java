package com.banksystem.transaction.infrastructure.feign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LedgerClientDtos {

  private LedgerClientDtos() {}

  public record LedgerSearchRequest(List<String> referenceIds) {}

  public record LedgerEntryView(
      String id,
      String accountId,
      String entryType,
      BigDecimal amount,
      String referenceId,
      Instant createdAt) {}
}
