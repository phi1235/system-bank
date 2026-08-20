package com.banksystem.account.domain.ledger;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountHoldRepository extends JpaRepository<AccountHoldEntity, UUID> {
  Optional<AccountHoldEntity> findByAccountIdAndCommandId(UUID accountId, String commandId);
  List<AccountHoldEntity> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM AccountHoldEntity h WHERE h.id = :id")
  Optional<AccountHoldEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      SELECT COALESCE(SUM(h.amount), 0) FROM AccountHoldEntity h
      WHERE h.accountId = :accountId
        AND h.status = 'ACTIVE'
        AND h.createdAt <= :at
        AND h.expiresAt > :at
      """)
  BigDecimal activeAmountAt(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
