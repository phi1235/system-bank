package com.banksystem.transaction.domain.collection;

public enum CollectionOrderStatus {
  PENDING,
  PAYMENT_PROCESSING,
  PARTIAL,
  PAID,
  OVERPAID,
  EXPIRED,
  CANCELLED,
  REVIEW
}
