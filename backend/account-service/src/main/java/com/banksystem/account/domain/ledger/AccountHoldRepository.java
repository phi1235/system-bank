package com.banksystem.account.domain.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountHoldRepository extends JpaRepository<AccountHoldEntity, UUID> {
  Optional<AccountHoldEntity> findByAccountIdAndCommandId(UUID accountId, String commandId);
  List<AccountHoldEntity> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

  @Query("""
      SELECT COALESCE(SUM(h.amount), 0) FROM AccountHoldEntity h
      WHERE h.accountId = :accountId
        AND h.status = 'ACTIVE'
        AND h.createdAt <= :at
        AND h.expiresAt > :at
      """)
  BigDecimal activeAmountAt(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
