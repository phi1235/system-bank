package com.banksystem.transaction.infrastructure.napas;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MockNapasSwitchService {

  public record NapasInquiryResponse(
      String bankCode,
      String accountNumber,
      String accountName,
      boolean valid
  ) {}

  public record NapasPaymentResponse(
      String napasRefId,
      boolean success,
      String responseCode,
      String responseMessage
  ) {}

  /**
   * Mock NAPAS 24/7 Account Name Lookup.
   */
  public NapasInquiryResponse inquireAccount(String bankCode, String accountNumber) {
    if (accountNumber == null || accountNumber.isBlank()) {
      return new NapasInquiryResponse(bankCode, accountNumber, "", false);
    }
    String cleanNum = accountNumber.trim();
    // Deterministic mock name generation based on account number for testing
    String mockName = switch (cleanNum.substring(Math.max(0, cleanNum.length() - 2))) {
      case "01", "11", "21" -> "NGUYEN VAN AN";
      case "02", "12", "22" -> "TRAN THI BINH";
      case "03", "13", "23" -> "LE HOANG CUONG";
      case "04", "14", "24" -> "PHAM MINH DUC";
      default -> "NGUYEN THI " + cleanNum.substring(Math.max(0, cleanNum.length() - 4));
    };
    return new NapasInquiryResponse(bankCode, cleanNum, mockName, true);
  }

  /**
   * Mock NAPAS 24/7 Interbank Payment Execution.
   */
  public NapasPaymentResponse executePayment(
      String sourceAccountNumber,
      String targetBankCode,
      String targetAccountNumber,
      BigDecimal amount,
      String description) {
    String napasRefId = "NAPAS247-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return new NapasPaymentResponse(napasRefId, true, "00", "SUCCESS");
  }
}
