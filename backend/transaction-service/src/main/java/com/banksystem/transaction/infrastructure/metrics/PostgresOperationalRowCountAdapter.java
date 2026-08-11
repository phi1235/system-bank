package com.banksystem.transaction.infrastructure.metrics;

import com.banksystem.transaction.application.metrics.OperationalRowCountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresOperationalRowCountAdapter implements OperationalRowCountPort {

  private static final long EXACT_COUNT_THRESHOLD = 10_000L;

  private final JdbcTemplate jdbcTemplate;

  public PostgresOperationalRowCountAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public long transferOrders() {
    return estimatedOrExact("transfer_orders", "SELECT COUNT(*) FROM transfer_orders");
  }

  @Override
  public long auditLogs() {
    return estimatedOrExact("audit_logs", "SELECT COUNT(*) FROM audit_logs");
  }

  private long estimatedOrExact(String tableName, String exactCountSql) {
    Long estimate = jdbcTemplate.queryForObject(
        "SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?",
        Long.class,
        tableName);
    if (estimate != null && estimate > EXACT_COUNT_THRESHOLD) {
      return estimate;
    }
    Long exact = jdbcTemplate.queryForObject(exactCountSql, Long.class);
    return exact != null ? exact : 0L;
  }
}
