package com.banksystem.transaction.domain.sepay;

public enum SepayWebhookProcessingStatus {
  RECEIVED,
  PROCESSING,
  IN_PROGRESS,
  PROCESSED,
  FAILED_RETRYABLE,
  MANUAL_REVIEW,
  DUPLICATE,
  IGNORED,
  ERROR
}
