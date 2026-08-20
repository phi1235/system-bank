package com.banksystem.transaction.domain.merchant;

public enum MerchantWebhookDeliveryStatus {
  PENDING,
  SENDING,
  SUCCESS,
  RETRYING,
  DEAD_LETTER
}
