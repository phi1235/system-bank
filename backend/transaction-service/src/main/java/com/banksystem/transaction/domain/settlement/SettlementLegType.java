package com.banksystem.transaction.domain.settlement;

public enum SettlementLegType {
  INTERNAL_CREDIT,
  EXTERNAL_PAYOUT,
  COMMISSION,
  OVERPAYMENT_HOLD
}
