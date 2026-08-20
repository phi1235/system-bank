package com.banksystem.transaction.domain.settlement;

public enum SettlementLegStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED,
  RETRYING
}
