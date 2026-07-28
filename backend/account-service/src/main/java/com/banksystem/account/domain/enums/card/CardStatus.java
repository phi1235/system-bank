package com.banksystem.account.domain.enums.card;

public enum CardStatus {
  /** Customer requested; awaiting back-office approval. No PAN exists yet. */
  REQUESTED,
  /** Staff declined the request (mandatory reason). Terminal; does not block a new request. */
  REJECTED,
  PENDING_ACTIVATION,
  ACTIVE,
  LOCKED,
  CLOSED
}
