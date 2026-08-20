package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.DebitAgainstHoldCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.TransactionLedgerEvidenceView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountStateEvidenceView;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ACCOUNT-SERVICE", contextId = "accountClient", fallback = AccountClientFallback.class, url = "${bank.feign.account-url}")
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

  @PostMapping("/internal/accounts/{id}/debit-against-hold")
  ApiResponse<MoneyResult> debitAgainstHold(
      @PathVariable("id") UUID id,
      @RequestBody DebitAgainstHoldCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/{id}/credit")
  ApiResponse<MoneyResult> credit(
      @PathVariable("id") UUID id,
      @RequestBody MoneyCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/{id}/compensation-credit")
  ApiResponse<MoneyResult> compensateCredit(
      @PathVariable("id") UUID id,
      @RequestBody MoneyCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/{id}/compensation-credit-against-hold")
  ApiResponse<MoneyResult> compensateCreditAgainstHold(
      @PathVariable("id") UUID id,
      @RequestBody AccountClientDtos.CompensateCreditAgainstHoldCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @GetMapping("/internal/ledger/transactions/{transactionId}")
  ApiResponse<TransactionLedgerEvidenceView> transactionLedgerEvidence(
      @PathVariable("transactionId") UUID transactionId,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @GetMapping("/internal/ledger/accounts/{accountId}/state")
  ApiResponse<AccountStateEvidenceView> accountStateEvidence(
      @PathVariable("accountId") UUID accountId,
      @RequestParam("at") Instant at,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/ledger/holds/accounts/{accountId}")
  ApiResponse<AccountClientDtos.AccountHoldView> createHold(
      @PathVariable("accountId") UUID accountId,
      @RequestBody AccountClientDtos.CreateHoldCommand command,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  @PostMapping("/internal/accounts/remediation/inbox")
  ApiResponse<Boolean> processRemediationInbox(
      @RequestBody AccountClientDtos.AdjustmentRequestedEventRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
