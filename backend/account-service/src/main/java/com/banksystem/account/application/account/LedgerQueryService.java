package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.account.AccountDtos.InternalLedgerEntryResponse;
import com.banksystem.account.domain.entity.account.LedgerEntryEntity;
import com.banksystem.account.domain.repository.account.LedgerEntryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only ledger lookups for internal callers (reconciliation in transaction-service). */
@Service
public class LedgerQueryService {

  private final LedgerEntryRepository repository;

  public LedgerQueryService(LedgerEntryRepository repository) {
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
