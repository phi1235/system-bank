package com.banksystem.transaction.application.transfer;

/** Signals a transient beneficiary-resolution provider failure to Resilience4j. */
public class BeneficiaryProviderException extends RuntimeException {

  public BeneficiaryProviderException(String message) {
    super(message);
  }

  public BeneficiaryProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
