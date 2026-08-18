package com.banksystem.account.api.ledger;

import com.banksystem.account.api.dto.LedgerEvidenceDtos.JournalEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.ReverseJournalRequest;
import com.banksystem.account.application.ledger.DoubleEntryJournalService;
import com.banksystem.account.application.ledger.LedgerEvidenceQueryService;
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
@RequestMapping("/internal/ledger/journals")
@RequireInternalApiKey
public class InternalLedgerCommandController {
  private final DoubleEntryJournalService journalService;
  private final LedgerEvidenceQueryService queryService;

  public InternalLedgerCommandController(
      DoubleEntryJournalService journalService,
      LedgerEvidenceQueryService queryService) {
    this.journalService = journalService;
    this.queryService = queryService;
  }

  @PostMapping("/{journalId}/reversals")
  public ApiResponse<JournalEvidenceResponse> reverse(
      @PathVariable UUID journalId,
      @Valid @RequestBody ReverseJournalRequest request) {
    UUID reversalId = journalService.reverse(journalId, request.commandId(), request.reason());
    return ApiResponse.ok(queryService.journal(reversalId));
  }
}
