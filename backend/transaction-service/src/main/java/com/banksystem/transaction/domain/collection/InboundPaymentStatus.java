package com.banksystem.transaction.domain.collection;

public enum InboundPaymentStatus {
  RECEIVED,
  UNMATCHED,
  MISMATCH,
  LEDGER_PENDING,
  LEDGER_POSTED,
  FINALIZE_PENDING,
  PROCESSED,
  PENDING_RECOVERY,
  FAILED,
  DEAD_LETTER
}
