package com.banksystem.account.api.account;

import com.banksystem.account.api.dto.AccountDtos.AccountOwnershipResponse;
import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.AdjustmentRequestedEventRequest;
import com.banksystem.account.api.dto.AccountDtos.CompensateCreditAgainstHoldCommand;
import com.banksystem.account.api.dto.AccountDtos.CreateCorporateAccountRequest;
import com.banksystem.account.api.dto.AccountDtos.DebitAgainstHoldCommand;
import com.banksystem.account.api.dto.AccountDtos.InternalAccountCountsResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.application.account.AccountMoneyService;
import com.banksystem.account.application.account.InternalAccountQueryService;
import com.banksystem.account.application.ledger.AccountInboxService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.List;
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
  private final AccountInboxService inboxService;

  public InternalAccountController(
      AccountMoneyService moneyService,
      InternalAccountQueryService queryService,
      AccountInboxService inboxService) {
    this.moneyService = moneyService;
    this.queryService = queryService;
    this.inboxService = inboxService;
  }

  @PostMapping("/remediation/inbox")
  public ApiResponse<Boolean> processRemediationInbox(
      @Valid @RequestBody AdjustmentRequestedEventRequest req) {
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

  @GetMapping("/owners/{ownerType}/{ownerId}")
  public ApiResponse<List<AccountResponse>> listByOwner(
      @PathVariable String ownerType,
      @PathVariable UUID ownerId) {
    return ApiResponse.ok(queryService.listByOwner(ownerType, ownerId));
  }

  @PostMapping("/corporate")
  public ApiResponse<AccountResponse> createCorporateAccount(
      @Valid @RequestBody CreateCorporateAccountRequest req) {
    return ApiResponse.ok(queryService.createCorporateAccount(req));
  }

  @GetMapping("/{id}")
  public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(moneyService.getInternal(id));
  }

  @GetMapping("/{id}/ownership")
  public ApiResponse<AccountOwnershipResponse> ownership(@PathVariable UUID id) {
    return ApiResponse.ok(moneyService.getOwnership(id));
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

  @PostMapping("/{id}/debit-against-hold")
  public ApiResponse<MoneyResult> debitAgainstHold(
      @PathVariable UUID id,
      @Valid @RequestBody DebitAgainstHoldCommand command) {
    return ApiResponse.ok(moneyService.debitAgainstHold(id, command));
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

  @PostMapping("/{id}/compensation-credit-against-hold")
  public ApiResponse<MoneyResult> compensationCreditAgainstHold(
      @PathVariable UUID id,
      @Valid @RequestBody CompensateCreditAgainstHoldCommand command) {
    return ApiResponse.ok(moneyService.compensateCreditAgainstHold(id, command));
  }
}
