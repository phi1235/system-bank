package com.banksystem.transaction.domain.settlement;

public enum B2bPayoutStatus {
  READY,
  DISPATCHING,
  PENDING_RECON,
  SWITCH_SUCCESS_LEDGER_PENDING,
  SUCCESS,
  FAILED,
  DEAD_LETTER,
  MANUAL_REVIEW
}
