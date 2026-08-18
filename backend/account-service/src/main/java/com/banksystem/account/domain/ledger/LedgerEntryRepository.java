package com.banksystem.account.domain.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

  List<LedgerEntryEntity> findByReferenceIdIn(Collection<String> referenceIds);

  /**
   * Statement search. Callers must pass non-null time bounds (EPOCH/FAR_FUTURE sentinels) and a
   * boolean flag for the optional type — Postgres cannot infer the type of an untyped NULL bind
   * in {@code (:param IS NULL OR ...)} ({@code could not determine data type of parameter}).
   */
  @Query("""
      SELECT e FROM LedgerEntryEntity e
      WHERE e.accountId = :accountId
        AND (:hasType = false OR e.entryType = :entryType)
        AND e.createdAt >= :fromTs
        AND e.createdAt <= :toTs
      """)
  Page<LedgerEntryEntity> search(
      @Param("accountId") UUID accountId,
      @Param("hasType") boolean hasType,
      @Param("entryType") String entryType,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  @Query("""
      SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
      FROM LedgerEntryEntity e
      WHERE e.accountId = :accountId AND e.createdAt <= :at
      """)
  BigDecimal balanceAt(@Param("accountId") UUID accountId, @Param("at") Instant at);

  @Query("""
      SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
      FROM LedgerEntryEntity e
      WHERE e.accountId = :accountId AND e.createdAt > :after AND e.createdAt <= :at
      """)
  BigDecimal netMovement(
      @Param("accountId") UUID accountId,
      @Param("after") Instant after,
      @Param("at") Instant at);

  @Query("SELECT MAX(e.createdAt) FROM LedgerEntryEntity e WHERE e.accountId = :accountId AND e.createdAt <= :at")
  Instant latestEntryAt(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
