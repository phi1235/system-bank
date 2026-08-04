package com.banksystem.account.application.ledger.impl;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only ledger lookups for internal callers (reconciliation in transaction-service). */
@Service
public class LedgerQueryServiceImpl implements LedgerQueryService {

  private final LedgerEntryRepository repository;

  public LedgerQueryServiceImpl(LedgerEntryRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<InternalLedgerEntryResponse> searchByReferenceIds(List<String> referenceIds) {
    return repository.findByReferenceIdIn(referenceIds).stream().map(this::toResponse).toList();
  }

  private InternalLedgerEntryResponse toResponse(LedgerEntryEntity e) {
    return new InternalLedgerEntryResponse(
        e.getId().toString(),
        e.getAccountId().toString(),
        e.getEntryType(),
        e.getAmount(),
        e.getReferenceId(),
        e.getCreatedAt());
  }
}
