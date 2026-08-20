package com.banksystem.transaction.domain.settlement;

public enum SettlementStatus {
  PREPARING,
  LEDGER_PENDING,
  LEDGER_POSTED,
  PAYOUT_PENDING,
  PROCESSING,
  COMPLETED,
  FAILED,
  MANUAL_REVIEW,
  REVERSED
}
