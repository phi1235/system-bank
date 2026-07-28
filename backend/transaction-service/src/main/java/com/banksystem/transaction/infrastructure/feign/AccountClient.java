package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "ACCOUNT-SERVICE", url = "${ACCOUNT_SERVICE_URL:}")
public interface AccountClient {

  @GetMapping("/internal/accounts/{id}")
  ApiResponse<AccountView> getById(
      @PathVariable("id") UUID id,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @GetMapping("/internal/accounts/by-number/{accountNumber}")
  ApiResponse<AccountView> getByNumber(
      @PathVariable("accountNumber") String accountNumber,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/{id}/debit")
  ApiResponse<MoneyResult> debit(
      @PathVariable("id") UUID id,
      @RequestBody MoneyCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/{id}/credit")
  ApiResponse<MoneyResult> credit(
      @PathVariable("id") UUID id,
      @RequestBody MoneyCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
