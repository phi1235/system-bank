package com.banksystem.account.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
  Optional<LedgerEntryEntity> findByAccountIdAndReferenceIdAndEntryType(
      UUID accountId, String referenceId, String entryType);

  @Query("""
      SELECT e FROM LedgerEntryEntity e
      WHERE e.accountId = :accountId
        AND (:entryType IS NULL OR e.entryType = :entryType)
        AND (:fromTs IS NULL OR e.createdAt >= :fromTs)
        AND (:toTs IS NULL OR e.createdAt <= :toTs)
      """)
  Page<LedgerEntryEntity> search(
      @Param("accountId") UUID accountId,
      @Param("entryType") String entryType,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);
}
