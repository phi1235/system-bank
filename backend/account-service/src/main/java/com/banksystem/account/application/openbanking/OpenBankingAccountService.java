package com.banksystem.account.application.openbanking;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.StatementFilterRequest;
import com.banksystem.common.iso20022.Camt053Dto;
import java.util.List;

public interface OpenBankingAccountService {

  List<AccountResponse> listAccountsForB2bClient(String clientId);

  AccountResponse getAccountBalanceForB2bClient(String clientId, String accountNumber);

  Camt053Dto generateCamt053Statement(String clientId, String accountNumber, StatementFilterRequest filter);
}
