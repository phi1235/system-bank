package com.banksystem.transaction.infrastructure.gateway;

import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignAccountGateway implements AccountGateway {

  private final AccountClient accountClient;
  private final String internalApiKey;

  public FeignAccountGateway(
      Optional<AccountClient> accountClient,
      @Value("${bank.internal.account-api-key:internal-secret-key-12345}") String internalApiKey) {
    this.accountClient = accountClient.orElse(null);
    this.internalApiKey = internalApiKey;
  }

  @Override
  public AccountView getAccount(UUID accountId) {
    if (accountClient == null) return null;
    var resp = accountClient.getById(accountId, internalApiKey);
    return resp != null ? resp.data() : null;
  }

  @Override
  public AccountView getAccountByNumber(String accountNumber) {
    if (accountClient == null) return null;
    var resp = accountClient.getByNumber(accountNumber, internalApiKey);
    return resp != null ? resp.data() : null;
  }

  @Override
  public MoneyResult debit(UUID accountId, MoneyCommand command) {
    if (accountClient == null) return null;
    var resp = accountClient.debit(accountId, command, internalApiKey);
    return resp != null ? resp.data() : null;
  }

  @Override
  public MoneyResult credit(UUID accountId, MoneyCommand command) {
    if (accountClient == null) return null;
    var resp = accountClient.credit(accountId, command, internalApiKey);
    return resp != null ? resp.data() : null;
  }
}
