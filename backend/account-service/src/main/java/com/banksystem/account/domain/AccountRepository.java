package com.banksystem.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

  List<AccountEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  Optional<AccountEntity> findByAccountNumber(String accountNumber);

  boolean existsByAccountNumber(String accountNumber);

  @Query("""
      SELECT a FROM AccountEntity a
      WHERE (:status IS NULL OR :status = '' OR a.status = :status)
        AND (
          :q IS NULL OR :q = ''
          OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
          OR (:userId IS NOT NULL AND a.userId = :userId)
          OR (:accountId IS NOT NULL AND a.id = :accountId)
        )
      """)
  Page<AccountEntity> adminSearch(
      @Param("q") String q,
      @Param("status") String status,
      @Param("userId") UUID userId,
      @Param("accountId") UUID accountId,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE accounts
      SET balance = balance - :amount, updated_at = NOW()
      WHERE id = :id AND status = 'ACTIVE' AND balance >= :amount
      """, nativeQuery = true)
  int debitIfSufficient(@Param("id") UUID id, @Param("amount") java.math.BigDecimal amount);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE accounts
      SET balance = balance + :amount, updated_at = NOW()
      WHERE id = :id AND status = 'ACTIVE'
      """, nativeQuery = true)
  int creditIfActive(@Param("id") UUID id, @Param("amount") java.math.BigDecimal amount);
}
