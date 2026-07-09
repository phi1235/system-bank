package com.banksystem.account.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
  Optional<LedgerEntryEntity> findByAccountIdAndReferenceIdAndEntryType(
      UUID accountId, String referenceId, String entryType);
}
