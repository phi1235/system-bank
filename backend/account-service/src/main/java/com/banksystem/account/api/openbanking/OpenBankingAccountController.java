package com.banksystem.account.api.openbanking;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.StatementFilterRequest;
import com.banksystem.account.application.openbanking.OpenBankingAccountService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.iso20022.Camt053Dto;
import com.banksystem.common.security.B2bClientPrincipal;
import com.banksystem.common.security.B2bContext;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-banking/v1/accounts")
public class OpenBankingAccountController {

  private final OpenBankingAccountService accountService;

  public OpenBankingAccountController(OpenBankingAccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<List<AccountResponse>> listAccounts() {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_ACCOUNTS_READ);
    B2bClientPrincipal client = B2bContext.requireClient();
    List<AccountResponse> accounts = accountService.listAccountsForB2bClient(client.clientId());
    return ApiResponse.ok(accounts);
  }

  @GetMapping(value = "/{accountNumber}/balances", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<AccountResponse> getAccountBalance(@PathVariable String accountNumber) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_ACCOUNTS_READ);
    B2bClientPrincipal client = B2bContext.requireClient();
    AccountResponse balance = accountService.getAccountBalanceForB2bClient(client.clientId(), accountNumber);
    return ApiResponse.ok(balance);
  }

  @GetMapping(value = "/{accountNumber}/statements", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
  public ResponseEntity<Camt053Dto> getCamt053Statement(
      @PathVariable String accountNumber,
      @Valid @ModelAttribute StatementFilterRequest req) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_STATEMENTS_READ);
    B2bClientPrincipal client = B2bContext.requireClient();
    Camt053Dto statement = accountService.generateCamt053Statement(client.clientId(), accountNumber, req);
    return ResponseEntity.ok(statement);
  }
}
