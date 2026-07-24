package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.ExternalBankAccountEntity;
import com.banksystem.transaction.domain.ExternalBankAccountRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasInquiryResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountInquiryService {

  public record InquiryRequest(
      String bankCode,
      String accountNumber
  ) {}

  public record InquiryResponse(
      String bankCode,
      String accountNumber,
      String accountName,
      boolean isInternal,
      String accountId
  ) {}

  private final AccountClient accountClient;
  private final ExternalBankAccountRepository externalAccountRepository;
  private final NapasSwitchClient napasService;
  private final String internalApiKey;

  public AccountInquiryService(
      AccountClient accountClient,
      ExternalBankAccountRepository externalAccountRepository,
      NapasSwitchClient napasService,
      @Value("${bank.internal.account-api-key}") String internalApiKey) {
    this.accountClient = accountClient;
    this.externalAccountRepository = externalAccountRepository;
    this.napasService = napasService;
    this.internalApiKey = internalApiKey;
  }

  @Transactional(readOnly = true)
  public InquiryResponse inquire(InquiryRequest req) {
    if (req == null || req.accountNumber() == null || req.accountNumber().isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required", HttpStatus.BAD_REQUEST);
    }
    String bankCode = req.bankCode() == null || req.bankCode().isBlank() ? "SYSTEM_BANK" : req.bankCode().trim();
    String accNum = req.accountNumber().trim();

    if ("SYSTEM_BANK".equalsIgnoreCase(bankCode) || "970499".equals(bankCode)) {
      try {
        var res = accountClient.getByNumber(accNum, internalApiKey);
        if (res == null || res.data() == null) {
          throw new BusinessException("ACCOUNT_NOT_FOUND", "Internal account not found", HttpStatus.NOT_FOUND);
        }
        AccountView acc = res.data();
        if (!"ACTIVE".equalsIgnoreCase(acc.status())) {
          throw new BusinessException("ACCOUNT_FROZEN", "Internal account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String recipientName = "TK KH (" + acc.accountNumber().substring(Math.max(0, acc.accountNumber().length() - 4)) + ")";
        return new InquiryResponse("SYSTEM_BANK", acc.accountNumber(), recipientName, true, acc.id());
      } catch (Exception ex) {
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Internal account not found", HttpStatus.NOT_FOUND);
      }
    } else {
      // First try DB table external_bank_accounts
      Optional<ExternalBankAccountEntity> dbAcc = externalAccountRepository.findByBankCodeAndAccountNumber(bankCode, accNum);
      if (dbAcc.isPresent()) {
        return new InquiryResponse(bankCode, accNum, dbAcc.get().getAccountHolderName(), false, null);
      }

      // Fallback to NAPAS Switch mock generator if not in DB seed
      NapasInquiryResponse napasRes = napasService.inquireAccount(bankCode, accNum);
      if (!napasRes.valid()) {
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Interbank account not found via NAPAS 247", HttpStatus.NOT_FOUND);
      }
      return new InquiryResponse(bankCode, accNum, napasRes.accountName(), false, null);
    }
  }
}
