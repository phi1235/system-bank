package com.banksystem.transaction.infrastructure.napas;

import java.math.BigDecimal;

/** NAPAS 24/7 switch adapter (inquiry + payment). */
public interface NapasSwitchClient {

  record NapasInquiryResponse(
      String bankCode, String accountNumber, String accountName, boolean valid) {}

  record NapasPaymentResponse(
      String napasRefId, boolean success, String responseCode, String responseMessage) {}

  NapasInquiryResponse inquireAccount(String bankCode, String accountNumber);

  NapasPaymentResponse executePayment(
      String sourceAccountNumber,
      String targetBankCode,
      String targetAccountNumber,
      BigDecimal amount,
      String description);
}
