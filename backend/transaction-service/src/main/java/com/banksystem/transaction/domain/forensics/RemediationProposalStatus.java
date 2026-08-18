package com.banksystem.transaction.domain.forensics;

public enum RemediationProposalStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  EXECUTION_PENDING,
  EXECUTING,
  POSTED,
  VERIFIED,

  REJECTED,
  EXECUTION_FAILED,
  VERIFICATION_FAILED,
  CANCELLED
}
