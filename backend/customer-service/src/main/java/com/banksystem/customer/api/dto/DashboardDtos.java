package com.banksystem.customer.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class DashboardDtos {
  private DashboardDtos() {}

  public record DashboardSummaryResponse(
      long customers,
      long kycPending,
      long accounts,
      long accountsFrozen,
      long transfers,
      long transfersFailed,
      long transfersCompensated,
      long outboxDead,
      long outboxPending,
      long outboxPublished,
      long users,
      long usersLocked,
      long audits
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InternalAccountCountsResponse(long total, long frozen) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InternalTransactionCountsResponse(
      long transfers,
      long transfersFailed,
      long transfersCompensated,
      long audits,
      long outboxDead,
      long outboxPending,
      long outboxPublished
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InternalUserCountsResponse(
      long users,
      long usersLocked
  ) {}
}
