package com.banksystem.transaction.domain.transfer;

public enum TransferStatus {
  PENDING,
  DEBITED,
  SUBMITTED,
  PROCESSING,
  COMPLETED,
  FAILED,
  COMPENSATING,
  COMPENSATED,
  /** Provider outcome is not known yet; never refund automatically until inquiry/callback resolves it. */
  UNKNOWN,
  /** Money movement is known but an accounting/compensation leg needs operator intervention. */
  REVIEW_REQUIRED,
  /** Escalated to operations after reconciliation attempts are exhausted. */
  MANUAL_REVIEW,
  RISK_REVIEW
}
