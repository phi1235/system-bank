package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class OpenBankingDtos {

  private OpenBankingDtos() {}

  public record OpenBankingRecordFilterRequest(
      String messageId,
      String status,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size) {}
}
