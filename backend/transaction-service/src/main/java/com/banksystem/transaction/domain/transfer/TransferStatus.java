package com.banksystem.transaction.domain.transfer;

public enum TransferStatus {
  PENDING,
  DEBITED,
  COMPLETED,
  FAILED,
  COMPENSATING,
  COMPENSATED
}
