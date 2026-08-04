package com.banksystem.account.domain.deposit;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermDepositRepository extends JpaRepository<TermDepositEntity, UUID> {

  List<TermDepositEntity> findByUserIdOrderByOpenedAtDesc(UUID userId);

  List<TermDepositEntity> findByStatusAndMaturityDateLessThanEqual(
      TermDepositStatus status, LocalDate date);

  /**
   * Admin drill-down. Optional filters via boolean flags + non-null sentinels (Postgres cannot
   * type an untyped NULL bind — see the statement-search 42P18 regression).
   */
  @Query("""
      SELECT d FROM TermDepositEntity d
      WHERE (:hasStatus = false OR d.status = :status)
        AND (:hasProduct = false OR d.productCode = :productCode)
        AND (:hasUser = false OR d.userId = :userId)
        AND (:hasAccount = false OR d.sourceAccountId = :accountId)
        AND d.maturityDate >= :maturityFrom
        AND d.maturityDate <= :maturityTo
      ORDER BY d.openedAt DESC
      """)
  Page<TermDepositEntity> searchAdmin(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") TermDepositStatus status,
      @Param("hasProduct") boolean hasProduct,
      @Param("productCode") String productCode,
      @Param("hasUser") boolean hasUser,
      @Param("userId") UUID userId,
      @Param("hasAccount") boolean hasAccount,
      @Param("accountId") UUID accountId,
      @Param("maturityFrom") LocalDate maturityFrom,
      @Param("maturityTo") LocalDate maturityTo,
      Pageable pageable);

  /**
   * Set-based daily accrual: interest earned so far at the contract rate over elapsed
   * banking days (ACT/365, 2 decimals). One UPDATE for the whole book — no per-row loop.
   * 3650000 = 10000 (bps) * 365.
   */
  @Modifying
  @Query(
      value =
          """
          UPDATE term_deposits
          SET accrued_interest = ROUND(
                amount * rate_bps
                  * ((now() AT TIME ZONE :zone)::date - (opened_at AT TIME ZONE :zone)::date)
                  / 3650000.0, 2),
              updated_at = now()
          WHERE status = 'OPEN'
          """,
      nativeQuery = true)
  int accrueDailyInterest(@Param("zone") String zone);
}
