package com.banksystem.account.api.ledger;

import com.banksystem.account.api.dto.AccountDtos.InternalLedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerSearchRequest;
import com.banksystem.account.application.ledger.LedgerQueryService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Batch ledger lookup used by cross-service reconciliation. */
@RestController
@RequestMapping("/internal/ledger")
@RequireInternalApiKey
public class InternalLedgerController {

  private final LedgerQueryService queryService;

  public InternalLedgerController(LedgerQueryService queryService) {
    this.queryService = queryService;
  }

  @PostMapping("/findLedgerByCondition")
  public ApiResponse<List<InternalLedgerEntryResponse>> findLedgerByCondition(
      @Valid @RequestBody LedgerSearchRequest request) {
    return ApiResponse.ok(queryService.searchByReferenceIds(request.referenceIds()));
  }
}
