package com.banksystem.account.domain.account;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

  List<AccountEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  long countByStatus(String status);

  Optional<AccountEntity> findByAccountNumber(String accountNumber);

  boolean existsByAccountNumber(String accountNumber);

  /**
   * Staff search with boolean flags so Postgres never sees untyped NULL binds
   * for optional status/type/UUID filters.
   */
  @Query("""
      SELECT a FROM AccountEntity a
      WHERE (:hasStatus = false OR a.status = :status)
        AND (:hasType = false OR a.accountType = :accountType)
        AND (
          :hasQ = false
          OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR (:hasUserId = true AND a.userId = :userId)
          OR (:hasAccountId = true AND a.id = :accountId)
        )
      ORDER BY a.updatedAt DESC
      """)
  Page<AccountEntity> adminSearch(
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasType") boolean hasType,
      @Param("accountType") String accountType,
      @Param("hasUserId") boolean hasUserId,
      @Param("userId") UUID userId,
      @Param("hasAccountId") boolean hasAccountId,
      @Param("accountId") UUID accountId,
      Pageable pageable);

  @Query("""
      SELECT a FROM AccountEntity a
      WHERE (:hasStatus = false OR a.status = :status)
        AND (:hasType = false OR a.accountType = :accountType)
        AND (
          :hasQ = false
          OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR (:hasUserId = true AND a.userId = :userId)
          OR (:hasAccountId = true AND a.id = :accountId)
        )
      ORDER BY a.updatedAt DESC
      """)
  Slice<AccountEntity> adminSearchSlice(
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasType") boolean hasType,
      @Param("accountType") String accountType,
      @Param("hasUserId") boolean hasUserId,
      @Param("userId") UUID userId,
      @Param("hasAccountId") boolean hasAccountId,
      @Param("accountId") UUID accountId,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE accounts
      SET balance = balance - :amount, updated_at = NOW()
      WHERE id = :id AND status = 'ACTIVE' AND balance >= :amount
      """, nativeQuery = true)
  int debitIfSufficient(@Param("id") UUID id, @Param("amount") BigDecimal amount);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE accounts
      SET balance = balance + :amount, updated_at = NOW()
      WHERE id = :id AND status = 'ACTIVE'
      """, nativeQuery = true)
  int creditIfActive(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
