package com.banksystem.transaction.infrastructure.inquiry;

import com.banksystem.transaction.application.transfer.BeneficiaryInquiryPort;
import com.banksystem.transaction.domain.transfer.ExternalBankAccountEntity;
import com.banksystem.transaction.domain.transfer.ExternalBankAccountRepository;
import com.banksystem.transaction.domain.transfer.BankDirectoryRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.inquiry.provider", havingValue = "mock", matchIfMissing = true)
public class MockBeneficiaryInquiryAdapter implements BeneficiaryInquiryPort {

  private final ExternalBankAccountRepository externalAccountRepository;
  private final BankDirectoryRepository bankDirectoryRepository;

  public MockBeneficiaryInquiryAdapter(
      ExternalBankAccountRepository externalAccountRepository,
      BankDirectoryRepository bankDirectoryRepository) {
    this.externalAccountRepository = externalAccountRepository;
    this.bankDirectoryRepository = bankDirectoryRepository;
  }

  @Override
  public InquiryResult inquire(String bankBin, String accountNumber) {
    if (accountNumber == null || accountNumber.isBlank()) {
      return InquiryResult.failure(bankBin, accountNumber, "MOCK", "INVALID_ACCOUNT", "Account number is required");
    }
    String cleanNum = accountNumber.trim();

    // 1. Check existing DB seed in external_bank_accounts
    String bankCode = bankDirectoryRepository.findByBin(bankBin)
        .map(bank -> bank.getCode())
        .orElse(bankBin);
    Optional<ExternalBankAccountEntity> dbAcc =
        externalAccountRepository.findByBankCodeAndAccountNumber(bankCode, cleanNum);
    if (dbAcc.isPresent()) {
      return InquiryResult.success(bankBin, cleanNum, dbAcc.get().getAccountHolderName(), "MOCK");
    }

    // 2. Deterministic mock generation for automated tests
    String last2 = cleanNum.length() >= 2 ? cleanNum.substring(cleanNum.length() - 2) : "00";
    String mockName = switch (last2) {
      case "01", "11", "21" -> "NGUYEN VAN AN";
      case "02", "12", "22" -> "TRAN THI BINH";
      case "03", "13", "23" -> "LE HOANG CUONG";
      case "04", "14", "24" -> "PHAM MINH DUC";
      case "99" -> null; // Simulate NOT_FOUND
      default -> "NGUYEN THI " + (cleanNum.length() >= 4 ? cleanNum.substring(cleanNum.length() - 4) : cleanNum);
    };

    if (mockName == null) {
      return InquiryResult.failure(bankBin, cleanNum, "MOCK", "BENEFICIARY_NOT_FOUND", "Beneficiary account not found");
    }

    return InquiryResult.success(bankBin, cleanNum, mockName, "MOCK");
  }

  @Override
  public boolean supports(String bankBin) {
    return bankBin != null && !bankBin.isBlank();
  }
}
