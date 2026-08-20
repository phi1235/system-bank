package com.banksystem.account.application.sweep;

import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepListRequest;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepOperationsRequest;

public final class AutoSweepQueries {
  private AutoSweepQueries() {}

  public record ListQuery(int page, int size) {
    public static ListQuery of(AutoSweepListRequest request) {
      int page = request.page() == null ? 0 : request.page();
      int size = request.size() == null ? 20 : request.size();
      return new ListQuery(page, size);
    }
  }

  public record OperationsQuery(int limit) {
    public static OperationsQuery of(AutoSweepOperationsRequest request) {
      return new OperationsQuery(request.limit() == null ? 30 : request.limit());
    }
  }
}
