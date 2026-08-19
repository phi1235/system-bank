package com.banksystem.transaction.domain.transfer;

import com.banksystem.transaction.domain.transfer.SandboxTopupQuotaEntity.QuotaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SandboxTopupQuotaRepository extends JpaRepository<SandboxTopupQuotaEntity, QuotaId> {

  @Query(value = """
      INSERT INTO sandbox_topup_quotas (user_id, topup_date, accumulated_amount, updated_at)
      VALUES (:userId, :topupDate, :amount, NOW())
      ON CONFLICT (user_id, topup_date)
      DO UPDATE SET accumulated_amount = sandbox_topup_quotas.accumulated_amount + EXCLUDED.accumulated_amount,
                    updated_at = NOW()
      WHERE sandbox_topup_quotas.accumulated_amount + EXCLUDED.accumulated_amount <= :maxDailyQuota
      RETURNING accumulated_amount
      """, nativeQuery = true)
  Optional<BigDecimal> atomicAccumulateQuota(
      @Param("userId") UUID userId,
      @Param("topupDate") LocalDate topupDate,
      @Param("amount") BigDecimal amount,
      @Param("maxDailyQuota") BigDecimal maxDailyQuota);
}
