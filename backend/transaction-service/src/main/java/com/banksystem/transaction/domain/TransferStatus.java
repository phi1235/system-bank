package com.banksystem.transaction.domain;

public enum TransferStatus {
  PENDING,
  DEBITED,
  COMPLETED,
  FAILED,
  COMPENSATING,
  COMPENSATED
}
