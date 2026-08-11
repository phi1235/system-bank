package com.banksystem.transaction.application.risk;

public record RiskListQuery(int page, int size) {
  public static RiskListQuery of(Integer page, Integer size) {
    int safePage = page == null ? 0 : Math.max(page, 0);
    int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
    return new RiskListQuery(safePage, safeSize);
  }
}
