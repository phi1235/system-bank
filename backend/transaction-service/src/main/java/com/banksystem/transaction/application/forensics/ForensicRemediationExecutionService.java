package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ExecuteAdjustmentRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ExecuteHoldRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseDetailResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RecordRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RemediationActionResponse;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountHoldView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.CreateHoldCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicRemediationExecutionService {
  private final ForensicCaseCommandService commandService;
  private final ForensicCaseQueryService queryService;
  private final AccountGateway accountGateway;

  public ForensicRemediationExecutionService(
      ForensicCaseCommandService commandService,
      ForensicCaseQueryService queryService,
      AccountGateway accountGateway) {
    this.commandService = commandService;
    this.queryService = queryService;
    this.accountGateway = accountGateway;
  }

  @Transactional
  public Map<String, Object> executeAdjustment(
      ExecuteAdjustmentRemediationRequest request, String idempotencyKey) {
    if (request.caseId() == null) {
      throw new BusinessException("CASE_ID_REQUIRED", "caseId is required to execute remediation adjustment", HttpStatus.BAD_REQUEST);
    }

    ForensicCaseDetailResponse detail = queryService.get(request.caseId());
    int cycle = detail.forensicCase().investigationCycle() > 0 ? detail.forensicCase().investigationCycle() : 1;

    String rawKey = (idempotencyKey != null && !idempotencyKey.isBlank())
        ? idempotencyKey.trim().toUpperCase()
        : UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    
    String referenceId = rawKey.endsWith("-C" + cycle)
        ? (rawKey.startsWith("ADJ-") ? rawKey : "ADJ-" + rawKey)
        : (rawKey.startsWith("ADJ-") ? rawKey + "-C" + cycle : "ADJ-" + rawKey + "-C" + cycle);

    // Idempotency check: If referenceId exists in case remediationActions, return existing response cleanly
    if (detail.forensicCase().remediationActions() != null) {
      for (RemediationActionResponse action : detail.forensicCase().remediationActions()) {
        if (referenceId.equals(action.referenceId())) {
          return Map.of(
              "adjustmentJournalId", referenceId,
              "status", "POSTED",
              "amount", request.amount(),
              "transactionId", request.transactionId() != null ? request.transactionId().toString() : "",
              "referenceId", referenceId,
              "idempotentReplay", true,
              "message", "Đã phát hành Bút toán Điều chỉnh (kết quả Idempotent replay)!");
        }
      }
    }

    // Execute REAL double-entry posting on account-service via AccountGateway
    UUID targetAccountId = request.targetAccountId() != null
        ? request.targetAccountId()
        : (detail.forensicCase().accountId() != null && !detail.forensicCase().accountId().isBlank()
            ? UUID.fromString(detail.forensicCase().accountId())
            : null);

    if (targetAccountId == null) {
      throw new BusinessException("TARGET_ACCOUNT_MISSING", "Cannot execute adjustment: Target Account ID is missing from request and case");
    }

    if (accountGateway == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountGateway is not configured");
    }

    MoneyCommand moneyCmd = new MoneyCommand(
        request.amount(), referenceId, "Remediation Adjustment (Cycle " + cycle + "): " + request.reason(), referenceId);
    MoneyResult moneyRes = accountGateway.credit(targetAccountId, moneyCmd);
    if (moneyRes == null || moneyRes.ledgerEntryId() == null) {
      throw new BusinessException("REMEDIATION_POSTING_FAILED", "Posting on account-service failed or returned null");
    }
    String ledgerPostingId = moneyRes.ledgerEntryId();

    long expectedVersion = detail.forensicCase().version();
    String description = "Phát hành Bút toán Điều chỉnh " + request.amount() + " VND [Cycle " + cycle + "] (Posting: "
        + ledgerPostingId + ") - Lý do: " + request.reason();
    commandService.recordRemediation(
        request.caseId(),
        new RecordRemediationRequest(expectedVersion, "ADJUSTMENT_JOURNAL", referenceId, description, true));

    return Map.of(
        "adjustmentJournalId", referenceId,
        "ledgerPostingId", ledgerPostingId,
        "status", "POSTED",
        "amount", request.amount(),
        "transactionId", request.transactionId() != null ? request.transactionId().toString() : "",
        "referenceId", referenceId,
        "investigationCycle", cycle,
        "idempotentReplay", false,
        "message", "Đã phát hành Bút toán Điều chỉnh thật sang account-service thành công!");
  }

  @Transactional
  public Map<String, Object> executeHold(
      ExecuteHoldRemediationRequest request, String idempotencyKey) {
    if (request.caseId() == null) {
      throw new BusinessException("CASE_ID_REQUIRED", "caseId is required to execute remediation hold", HttpStatus.BAD_REQUEST);
    }

    ForensicCaseDetailResponse detail = queryService.get(request.caseId());
    int cycle = detail.forensicCase().investigationCycle() > 0 ? detail.forensicCase().investigationCycle() : 1;

    String rawKey = (idempotencyKey != null && !idempotencyKey.isBlank())
        ? idempotencyKey.trim().toUpperCase()
        : UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    String referenceId = rawKey.endsWith("-C" + cycle)
        ? (rawKey.startsWith("HOLD-") ? rawKey : "HOLD-" + rawKey)
        : (rawKey.startsWith("HOLD-") ? rawKey + "-C" + cycle : "HOLD-" + rawKey + "-C" + cycle);

    // Idempotency check
    if (detail.forensicCase().remediationActions() != null) {
      for (RemediationActionResponse action : detail.forensicCase().remediationActions()) {
        if (referenceId.equals(action.referenceId())) {
          return Map.of(
              "holdId", referenceId,
              "status", "FROZEN",
              "targetAccountId", request.targetAccountId() != null ? request.targetAccountId().toString() : "",
              "amount", request.amount(),
              "referenceId", referenceId,
              "idempotentReplay", true,
              "message", "Đã gửi lệnh Phong tỏa Tạm thời (kết quả Idempotent replay)!");
        }
      }
    }

    UUID targetAccountId = request.targetAccountId() != null
        ? request.targetAccountId()
        : (detail.forensicCase().accountId() != null && !detail.forensicCase().accountId().isBlank()
            ? UUID.fromString(detail.forensicCase().accountId())
            : null);

    if (targetAccountId == null) {
      throw new BusinessException("TARGET_ACCOUNT_MISSING", "Cannot execute hold: Target Account ID is missing from request and case");
    }

    if (accountGateway == null) {
      throw new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE", "AccountGateway is not configured");
    }

    CreateHoldCommand holdCmd = new CreateHoldCommand(
        request.caseId(), referenceId, request.amount(), "VND", java.time.Instant.now().plusSeconds(86400 * 7));
    AccountHoldView holdView = accountGateway.createHold(targetAccountId, holdCmd);
    if (holdView == null || holdView.id() == null) {
      throw new BusinessException("REMEDIATION_HOLD_FAILED", "Account hold on account-service failed or returned null");
    }
    String accountHoldId = holdView.id();

    long expectedVersion = detail.forensicCase().version();
    String targetAcc = targetAccountId.toString();
    String description = "Phong tỏa Tạm thời " + targetAcc + " (" + request.amount() + " VND [Cycle " + cycle + "], Hold ID: "
        + accountHoldId + ") - Lý do: " + request.reason();
    commandService.recordRemediation(
        request.caseId(),
        new RecordRemediationRequest(expectedVersion, "ACCOUNT_HOLD", referenceId, description, true));

    return Map.of(
        "holdId", referenceId,
        "accountHoldId", accountHoldId,
        "status", "FROZEN",
        "targetAccountId", request.targetAccountId() != null ? request.targetAccountId().toString() : "",
        "amount", request.amount(),
        "referenceId", referenceId,
        "investigationCycle", cycle,
        "idempotentReplay", false,
        "message", "Đã gửi lệnh Phong tỏa Tạm thời thật sang account-service thành công!");
  }
}
