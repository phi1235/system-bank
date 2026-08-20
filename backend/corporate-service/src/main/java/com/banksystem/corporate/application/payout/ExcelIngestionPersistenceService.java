package com.banksystem.corporate.application.payout;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.application.payout.ExcelIngestionService.RowParsedData;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelIngestionPersistenceService {

  private static final int DB_BATCH_SIZE = 500;

  private final PayoutBatchRepository batchRepository;
  private final JdbcTemplate jdbcTemplate;

  public ExcelIngestionPersistenceService(
      PayoutBatchRepository batchRepository,
      JdbcTemplate jdbcTemplate) {
    this.batchRepository = batchRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PayoutBatchEntity persist(
      UUID corporateId,
      UUID batchId,
      List<RowParsedData> rows,
      String sha256,
      String originalKey,
      String errorReportKey) {
    PayoutBatchEntity batch = batchRepository
        .findByCorporateIdAndIdForUpdate(corporateId, batchId)
        .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "Payout batch not found"));
    if (!"DRAFT".equals(batch.getStatus())
        && !"RETURNED".equals(batch.getStatus())
        && !"VALIDATION_FAILED".equals(batch.getStatus())) {
      throw new BusinessException(
          "INVALID_BATCH_STATE",
          "Batch cannot accept file upload in state: " + batch.getStatus());
    }

    jdbcTemplate.update("DELETE FROM payout_items WHERE batch_id = ?", batchId);
    insertRowsInChunks(batchId, rows, batch.getCurrency());

    int validItems = (int) rows.stream().filter(RowParsedData::valid).count();
    int invalidItems = rows.size() - validItems;
    BigDecimal totalAmount = rows.stream()
        .filter(RowParsedData::valid)
        .map(RowParsedData::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    batch.setTotalItems(rows.size());
    batch.setValidItems(validItems);
    batch.setInvalidItems(invalidItems);
    batch.setTotalAmount(totalAmount);
    batch.setFileSha256(sha256);
    batch.setOriginalFileKey(originalKey);
    batch.setErrorReportFileKey(errorReportKey);
    batch.setStatus(invalidItems > 0 ? "VALIDATION_FAILED" : "READY_FOR_SUBMISSION");
    batch.setUpdatedAt(Instant.now());
    return batchRepository.saveAndFlush(batch);
  }

  private void insertRowsInChunks(UUID batchId, List<RowParsedData> rows, String currency) {
    String sql = """
        INSERT INTO payout_items (
          id, batch_id, row_number, employee_code, beneficiary_name,
          account_number, bank_code, amount, fee_amount, currency,
          description, employee_email, payroll_period, status,
          validation_error, idempotency_key, execution_version,
          retry_count, created_at, updated_at, version
        ) VALUES (
          ?, ?, ?, ?, ?,
          ?, ?, ?, 0, ?,
          ?, ?, ?, ?,
          ?, ?, 1,
          0, NOW(), NOW(), 0
        )
        """;
    for (int i = 0; i < rows.size(); i += DB_BATCH_SIZE) {
      int end = Math.min(i + DB_BATCH_SIZE, rows.size());
      List<RowParsedData> chunk = rows.subList(i, end);
      jdbcTemplate.batchUpdate(sql, chunk, chunk.size(), (statement, row) -> {
        UUID itemId = UUID.randomUUID();
        String idempotencyKey = "CORP:" + batchId + ":" + itemId + ":v1";
        statement.setObject(1, itemId);
        statement.setObject(2, batchId);
        statement.setInt(3, row.rowNumber());
        statement.setString(4, row.employeeCode());
        statement.setString(5, row.beneficiaryName());
        statement.setString(6, row.accountNumber());
        statement.setString(7, row.bankCode());
        statement.setBigDecimal(8, row.amount());
        statement.setString(9, currency == null ? "VND" : currency);
        statement.setString(10, row.description());
        statement.setString(11, row.employeeEmail());
        statement.setString(12, row.payrollPeriod());
        statement.setString(13, row.valid() ? "VALID" : "INVALID");
        statement.setString(14, row.errorMessage());
        statement.setString(15, idempotencyKey);
      });
    }
  }
}
