package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.outbox.OutboxStatus;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/transactions")
public class InternalTransactionController {

  private final TransferOrderRepository transferRepository;
  private final AuditLogRepository auditRepository;
  private final OutboxEventRepository outboxRepository;
  private final JdbcTemplate jdbcTemplate;
  private final String apiKey;

  public InternalTransactionController(
      TransferOrderRepository transferRepository,
      AuditLogRepository auditRepository,
      OutboxEventRepository outboxRepository,
      JdbcTemplate jdbcTemplate,
      @Value("${bank.internal.api-key:internal-dev-key}") String apiKey) {
    this.transferRepository = transferRepository;
    this.auditRepository = auditRepository;
    this.outboxRepository = outboxRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.apiKey = apiKey;
  }

  public record InternalTransactionCountsResponse(
      long transfers,
      long transfersFailed,
      long transfersCompensated,
      long audits,
      long outboxDead,
      long outboxPending,
      long outboxPublished
  ) {}

  @GetMapping("/counts")
  public ApiResponse<InternalTransactionCountsResponse> counts(
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    // Use estimated row count for large tables to avoid slow full-table COUNT(*)
    long transfers = estimatedRowCount("transfer_orders");
    long failed = transferRepository.countByStatus(TransferStatus.FAILED);
    long compensated = transferRepository.countByStatus(TransferStatus.COMPENSATED);
    long audits = estimatedRowCount("audit_logs");
    long outboxDead = outboxRepository.countByStatus(OutboxStatus.DEAD.name());
    long outboxPending = outboxRepository.countByStatus(OutboxStatus.PENDING.name());
    long outboxPublished = estimatedRowCount("outbox_events");
    return ApiResponse.ok(new InternalTransactionCountsResponse(
        transfers, failed, compensated, audits, outboxDead, outboxPending, outboxPublished));
  }

  /**
   * PostgreSQL estimated row count from pg_stat_user_tables (O(1), updated by ANALYZE).
   * Falls back to 0 if table not found or never analyzed.
   */
  private long estimatedRowCount(String tableName) {
    Long estimate = jdbcTemplate.queryForObject(
        "SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?",
        Long.class, tableName);
    if (estimate == null || estimate <= 10000) {
      Long exact = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM " + tableName, Long.class);
      return exact != null ? exact : 0;
    }
    return estimate;
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
