package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService.InquiryRequest;
import java.io.Serializable;

/**
 * Application layer Query Object encapsulating input normalization and validation.
 */
public record BeneficiaryInquiryQuery(String bankIdentifier, String accountNumber) implements Serializable {

  public static BeneficiaryInquiryQuery of(InquiryRequest req) {
    if (req == null || req.accountNumber() == null || req.accountNumber().isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required");
    }
    String bankIdentifier = req.bankBin() == null || req.bankBin().isBlank()
        ? (req.bankCode() == null || req.bankCode().isBlank() ? "970499" : req.bankCode().trim())
        : req.bankBin().trim();
    return new BeneficiaryInquiryQuery(bankIdentifier, normalizeAccountNumber(req.accountNumber()));
  }

  public static String normalizeAccountNumber(String accountNumber) {
    String normalized = accountNumber == null ? "" : accountNumber.replaceAll("\\s+", "");
    if (!normalized.matches("\\d{6,19}")) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number must contain 6 to 19 digits");
    }
    return normalized;
  }
}
