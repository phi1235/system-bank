package com.banksystem.account.infrastructure.sweep;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AutoSweepBatchRunRepository {
  private final JdbcTemplate jdbcTemplate;

  public AutoSweepBatchRunRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UUID> claim(
      LocalDate date, String workerId, Instant now, Instant leaseUntil) {
    List<UUID> claimed = jdbcTemplate.queryForList("""
        INSERT INTO auto_sweep_batch_runs (
          id, job_type, business_date, status, worker_id, lease_until, started_at
        ) VALUES (?, 'AUTO_SWEEP_EOD', ?, 'RUNNING', ?, ?, ?)
        ON CONFLICT (job_type, business_date) DO UPDATE
        SET status = 'RUNNING', worker_id = EXCLUDED.worker_id,
            lease_until = EXCLUDED.lease_until, started_at = EXCLUDED.started_at,
            completed_at = NULL, last_error = NULL
        WHERE auto_sweep_batch_runs.status = 'FAILED'
           OR (auto_sweep_batch_runs.status = 'RUNNING'
               AND auto_sweep_batch_runs.lease_until < EXCLUDED.started_at)
        RETURNING id
        """, UUID.class, UUID.randomUUID(), date, workerId, leaseUntil, now);
    return claimed.stream().findFirst();
  }

  public boolean finish(
      UUID runId,
      String workerId,
      int processed,
      int failed,
      BigDecimal total,
      String lastError,
      Instant completedAt) {
    return jdbcTemplate.update("""
        UPDATE auto_sweep_batch_runs
        SET status = ?, processed_count = ?, failed_count = ?, total_amount = ?,
            last_error = ?, completed_at = ?, lease_until = ?
        WHERE id = ? AND worker_id = ?
        """, failed == 0 ? "COMPLETED" : "FAILED", processed, failed, total,
        truncate(lastError), completedAt, completedAt, runId, workerId) == 1;
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }
}
