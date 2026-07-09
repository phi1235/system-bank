package com.banksystem.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

  List<AccountEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserId(UUID userId);

  Optional<AccountEntity> findByAccountNumber(String accountNumber);

  boolean existsByAccountNumber(String accountNumber);

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
