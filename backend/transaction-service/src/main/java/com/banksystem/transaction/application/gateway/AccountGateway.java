package com.banksystem.transaction.application.gateway;

import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.UUID;

public interface AccountGateway {
  AccountView getAccount(UUID accountId);
  AccountView getAccountByNumber(String accountNumber);
  MoneyResult debit(UUID accountId, MoneyCommand command);
  MoneyResult credit(UUID accountId, MoneyCommand command);
}
