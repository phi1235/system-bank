package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.account.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.account.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.entity.account.LedgerEntryEntity;
import com.banksystem.account.domain.enums.account.LedgerEntryType;
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
        a.getStatus(),
        a.getCreatedAt(),
        a.getUpdatedAt()
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
