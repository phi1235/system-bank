package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for AccountClient when ACCOUNT-SERVICE is unavailable or timing out.
 */
@Component
public class AccountClientFallback implements AccountClient {

  @Override
  public ApiResponse<AccountView> getById(UUID id, String apiKey) {
    throw new BusinessException(
        "ACCOUNT_SERVICE_UNAVAILABLE",
        "Account service is currently unavailable. Please try again later.",
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Override
  public ApiResponse<AccountView> getByNumber(String accountNumber, String apiKey) {
    throw new BusinessException(
        "ACCOUNT_SERVICE_UNAVAILABLE",
        "Account service is currently unavailable. Please try again later.",
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Override
  public ApiResponse<MoneyResult> debit(UUID id, MoneyCommand command, String apiKey) {
    throw new BusinessException(
        "ACCOUNT_SERVICE_UNAVAILABLE",
        "Unable to process account debit as Account service is unavailable.",
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Override
  public ApiResponse<MoneyResult> credit(UUID id, MoneyCommand command, String apiKey) {
    throw new BusinessException(
        "ACCOUNT_SERVICE_UNAVAILABLE",
        "Unable to process account credit as Account service is unavailable.",
        HttpStatus.SERVICE_UNAVAILABLE);
  }
}
