package com.banksystem.transaction.application.transfer;

public interface BeneficiaryInquiryPort {

  record InquiryResult(
      String bankBin,
      String accountNumber,
      String accountName,
      boolean verified,
      String provider,
      String errorCode,
      String errorMessage
  ) {
    public static InquiryResult success(String bankBin, String accountNumber, String accountName, String provider) {
      return new InquiryResult(bankBin, accountNumber, accountName, true, provider, null, null);
    }

    public static InquiryResult failure(String bankBin, String accountNumber, String provider, String errorCode, String errorMessage) {
      return new InquiryResult(bankBin, accountNumber, null, false, provider, errorCode, errorMessage);
    }
  }

  InquiryResult inquire(String bankBin, String accountNumber);

  boolean supports(String bankBin);
}
