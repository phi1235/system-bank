package com.banksystem.transaction.infrastructure.gateway;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.Optional;
import java.util.UUID;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountHoldView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AdjustmentRequestedEventRequest;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.CreateHoldCommand;

import com.banksystem.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FeignAccountGateway implements AccountGateway {

  private final AccountClient accountClient;
  private final String internalApiKey;

  public FeignAccountGateway(
      Optional<AccountClient> accountClient,
      @Value("${bank.internal.account-api-key}") String internalApiKey) {
    this.accountClient = accountClient.orElse(null);
    this.internalApiKey = internalApiKey;
  }

  @Override
  public AccountView getAccount(UUID accountId) {
    if (accountClient == null) return null;
    ApiResponse<AccountView> resp = accountClient.getById(accountId, internalApiKey);
    return resp != null ? resp.data() : null;
  }

  @Override
  public AccountView getAccountByNumber(String accountNumber) {
    if (accountClient == null) return null;
    ApiResponse<AccountView> resp = accountClient.getByNumber(accountNumber, internalApiKey);
    return resp != null ? resp.data() : null;
  }

  @Override
  public MoneyResult debit(UUID accountId, MoneyCommand command) {
    if (accountClient == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountClient is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    ApiResponse<MoneyResult> resp = accountClient.debit(accountId, command, internalApiKey);
    if (resp == null || resp.data() == null) {
      throw new BusinessException("ACCOUNT_REMEDIATION_FAILED", "Failed to execute debit posting on account-service");
    }
    return resp.data();
  }

  @Override
  public MoneyResult credit(UUID accountId, MoneyCommand command) {
    if (accountClient == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountClient is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    ApiResponse<MoneyResult> resp = accountClient.credit(accountId, command, internalApiKey);
    if (resp == null || resp.data() == null) {
      throw new BusinessException("ACCOUNT_REMEDIATION_FAILED", "Failed to execute credit posting on account-service");
    }
    return resp.data();
  }

  @Override
  public MoneyResult compensateCredit(UUID accountId, MoneyCommand command) {
    if (accountClient == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountClient is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    ApiResponse<MoneyResult> resp =
        accountClient.compensateCredit(accountId, command, internalApiKey);
    if (resp == null || resp.data() == null) {
      throw new BusinessException("ACCOUNT_REMEDIATION_FAILED", "Failed to execute compensate credit on account-service");
    }
    return resp.data();
  }

  @Override
  public AccountHoldView createHold(UUID accountId, CreateHoldCommand command) {
    if (accountClient == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountClient is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    ApiResponse<AccountHoldView> resp = accountClient.createHold(accountId, command, internalApiKey);
    if (resp == null || resp.data() == null) {
      throw new BusinessException("ACCOUNT_REMEDIATION_FAILED", "Failed to execute account hold on account-service");
    }
    return resp.data();
  }

  @Override
  public boolean processRemediationInbox(AdjustmentRequestedEventRequest request) {
    if (accountClient == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountClient is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
    ApiResponse<Boolean> resp = accountClient.processRemediationInbox(request, internalApiKey);
    if (resp == null || resp.data() == null) {
      throw new BusinessException("ACCOUNT_REMEDIATION_FAILED", "Failed to process remediation event on account-service inbox");
    }
    return Boolean.TRUE.equals(resp.data());
  }
}
