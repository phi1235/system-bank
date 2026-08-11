package com.banksystem.transaction.infrastructure.napas;

import java.math.BigDecimal;

/** NAPAS 24/7 switch adapter (inquiry + payment). */
public interface NapasSwitchClient {

  enum ProviderOutcome { SUCCESS, FAILED, PENDING, UNKNOWN }

  record NapasInquiryResponse(
      String bankCode, String accountNumber, String accountName, boolean valid) {}

  record NapasPaymentResponse(
      String napasRefId, ProviderOutcome outcome, String responseCode, String responseMessage) {}

  NapasInquiryResponse inquireAccount(String bankCode, String accountNumber);

  NapasPaymentResponse executePayment(
      String sourceAccountNumber,
      String targetBankCode,
      String targetAccountNumber,
      BigDecimal amount,
      String description,
      String clientRequestId);

  /** Non-mutating status inquiry used after a timeout or asynchronous provider acceptance. */
  NapasPaymentResponse inquirePayment(String clientRequestId, String napasRefId);
}
