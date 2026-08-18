package com.banksystem.account.application.ledger;

import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Restartable expand-phase worker that maps legacy entries or records an exception. */
@Component
@ConditionalOnProperty(prefix = "bank.ledger.backfill", name = "enabled", havingValue = "true")
public class LegacyLedgerBackfillWorker {
  private static final String JOB = "LEGACY_LEDGER_TO_JOURNAL";
  private final JdbcTemplate jdbcTemplate;
  private final DoubleEntryJournalService journalService;
  private final TransactionTemplate requiresNew;
  private final int batchSize;

  public LegacyLedgerBackfillWorker(
      JdbcTemplate jdbcTemplate,
      DoubleEntryJournalService journalService,
      PlatformTransactionManager transactionManager,
      @Value("${bank.ledger.backfill.batch-size}") int batchSize) {
    this.jdbcTemplate = jdbcTemplate;
    this.journalService = journalService;
    this.batchSize = batchSize;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Scheduled(cron = "${bank.ledger.backfill.cron}")
  public void runBatch() {
    Checkpoint checkpoint = checkpoint();
    markRunning();
    List<LegacyRow> rows = load(checkpoint);
    if (rows.isEmpty()) {
      markCompletedIfConsistent();
      return;
    }
    for (LegacyRow row : rows) {
      try {
        requiresNew.executeWithoutResult(status -> migrate(row));
      } catch (RuntimeException exception) {
        requiresNew.executeWithoutResult(status -> recordException(row, exception));
      }
    }
  }

  private void migrate(LegacyRow row) {
    journalService.recordLegacyEntry(row.entry(), row.currency());
    advance(row, true);
  }

  private void recordException(LegacyRow row, RuntimeException exception) {
    String detail = exception.getMessage() == null
        ? exception.getClass().getSimpleName() : exception.getMessage();
    jdbcTemplate.update("""
        INSERT INTO ledger_backfill_exceptions
          (id, job_name, ledger_entry_id, error_code, error_detail)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (job_name, ledger_entry_id) DO NOTHING
        """, UUID.randomUUID(), JOB, row.entry().getId(),
        exception.getClass().getSimpleName(), detail.substring(0, Math.min(detail.length(), 500)));
    advance(row, false);
  }

  private void advance(LegacyRow row, boolean processed) {
    jdbcTemplate.update("""
        UPDATE ledger_backfill_checkpoints
        SET last_ledger_entry_id = ?,
            last_ledger_entry_created_at = ?,
            processed_count = processed_count + ?,
            exception_count = exception_count + ?,
            updated_at = NOW()
        WHERE job_name = ?
        """, row.entry().getId(), row.entry().getCreatedAt(), processed ? 1 : 0,
        processed ? 0 : 1, JOB);
  }

  private List<LegacyRow> load(Checkpoint checkpoint) {
    Instant cursorTime = checkpoint.createdAt() == null ? Instant.EPOCH : checkpoint.createdAt();
    UUID cursorId = checkpoint.entryId() == null ? new UUID(0L, 0L) : checkpoint.entryId();
    return jdbcTemplate.query("""
        SELECT e.id, e.account_id, e.entry_type, e.amount, e.reference_id,
               e.description, e.created_at, a.currency
        FROM ledger_entries e
        JOIN accounts a ON a.id = e.account_id
        WHERE (e.created_at > ? OR (e.created_at = ? AND e.id > ?))
        ORDER BY e.created_at, e.id
        LIMIT ?
        """, this::mapRow, cursorTime, cursorTime, cursorId, batchSize);
  }

  private LegacyRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    LedgerEntryEntity entry = new LedgerEntryEntity();
    entry.setId(resultSet.getObject("id", UUID.class));
    entry.setAccountId(resultSet.getObject("account_id", UUID.class));
    entry.setEntryType(resultSet.getString("entry_type"));
    entry.setAmount(resultSet.getBigDecimal("amount"));
    entry.setReferenceId(resultSet.getString("reference_id"));
    entry.setDescription(resultSet.getString("description"));
    entry.setCreatedAt(resultSet.getTimestamp("created_at").toInstant());
    return new LegacyRow(entry, resultSet.getString("currency"));
  }

  private Checkpoint checkpoint() {
    return jdbcTemplate.queryForObject("""
        SELECT last_ledger_entry_id, last_ledger_entry_created_at
        FROM ledger_backfill_checkpoints WHERE job_name = ?
        """, (resultSet, rowNumber) -> new Checkpoint(
        resultSet.getObject(1, UUID.class),
        resultSet.getTimestamp(2) == null ? null : resultSet.getTimestamp(2).toInstant()), JOB);
  }

  private void markRunning() {
    jdbcTemplate.update("""
        UPDATE ledger_backfill_checkpoints
        SET status = 'RUNNING', started_at = COALESCE(started_at, NOW()), updated_at = NOW()
        WHERE job_name = ? AND status <> 'COMPLETED'
        """, JOB);
  }

  private void markCompletedIfConsistent() {
    Long remaining = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM ledger_entries e
        LEFT JOIN ledger_journals j ON j.business_command_id = 'LEGACY_ENTRY:' || e.id
        LEFT JOIN ledger_backfill_exceptions x
          ON x.job_name = ? AND x.ledger_entry_id = e.id
        WHERE j.id IS NULL AND x.id IS NULL
        """, Long.class, JOB);
    if (remaining != null && remaining == 0) {
      jdbcTemplate.update("""
          UPDATE ledger_backfill_checkpoints
          SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW()
          WHERE job_name = ?
          """, JOB);
    }
  }

  private record Checkpoint(UUID entryId, Instant createdAt) {}
  private record LegacyRow(LedgerEntryEntity entry, String currency) {}
}
