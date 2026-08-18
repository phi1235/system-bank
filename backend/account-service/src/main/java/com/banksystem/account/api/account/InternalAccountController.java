package com.banksystem.account.api.account;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.InternalAccountCountsResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.application.account.AccountMoneyService;
import com.banksystem.account.application.account.InternalAccountQueryService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
@RequireInternalApiKey
public class InternalAccountController {

  private final AccountMoneyService moneyService;
  private final InternalAccountQueryService queryService;
  private final com.banksystem.account.application.ledger.AccountInboxService inboxService;

  public InternalAccountController(
      AccountMoneyService moneyService,
      InternalAccountQueryService queryService,
      com.banksystem.account.application.ledger.AccountInboxService inboxService) {
    this.moneyService = moneyService;
    this.queryService = queryService;
    this.inboxService = inboxService;
  }

  @PostMapping("/remediation/inbox")
  public ApiResponse<Boolean> processRemediationInbox(
      @Valid @RequestBody com.banksystem.account.api.dto.AccountDtos.AdjustmentRequestedEventRequest req) {
    boolean processed = inboxService.processAdjustmentRequestedEvent(
        req.eventId(),
        req.proposalId(),
        req.caseId(),
        req.cycle(),
        req.targetAccountId(),
        req.direction(),
        req.amount(),
        req.currency(),
        req.referenceId(),
        req.reason());
    return ApiResponse.ok(processed);
  }

  @GetMapping("/counts")
  public ApiResponse<InternalAccountCountsResponse> counts() {
    return ApiResponse.ok(queryService.counts());
  }

  @GetMapping("/{id}")
  public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(moneyService.getInternal(id));
  }

  @GetMapping("/by-number/{accountNumber}")
  public ApiResponse<AccountResponse> byNumber(@PathVariable String accountNumber) {
    return ApiResponse.ok(moneyService.getByNumber(accountNumber));
  }

  @PostMapping("/{id}/debit")
  public ApiResponse<MoneyResult> debit(
      @PathVariable UUID id,
      @Valid @RequestBody MoneyCommand command) {
    return ApiResponse.ok(moneyService.debit(id, command));
  }

  @PostMapping("/{id}/credit")
  public ApiResponse<MoneyResult> credit(
      @PathVariable UUID id,
      @Valid @RequestBody MoneyCommand command) {
    return ApiResponse.ok(moneyService.credit(id, command));
  }

  @PostMapping("/{id}/compensation-credit")
  public ApiResponse<MoneyResult> compensationCredit(
      @PathVariable UUID id,
      @Valid @RequestBody MoneyCommand command) {
    return ApiResponse.ok(moneyService.compensateCredit(id, command));
  }
}
