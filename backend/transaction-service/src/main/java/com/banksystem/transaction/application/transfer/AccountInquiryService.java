package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.transfer.ExternalBankAccountEntity;
import com.banksystem.transaction.domain.transfer.ExternalBankAccountRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasInquiryResponse;
import java.util.Optional;
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

  private final AccountGateway accountGateway;
  private final ExternalBankAccountRepository externalAccountRepository;
  private final NapasSwitchClient napasService;

  public AccountInquiryService(
      AccountGateway accountGateway,
      ExternalBankAccountRepository externalAccountRepository,
      NapasSwitchClient napasService) {
    this.accountGateway = accountGateway;
    this.externalAccountRepository = externalAccountRepository;
    this.napasService = napasService;
  }

  @Transactional(readOnly = true)
  public InquiryResponse inquire(InquiryRequest req) {
    if (req == null || req.accountNumber() == null || req.accountNumber().isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required");
    }
    String bankCode = req.bankCode() == null || req.bankCode().isBlank() ? "SYSTEM_BANK" : req.bankCode().trim();
    String accNum = req.accountNumber().trim();

    if ("SYSTEM_BANK".equalsIgnoreCase(bankCode) || "970499".equals(bankCode)) {
      try {
        AccountView acc = accountGateway.getAccountByNumber(accNum);
        if (acc == null) {
          throw new BusinessException("ACCOUNT_NOT_FOUND", "Internal account not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(acc.status())) {
          throw new BusinessException("ACCOUNT_FROZEN", "Internal account is not active");
        }
        String recipientName = "TK KH (" + acc.accountNumber().substring(Math.max(0, acc.accountNumber().length() - 4)) + ")";
        return new InquiryResponse("SYSTEM_BANK", acc.accountNumber(), recipientName, true, acc.id());
      } catch (BusinessException be) {
        throw be;
      } catch (Exception ex) {
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Internal account not found");
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
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Interbank account not found via NAPAS 247");
      }
      return new InquiryResponse(bankCode, accNum, napasRes.accountName(), false, null);
    }
  }
}
