package com.banksystem.account.api.ledger;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.AccountDtos.InternalLedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerSearchRequest;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch ledger lookup for cross-service reconciliation. HTTP + internal key only; rules in
 * {@link LedgerQueryService}. Internal network only.
 */
@RestController
@RequestMapping("/internal/ledger")
public class InternalLedgerController {

  private final LedgerQueryService service;
  private final String apiKey;

  public InternalLedgerController(
      LedgerQueryService service, @Value("${bank.internal.api-key}") String apiKey) {
    this.service = service;
    this.apiKey = apiKey;
  }

  @PostMapping("/findLedgerByCondition")
  public ApiResponse<List<InternalLedgerEntryResponse>> findLedgerByCondition(
      @Valid @RequestBody LedgerSearchRequest request,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(service.searchByReferenceIds(request.referenceIds()));
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
