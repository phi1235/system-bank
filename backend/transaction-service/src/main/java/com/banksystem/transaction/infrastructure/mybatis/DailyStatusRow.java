package com.banksystem.transaction.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single row from the combined daily×status aggregate query.
 * The service derives summary, dailyVolume and statusBreakdown from these rows
 * in Java, avoiding 3 separate full-table scans on 1M+ rows.
 */
public record DailyStatusRow(
    LocalDate day,
    String status,
    long cnt,
    BigDecimal totalAmount,
    BigDecimal totalFee
) {}
