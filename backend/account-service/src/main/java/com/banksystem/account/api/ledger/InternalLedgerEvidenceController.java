package com.banksystem.account.api.ledger;

import com.banksystem.account.api.dto.LedgerEvidenceDtos.AccountStateEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.FinancialEvidenceSearchRequest;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.FinancialEvidenceSearchResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.JournalEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.TransactionLedgerEvidenceResponse;
import com.banksystem.account.application.ledger.LedgerEvidenceQueryService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ledger")
@RequireInternalApiKey
public class InternalLedgerEvidenceController {
  private final LedgerEvidenceQueryService queryService;

  public InternalLedgerEvidenceController(LedgerEvidenceQueryService queryService) {
    this.queryService = queryService;
  }

  @PostMapping("/financial-evidence/search")
  public ApiResponse<FinancialEvidenceSearchResponse> search(
      @Valid @RequestBody FinancialEvidenceSearchRequest request) {
    return ApiResponse.ok(queryService.search(request.referenceIds()));
  }

  @GetMapping("/journals/{journalId}")
  public ApiResponse<JournalEvidenceResponse> journal(@PathVariable UUID journalId) {
    return ApiResponse.ok(queryService.journal(journalId));
  }

  @GetMapping("/accounts/{accountId}/state")
  public ApiResponse<AccountStateEvidenceResponse> accountState(
      @PathVariable UUID accountId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ApiResponse.ok(queryService.accountState(accountId, at));
  }

  @GetMapping("/transactions/{transactionId}")
  public ApiResponse<TransactionLedgerEvidenceResponse> transaction(
      @PathVariable UUID transactionId) {
    return ApiResponse.ok(queryService.transaction(transactionId));
  }
}
