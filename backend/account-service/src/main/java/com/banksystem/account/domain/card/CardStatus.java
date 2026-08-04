package com.banksystem.account.domain.card;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

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
