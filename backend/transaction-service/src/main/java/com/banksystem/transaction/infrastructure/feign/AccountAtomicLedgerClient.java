package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "ACCOUNT-SERVICE",
    contextId = "accountAtomicLedgerClient",
    url = "${bank.feign.account-url}",
    configuration = AccountLedgerClientConfig.class
)
public interface AccountAtomicLedgerClient {

  @PostMapping("/internal/ledger/collection-receipts")
  ApiResponse<AtomicPostingView> recordCollectionReceipt(
      @RequestBody CollectionReceiptCommand command);

  @PostMapping("/internal/ledger/settlements")
  ApiResponse<AtomicPostingView> recordSettlement(
      @RequestBody SettlementPostingCommand command);

  @PostMapping("/internal/ledger/payout-clearings")
  ApiResponse<AtomicPostingView> recordPayoutClearing(
      @RequestBody PayoutClearingCommand command);

  @PostMapping("/internal/ledger/settlements/{journalId}/reverse")
  ApiResponse<AtomicPostingView> reverseSettlement(
      @PathVariable("journalId") UUID journalId,
      @RequestBody SettlementReversalCommand command);

  record CollectionReceiptCommand(
      String businessCommandId,
      String businessReference,
      UUID transactionId,
      UUID collectionAccountId,
      String clearingAccountCode,
      BigDecimal amount,
      String currency,
      String description
  ) {}

  record SettlementLegCommand(
      UUID accountId,
      String ledgerAccountCode,
      BigDecimal amount,
      String description
  ) {}

  record SettlementPostingCommand(
      String businessCommandId,
      String businessReference,
      UUID transactionId,
      UUID sourceAccountId,
      String currency,
      BigDecimal grossAmount,
      List<SettlementLegCommand> legs,
      String description
  ) {}

  record PayoutClearingCommand(
      String businessCommandId,
      String businessReference,
      UUID payoutId,
      String payableAccountCode,
      String clearingAccountCode,
      BigDecimal amount,
      String currency,
      String description
  ) {}

  record SettlementReversalCommand(
      String businessCommandId,
      String reason
  ) {}

  record AtomicPostingView(
      UUID journalId,
      String businessCommandId,
      String status,
      String journalType,
      String currency,
      BigDecimal amount,
      Instant postedAt
  ) {}
}
