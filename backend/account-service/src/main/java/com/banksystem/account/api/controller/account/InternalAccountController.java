package com.banksystem.account.api.controller.account;

import com.banksystem.account.api.dto.account.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyResult;
import com.banksystem.account.application.account.AccountMoneyService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.SecretVerifier;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

  private final AccountMoneyService service;
  private final String apiKey;

  public InternalAccountController(
      AccountMoneyService service,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.service = service;
    this.apiKey = apiKey;
  }

  @GetMapping("/{id}")
  public ApiResponse<AccountResponse> get(
      @PathVariable UUID id,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(service.getInternal(id));
  }

  @GetMapping("/by-number/{accountNumber}")
  public ApiResponse<AccountResponse> byNumber(
      @PathVariable String accountNumber,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(service.getByNumber(accountNumber));
  }

  @PostMapping("/{id}/debit")
  public ApiResponse<MoneyResult> debit(
      @PathVariable UUID id,
      @Valid @RequestBody MoneyCommand cmd,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(service.debit(id, cmd));
  }

  @PostMapping("/{id}/credit")
  public ApiResponse<MoneyResult> credit(
      @PathVariable UUID id,
      @Valid @RequestBody MoneyCommand cmd,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(service.credit(id, cmd));
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
