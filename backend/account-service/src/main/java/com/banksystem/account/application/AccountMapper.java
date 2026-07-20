package com.banksystem.account.application;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.LedgerEntryEntity;
import com.banksystem.account.domain.LedgerEntryType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** DTO mapping only — no business rules. */
@Component
public class AccountMapper {

  public AccountResponse toResponse(AccountEntity a) {
    return new AccountResponse(
        a.getId().toString(),
        a.getUserId().toString(),
        a.getAccountNumber(),
        a.getAccountType(),
        a.getCurrency(),
        a.getBalance(),
        a.getStatus()
    );
  }

  public LedgerEntryResponse toLedgerResponse(LedgerEntryEntity e) {
    BigDecimal signed = e.getAmount();
    if (LedgerEntryType.DEBIT.name().equalsIgnoreCase(e.getEntryType())) {
      signed = e.getAmount().negate();
    }
    return new LedgerEntryResponse(
        e.getId().toString(),
        e.getAccountId().toString(),
        e.getEntryType(),
        e.getAmount(),
        signed,
        e.getReferenceId(),
        e.getDescription(),
        e.getCreatedAt());
  }
}
