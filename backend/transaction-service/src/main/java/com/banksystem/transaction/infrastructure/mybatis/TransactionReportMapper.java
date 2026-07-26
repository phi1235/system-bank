package com.banksystem.transaction.infrastructure.mybatis;

import com.banksystem.transaction.api.dto.ReportDtos.DailyVolumePoint;
import com.banksystem.transaction.api.dto.ReportDtos.ReportSummaryRow;
import com.banksystem.transaction.api.dto.ReportDtos.StatusBreakdownRow;
import com.banksystem.transaction.api.dto.ReportDtos.TopAccountRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Read-only reporting queries over transfer_orders (hand-written SQL in
 * resources/mybatis/TransactionReportMapper.xml). Writes stay on JPA; this mapper exists for
 * aggregate/reporting SQL that JPQL expresses poorly (FILTER, AT TIME ZONE, LIMIT ranking).
 *
 * <p>Range convention: {@code fromTs} inclusive, {@code toTs} exclusive.
 */
@Mapper
public interface TransactionReportMapper {

  ReportSummaryRow summary(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("fromAccountId") String fromAccountId);

  List<DailyVolumePoint> dailyVolume(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("zone") String zone,
      @Param("fromAccountId") String fromAccountId);

  List<StatusBreakdownRow> statusBreakdown(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("fromAccountId") String fromAccountId);

  List<TopAccountRow> topSourceAccounts(
      @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs, @Param("limit") int limit);
}
