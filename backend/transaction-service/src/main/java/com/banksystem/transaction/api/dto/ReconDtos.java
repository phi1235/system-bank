package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Admin reconciliation API payloads. */
public final class ReconDtos {

  private ReconDtos() {}

  public record RunReconRequest(@NotNull LocalDate date) {}

  public record ReconRunResponse(
      String id,
      LocalDate businessDate,
      String zone,
      String triggerType,
      String status,
      Instant startedAt,
      Instant finishedAt,
      int ordersChecked,
      int ledgerEntriesSeen,
      int discrepancyCount,
      String errorDetail) {}

  public record ReconItemResponse(
      String id,
      String transferId,
      String kind,
      String entryRef,
      BigDecimal expectedAmount,
      BigDecimal actualAmount,
      String detail) {}

  public record ReconRunDetailResponse(ReconRunResponse run, List<ReconItemResponse> items) {}
}
