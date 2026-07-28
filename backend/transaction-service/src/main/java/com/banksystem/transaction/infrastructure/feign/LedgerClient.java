package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerSearchRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Reconciliation-only ledger lookup; separate contextId to coexist with {@link AccountClient}. */
@FeignClient(name = "ACCOUNT-SERVICE", contextId = "ledgerClient", url = "${ACCOUNT_SERVICE_URL:}")
public interface LedgerClient {

  @PostMapping("/internal/ledger/search")
  ApiResponse<List<LedgerEntryView>> search(
      @RequestBody LedgerSearchRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
