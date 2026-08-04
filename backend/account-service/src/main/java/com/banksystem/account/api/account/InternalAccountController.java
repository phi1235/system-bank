package com.banksystem.account.api.account;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
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

import com.banksystem.account.api.dto.AccountDtos.InternalAccountCountsResponse;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

  private final AccountMoneyService service;
  private final AccountRepository accountRepository;
  private final String apiKey;

  public InternalAccountController(
      AccountMoneyService service,
      AccountRepository accountRepository,
      @Value("${bank.internal.api-key:internal-dev-key}") String apiKey) {
    this.service = service;
    this.accountRepository = accountRepository;
    this.apiKey = apiKey;
  }

  @GetMapping("/counts")
  public ApiResponse<InternalAccountCountsResponse> counts(
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    long total = accountRepository.count();
    long frozen = accountRepository.countByStatus("FROZEN");
    return ApiResponse.ok(new InternalAccountCountsResponse(total, frozen));
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
