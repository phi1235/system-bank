package com.banksystem.account.api.ledger;

import com.banksystem.account.api.dto.AtomicLedgerDtos.AtomicPostingResponse;
import com.banksystem.account.api.dto.AtomicLedgerDtos.CollectionReceiptCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.PayoutClearingCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.SettlementPostingCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.SettlementReversalCommand;
import com.banksystem.account.application.ledger.AtomicLedgerPostingService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ledger")
@RequireInternalApiKey
public class InternalAtomicLedgerController {

  private final AtomicLedgerPostingService atomicLedgerPostingService;

  public InternalAtomicLedgerController(AtomicLedgerPostingService atomicLedgerPostingService) {
    this.atomicLedgerPostingService = atomicLedgerPostingService;
  }

  @PostMapping("/collection-receipts")
  public ApiResponse<AtomicPostingResponse> recordCollectionReceipt(
      @Valid @RequestBody CollectionReceiptCommand command) {
    return ApiResponse.ok(atomicLedgerPostingService.recordCollectionReceipt(command));
  }

  @PostMapping("/settlements")
  public ApiResponse<AtomicPostingResponse> recordSettlement(
      @Valid @RequestBody SettlementPostingCommand command) {
    return ApiResponse.ok(atomicLedgerPostingService.recordSettlement(command));
  }

  @PostMapping("/payout-clearings")
  public ApiResponse<AtomicPostingResponse> recordPayoutClearing(
      @Valid @RequestBody PayoutClearingCommand command) {
    return ApiResponse.ok(atomicLedgerPostingService.recordPayoutClearing(command));
  }

  @PostMapping("/settlements/{journalId}/reverse")
  public ApiResponse<AtomicPostingResponse> reverseSettlement(
      @PathVariable UUID journalId,
      @Valid @RequestBody SettlementReversalCommand command) {
    return ApiResponse.ok(atomicLedgerPostingService.reverseSettlement(journalId, command));
  }
}
