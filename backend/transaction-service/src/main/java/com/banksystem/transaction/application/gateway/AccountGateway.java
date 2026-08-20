package com.banksystem.transaction.application.gateway;

import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountHoldView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AdjustmentRequestedEventRequest;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.CreateHoldCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.UUID;

public interface AccountGateway {
  AccountView getAccount(UUID accountId);
  AccountView getAccountByNumber(String accountNumber);
  MoneyResult debit(UUID accountId, MoneyCommand command);
  MoneyResult debitAgainstHold(UUID accountId, UUID holdId, UUID batchId, MoneyCommand command);
  MoneyResult credit(UUID accountId, MoneyCommand command);
  MoneyResult compensateCredit(UUID accountId, MoneyCommand command);
  MoneyResult compensateCreditAgainstHold(
      UUID accountId, UUID holdId, UUID batchId, MoneyCommand command);
  AccountHoldView createHold(UUID accountId, CreateHoldCommand command);
  boolean processRemediationInbox(AdjustmentRequestedEventRequest request);
}
