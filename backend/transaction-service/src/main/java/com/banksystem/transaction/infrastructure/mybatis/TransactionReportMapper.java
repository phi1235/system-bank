package com.banksystem.transaction.infrastructure.mybatis;

import com.banksystem.transaction.api.dto.ReportDtos.DailyVolumePoint;
import com.banksystem.transaction.api.dto.ReportDtos.ReportSummaryRow;
import com.banksystem.transaction.api.dto.ReportDtos.StatusBreakdownRow;
import com.banksystem.transaction.api.dto.ReportDtos.TopAccountRow;
import com.banksystem.transaction.api.dto.ReportDtos.ExportReportRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

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
      @Param("fromAccountId") UUID fromAccountId);

  List<DailyVolumePoint> dailyVolume(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("zone") String zone,
      @Param("fromAccountId") UUID fromAccountId);

  List<StatusBreakdownRow> statusBreakdown(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("fromAccountId") UUID fromAccountId);

  List<TopAccountRow> topSourceAccounts(
      @Param("fromTs") Instant fromTs, @Param("toTs") Instant toTs, @Param("limit") int limit);

  Cursor<ExportReportRow> streamExport(
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      @Param("fromAccountId") UUID fromAccountId);
}
