package com.banksystem.transaction.application.transfer.impl;

import com.banksystem.transaction.application.transfer.TransferService;
import com.banksystem.transaction.application.transfer.TransferQueryService;
import com.banksystem.transaction.application.transfer.TransferFeeGlService;
import com.banksystem.transaction.application.transfer.policy.TransferLimitPolicy;
import com.banksystem.transaction.application.transfer.policy.TransferFeePolicy;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.transfer.SagaStepLogEntity;
import com.banksystem.transaction.domain.transfer.SagaStepLogRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasPaymentResponse;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.ProviderOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import com.banksystem.transaction.domain.transfer.ManualReviewAuditLogEntity;
import com.banksystem.transaction.domain.transfer.ManualReviewAuditLogRepository;

@Service
public class TransferSagaOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

  private final TransferOrderRepository transferOrderRepository;
  private final SagaStepLogRepository sagaStepLogRepository;
  private final AccountGateway accountGateway;
  private final OutboxService outboxService;
  private final TransferFeeGlService feeGlService;
  private final NapasSwitchClient napasSwitchClient;
  private final ManualReviewAuditLogRepository manualReviewAuditLogRepository;
  private final TransactionTemplate transactionTemplate;

  public TransferSagaOrchestrator(
      TransferOrderRepository transferOrderRepository,
      SagaStepLogRepository sagaStepLogRepository,
      AccountGateway accountGateway,
      OutboxService outboxService,
      TransferFeeGlService feeGlService,
      NapasSwitchClient napasSwitchClient,
      ManualReviewAuditLogRepository manualReviewAuditLogRepository,
      TransactionTemplate transactionTemplate) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaStepLogRepository = sagaStepLogRepository;
    this.accountGateway = accountGateway;
    this.outboxService = outboxService;
    this.feeGlService = feeGlService;
    this.napasSwitchClient = napasSwitchClient;
    this.manualReviewAuditLogRepository = manualReviewAuditLogRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public TransferOrderEntity run(TransferOrderEntity order) {
    order = reload(order.getId());
    MoneyResult debit;
    try {
      debit = callDebitWithRetry(order.getFromAccountId(), order);
    } catch (Exception ex) {
      if (isAuthoritativeDebitFailure(ex)) {
        log.warn("Debit source rejected with authoritative business error for transfer {}: {}", order.getId(), ex.getMessage());
        order = markFailed(order, formatReason(ex));
        step(order.getId(), "DEBIT_SOURCE", "FAILED", ex.getMessage());
        return order;
      }
      // Transient / Timeout on debit: Do NOT mark FAILED (account may have already been debited)
      log.error("Debit source outcome unknown after retries for transfer {}. Escalating to UNKNOWN.", order.getId());
      order = markUnknown(order, formatReason("DEBIT_OUTCOME_UNKNOWN", ex.getMessage()), null);
      order.setReconciliationStatus("RETRY_DEBIT");
      order = persist(order);
      step(order.getId(), "DEBIT_SOURCE", "UNKNOWN", "Debit outcome ambiguous after timeout/network error");
      return order;
    }

    // Debit succeeded. Persistence/logging failures must never relabel it as a failed debit.
    order.setDebitEntryRef(debit.ledgerEntryId());
    order.setStatus(TransferStatus.DEBITED);
    order.setUpdatedAt(Instant.now());
    order = persist(order);
    step(order.getId(), "DEBIT_SOURCE", "SUCCESS", "ledger=" + debit.ledgerEntryId());

    if ("INTERBANK".equalsIgnoreCase(order.getTransferType())) {
      return runExternalAfterDebit(order);
    }

    return runInternalAfterDebit(order);
  }

  private TransferOrderEntity runInternalAfterDebit(TransferOrderEntity order) {
    MoneyResult credit = null;
    Exception lastException = null;

    // STEP 2 — Credit destination with deterministic idempotency key TX-CREDIT-{orderId}
    try {
      credit = callCreditWithRetry(order.getToAccountId(), order, "TX-CREDIT-" + order.getId());
    } catch (Exception ex) {
      lastException = ex;
    }

    // If credit failed after retries:
    if (credit == null) {
      if (isAuthoritativeCreditFailure(lastException)) {
        log.warn("Internal credit rejected with authoritative business error for transfer {}: {}",
            order.getId(), lastException.getMessage());
        step(order.getId(), "CREDIT_DEST", "FAILED", lastException.getMessage());
        return compensateSourceOnly(order, formatReason(lastException));
      }
      // Transient / Timeout: NEVER prematurely refund source! Escalate to UNKNOWN / REVIEW_REQUIRED.
      log.error("Internal credit outcome unknown after retries for transfer {}. Escalating to UNKNOWN without premature refund.", order.getId());
      order = markUnknown(order, formatReason("INTERNAL_CREDIT_UNKNOWN", lastException != null ? lastException.getMessage() : "No response"), null);
      step(order.getId(), "CREDIT_DEST", "UNKNOWN", "Credit outcome ambiguous after timeout/network error");
      return order;
    }

    return completeInternal(order, credit.ledgerEntryId());
  }

  private TransferOrderEntity completeInternal(TransferOrderEntity order, String creditLedgerId) {
    order.setCreditEntryRef(creditLedgerId);
    order.setUpdatedAt(Instant.now());
    order = persist(order);
    step(order.getId(), "CREDIT_DEST", "SUCCESS", "ledger=" + creditLedgerId);

    // STEP 3 — fee GL: credit bank income account (skip when fee = 0)
    if (feeGlService.requiresPosting(order) && order.getFeeEntryRef() == null) {
      String feeLedgerId = postFeeWithRetry(order);
      order.setFeeEntryRef(feeLedgerId);
      order.setUpdatedAt(Instant.now());
      order = persist(order);
    }

    order.setStatus(TransferStatus.COMPLETED);
    order.setFailureReason(null);
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "COMPLETED");
  }

  private String postFeeWithRetry(TransferOrderEntity order) {
    Exception lastEx = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        String feeLedgerId = feeGlService.postFee(order);
        step(order.getId(), "CREDIT_FEE_INCOME", "SUCCESS", "ledger=" + feeLedgerId);
        return feeLedgerId;
      } catch (Exception ex) {
        lastEx = ex;
        log.warn("Fee GL posting attempt {} failed for transfer {}: {}", attempt, order.getId(), ex.getMessage());
        if (attempt < 3) {
          try {
            Thread.sleep(attempt * 200L);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    log.error("Fee GL posting exhausted retries for transfer {}. Marking feeEntryRef=PENDING_RECON without reversing customer transfer.", order.getId());
    step(order.getId(), "CREDIT_FEE_INCOME", "PENDING_RECON", lastEx != null ? lastEx.getMessage() : "Exhausted retries");
    return "PENDING_RECON";
  }

  private MoneyResult callDebitWithRetry(UUID accountId, TransferOrderEntity order) throws Exception {
    Exception lastEx = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        return callDebit(accountId, order);
      } catch (Exception ex) {
        lastEx = ex;
        if (isAuthoritativeDebitFailure(ex)) {
          throw ex;
        }
        log.warn("Debit attempt {} encountered transient error for transfer {}: {}", attempt, order.getId(), ex.getMessage());
        if (attempt < 3) {
          try {
            Thread.sleep(attempt * 250L);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    throw lastEx;
  }

  private MoneyResult callCreditWithRetry(UUID accountId, TransferOrderEntity order, String referenceId) throws Exception {
    Exception lastEx = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        return callCredit(accountId, order, referenceId);
      } catch (Exception ex) {
        lastEx = ex;
        if (isAuthoritativeCreditFailure(ex)) {
          throw ex;
        }
        log.warn("Internal credit attempt {} encountered transient error for transfer {}: {}", attempt, order.getId(), ex.getMessage());
        if (attempt < 3) {
          try {
            Thread.sleep(attempt * 250L);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    throw lastEx;
  }

  private boolean isAuthoritativeDebitFailure(Throwable ex) {
    if (ex == null) {
      return false;
    }
    if (ex instanceof BusinessException be) {
      String code = be.getCode();
      if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(code)
          || "DAILY_LIMIT_EXCEEDED".equalsIgnoreCase(code)
          || "ACCOUNT_NOT_FOUND".equalsIgnoreCase(code)
          || "ACCOUNT_INACTIVE".equalsIgnoreCase(code)
          || "ACCOUNT_FROZEN".equalsIgnoreCase(code)
          || "ACCOUNT_CLOSED".equalsIgnoreCase(code)
          || "INVALID_ACCOUNT_STATUS".equalsIgnoreCase(code)
          || "CURRENCY_MISMATCH".equalsIgnoreCase(code)
          || "INVALID_AMOUNT".equalsIgnoreCase(code)) {
        return true;
      }
      HttpStatus status = be.getStatus();
      if (status != null && status.is4xxClientError()
          && status != HttpStatus.REQUEST_TIMEOUT
          && status != HttpStatus.CONFLICT
          && status != HttpStatus.TOO_MANY_REQUESTS) {
        return true;
      }
    }
    if (ex instanceof FeignException fe) {
      int status = fe.status();
      return status >= 400 && status < 500
          && status != 408
          && status != 409
          && status != 429;
    }
    Throwable cause = ex.getCause();
    if (cause != null && cause != ex) {
      return isAuthoritativeDebitFailure(cause);
    }
    return false;
  }

  private boolean isAuthoritativeCreditFailure(Throwable ex) {
    if (ex == null) {
      return false;
    }
    if (ex instanceof BusinessException be) {
      String code = be.getCode();
      if ("ACCOUNT_NOT_FOUND".equalsIgnoreCase(code)
          || "ACCOUNT_INACTIVE".equalsIgnoreCase(code)
          || "ACCOUNT_FROZEN".equalsIgnoreCase(code)
          || "ACCOUNT_CLOSED".equalsIgnoreCase(code)
          || "INVALID_ACCOUNT_STATUS".equalsIgnoreCase(code)
          || "CURRENCY_MISMATCH".equalsIgnoreCase(code)
          || "INVALID_AMOUNT".equalsIgnoreCase(code)) {
        return true;
      }
      HttpStatus status = be.getStatus();
      if (status != null && status.is4xxClientError()
          && status != HttpStatus.REQUEST_TIMEOUT
          && status != HttpStatus.CONFLICT
          && status != HttpStatus.TOO_MANY_REQUESTS) {
        return true;
      }
    }
    if (ex instanceof FeignException fe) {
      int status = fe.status();
      return status >= 400 && status < 500
          && status != 408
          && status != 409
          && status != 429;
    }
    Throwable cause = ex.getCause();
    if (cause != null && cause != ex) {
      return isAuthoritativeCreditFailure(cause);
    }
    return false;
  }

  /** Dest not credited yet — refund full debit (principal + fee) to source. */
  private TransferOrderEntity compensateSourceOnly(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.COMPENSATING);
    order.setFailureReason(reason);
    order.setUpdatedAt(Instant.now());
    order = persist(order);
    try {
      String ref = "TX-REFUND-" + order.getId();
      MoneyResult refund = callCreditTotal(order.getFromAccountId(), order, ref);
      order.setStatus(TransferStatus.COMPENSATED);
      order.setUpdatedAt(Instant.now());
      order = persistWithEvent(order, "FAILED");
      step(order.getId(), "COMPENSATE_SOURCE", "SUCCESS", "ledger=" + refund.ledgerEntryId());
    } catch (Exception ex) {
      order.setStatus(TransferStatus.REVIEW_REQUIRED);
      order.setFailureReason("COMPENSATION_PARTIAL: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      order = persistWithEvent(order, "REVIEW");
      step(order.getId(), "COMPENSATE_SOURCE", "FAILED", ex.getMessage());
    }
    return order;
  }

  /**
   * Dest already credited; fee GL failed.
   * Reverse dest principal, then refund source principal+fee.
   * Fee income was not posted (or failed before commit) so no fee reverse.
   */
  private TransferOrderEntity compensateAfterDestCredit(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.COMPENSATING);
    order.setFailureReason(reason);
    order.setUpdatedAt(Instant.now());
    order = persist(order);

    try {
      String revRef = "TX-REVERSE-DEST-" + order.getId();
      MoneyResult rev = callDebitAmount(
          order.getToAccountId(),
          order.getAmount(),
          revRef,
          "Reverse dest " + order.getId(),
          false);
      step(order.getId(), "REVERSE_DEST", "SUCCESS", "ledger=" + rev.ledgerEntryId());
    } catch (Exception ex) {
      order.setStatus(TransferStatus.REVIEW_REQUIRED);
      order.setFailureReason("COMPENSATION_PARTIAL: reverse dest failed: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      order = persistWithEvent(order, "REVIEW");
      step(order.getId(), "REVERSE_DEST", "FAILED", ex.getMessage());
      return order;
    }

    try {
      String ref = "TX-REFUND-" + order.getId();
      MoneyResult refund = callCreditTotal(order.getFromAccountId(), order, ref);
      order.setStatus(TransferStatus.COMPENSATED);
      order.setUpdatedAt(Instant.now());
      order = persistWithEvent(order, "FAILED");
      step(order.getId(), "COMPENSATE_SOURCE", "SUCCESS", "ledger=" + refund.ledgerEntryId());
    } catch (Exception ex) {
      order.setStatus(TransferStatus.REVIEW_REQUIRED);
      order.setFailureReason("COMPENSATION_PARTIAL: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      order = persistWithEvent(order, "REVIEW");
      step(order.getId(), "COMPENSATE_SOURCE", "FAILED", ex.getMessage());
    }
    return order;
  }

  private TransferOrderEntity runExternalAfterDebit(TransferOrderEntity order) {
    try {
      AccountView source = accountGateway.getAccount(order.getFromAccountId());
      if (source == null) {
        return markUnknown(order, "SOURCE_ACCOUNT_UNAVAILABLE", null);
      }
      order.setProviderAttemptCount(order.getProviderAttemptCount() + 1);
      order.setLastProviderQueryAt(Instant.now());
      order = persist(order);
      NapasPaymentResponse response = napasSwitchClient.executePayment(
          source.accountNumber(),
          order.getTargetBankCode(),
          order.getToAccountNumber(),
          order.getAmount(),
          order.getDescription(),
          "TX-SWITCH-" + order.getId());
      return resolveExternalOutcome(order, response, "PAYMENT_RESPONSE");
    } catch (Exception ex) {
      // A timeout may happen after the switch accepted the payment. Never refund an unknown outcome.
      order = markUnknown(order, formatReason("NAPAS_OUTCOME_UNKNOWN", ex.getMessage()), null);
      step(order.getId(), "NAPAS_PAYMENT", "UNKNOWN", ex.getMessage());
      return order;
    }
  }

  public TransferOrderEntity resolveExternalOutcome(
      TransferOrderEntity order, NapasPaymentResponse response, String source) {
    order = reload(order.getId());
    if (response == null) {
      return markUnknown(order, "NAPAS_EMPTY_RESPONSE", null);
    }
    order.setProviderReferenceId(response.napasRefId());
    order.setProviderStatus(response.outcome().name());
    order.setLastProviderQueryAt(Instant.now());
    order = persist(order);
    step(order.getId(), "NAPAS_" + source, response.outcome().name(),
        response.responseCode() + ": " + response.responseMessage());

    if (response.outcome() == ProviderOutcome.SUCCESS) {
      return completeExternal(order);
    }
    if (response.outcome() == ProviderOutcome.FAILED) {
      return compensateSourceOnly(order,
          formatReason("NAPAS_REJECTED", response.responseCode() + ": " + response.responseMessage()));
    }
    return markUnknown(order,
        formatReason("NAPAS_" + response.outcome().name(), response.responseMessage()),
        response.napasRefId());
  }

  private TransferOrderEntity completeExternal(TransferOrderEntity order) {
    if (feeGlService.requiresPosting(order) && order.getFeeEntryRef() == null) {
      String feeLedgerId = postFeeWithRetry(order);
      order.setFeeEntryRef(feeLedgerId);
    }
    order.setStatus(TransferStatus.COMPLETED);
    order.setFailureReason(null);
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "COMPLETED");
  }

  private TransferOrderEntity markUnknown(TransferOrderEntity order, String reason, String providerReferenceId) {
    order.setStatus(TransferStatus.UNKNOWN);
    order.setFailureReason(truncate(reason));
    if (providerReferenceId != null && !providerReferenceId.isBlank()) {
      order.setProviderReferenceId(providerReferenceId);
    }
    order.setReconciliationAttempts(0);
    order.setNextReconciliationAt(Instant.now().plusSeconds(30));
    order.setReconciliationStatus("RETRY_0");
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "REVIEW");
  }

  public TransferOrderEntity escalateExternalReview(TransferOrderEntity order, String reason) {
    order = reload(order.getId());
    order.setStatus(TransferStatus.REVIEW_REQUIRED);
    order.setFailureReason(truncate(reason));
    order.setUpdatedAt(Instant.now());
    step(order.getId(), "ESCALATE_REVIEW", "REVIEW_REQUIRED", reason);
    return persistWithEvent(order, "REVIEW");
  }

  public TransferOrderEntity reconcileExternalOrder(TransferOrderEntity order) {
    return reconcileOrder(order);
  }

  public TransferOrderEntity reconcileOrder(TransferOrderEntity order) {
    order = reload(order.getId());
    if (order.getStatus() != TransferStatus.UNKNOWN && order.getStatus() != TransferStatus.REVIEW_REQUIRED) {
      return order;
    }

    // Phase 1: Reconcile DEBIT if debit outcome was ambiguous
    if ("RETRY_DEBIT".equals(order.getReconciliationStatus()) || order.getDebitEntryRef() == null) {
      try {
        MoneyResult debit = callDebit(order.getFromAccountId(), order);
        order.setDebitEntryRef(debit.ledgerEntryId());
        order.setStatus(TransferStatus.DEBITED);
        order.setUpdatedAt(Instant.now());
        order = persist(order);
        step(order.getId(), "RECON_DEBIT", "SUCCESS", "Debit confirmed on account-service: " + debit.ledgerEntryId());
        if ("INTERBANK".equalsIgnoreCase(order.getTransferType())) {
          return runExternalAfterDebit(order);
        }
        return runInternalAfterDebit(order);
      } catch (Exception ex) {
        if (isAuthoritativeDebitFailure(ex)) {
          order = markFailed(order, formatReason(ex));
          step(order.getId(), "RECON_DEBIT", "FAILED", "Authoritative debit rejection: " + ex.getMessage());
          return order;
        }
        return advanceReconciliationBackoff(order, "Debit status still transient: " + ex.getMessage());
      }
    }

    // Phase 2: Reconcile TRANSFER execution (INTERNAL vs INTERBANK)
    if ("INTERNAL".equalsIgnoreCase(order.getTransferType())) {
      return reconcileInternalOrder(order);
    }
    return reconcileInterbankOrder(order);
  }

  private TransferOrderEntity reconcileInternalOrder(TransferOrderEntity order) {
    try {
      MoneyResult credit = callCredit(order.getToAccountId(), order, "TX-CREDIT-" + order.getId());
      order.setReconciliationStatus("RESOLVED_SUCCESS");
      step(order.getId(), "RECONCILIATION_INTERNAL", "SUCCESS", "Credit confirmed on account-service: " + credit.ledgerEntryId());
      return completeInternal(order, credit.ledgerEntryId());
    } catch (Exception ex) {
      if (isAuthoritativeCreditFailure(ex)) {
        order.setReconciliationStatus("RESOLVED_FAILED");
        step(order.getId(), "RECONCILIATION_INTERNAL", "FAILED", "Authoritative credit rejection: " + ex.getMessage());
        return compensateSourceOnly(order, formatReason(ex));
      }
      return advanceReconciliationBackoff(order, "Internal credit status still transient: " + ex.getMessage());
    }
  }

  private TransferOrderEntity reconcileInterbankOrder(TransferOrderEntity order) {
    String clientRequestId = "TX-SWITCH-" + order.getId();
    NapasPaymentResponse response;
    try {
      response = napasSwitchClient.inquirePayment(clientRequestId, order.getProviderReferenceId());
    } catch (Exception ex) {
      log.warn("Napas status inquiry failed for order {}: {}", order.getId(), ex.getMessage());
      response = new NapasPaymentResponse(order.getProviderReferenceId(), ProviderOutcome.UNKNOWN, "EX", ex.getMessage());
    }

    if (response.outcome() == ProviderOutcome.SUCCESS) {
      order.setProviderStatus(response.outcome().name());
      order.setReconciliationStatus("RESOLVED_SUCCESS");
      order.setNapasRrn(response.napasRefId());
      step(order.getId(), "RECONCILIATION_INTERBANK", "SUCCESS", "Authoritative success from switch");
      return completeExternal(order);
    }

    if (response.outcome() == ProviderOutcome.FAILED) {
      order.setProviderStatus(response.outcome().name());
      order.setReconciliationStatus("RESOLVED_FAILED");
      step(order.getId(), "RECONCILIATION_INTERBANK", "FAILED", "Authoritative rejection from switch");
      return compensateSourceOnly(order, formatReason("NAPAS_REJECTED", response.responseCode() + ": " + response.responseMessage()));
    }

    return advanceReconciliationBackoff(order, "Switch inquiry returned outcome: " + response.outcome());
  }

  private TransferOrderEntity advanceReconciliationBackoff(TransferOrderEntity order, String reason) {
    int attempts = order.getReconciliationAttempts() + 1;
    order.setReconciliationAttempts(attempts);
    if (attempts >= 5) {
      order.setStatus(TransferStatus.MANUAL_REVIEW);
      order.setReconciliationStatus("ESCALATED_MANUAL_REVIEW");
      order.setFailureReason("Reconciliation exhausted 5 attempts: " + reason);
      order.setUpdatedAt(Instant.now());
      step(order.getId(), "RECONCILIATION", "ESCALATED", "Exhausted 5 retry attempts");
      return persistWithEvent(order, "REVIEW");
    }

    int delaySeconds = switch (attempts) {
      case 1 -> 30;
      case 2 -> 60;
      case 3 -> 300;
      case 4 -> 900;
      default -> 1800;
    };
    order.setNextReconciliationAt(Instant.now().plusSeconds(delaySeconds));
    order.setReconciliationStatus("RETRY_" + attempts);
    order.setUpdatedAt(Instant.now());
    return persist(order);
  }

  public TransferOrderEntity forceSettle(UUID orderId, UUID adminUserId, String reason) {
    TransferOrderEntity order = reload(orderId);
    if (order.getStatus() != TransferStatus.MANUAL_REVIEW
        && order.getStatus() != TransferStatus.UNKNOWN
        && order.getStatus() != TransferStatus.REVIEW_REQUIRED) {
      throw new BusinessException("INVALID_STATUS", "Order is not in a manual review or unknown status");
    }
    TransferOrderEntity completed = completeExternal(order);
    manualReviewAuditLogRepository.save(new ManualReviewAuditLogEntity(
        UUID.randomUUID(), orderId, adminUserId, "FORCE_SETTLE",
        reason != null ? reason : "Admin force settle"));
    step(orderId, "ADMIN_FORCE_SETTLE", "SUCCESS", "Settled by admin " + adminUserId + ": " + reason);
    return completed;
  }

  public TransferOrderEntity forceRefund(UUID orderId, UUID adminUserId, String reason) {
    TransferOrderEntity order = reload(orderId);
    if (order.getStatus() != TransferStatus.MANUAL_REVIEW
        && order.getStatus() != TransferStatus.UNKNOWN
        && order.getStatus() != TransferStatus.REVIEW_REQUIRED) {
      throw new BusinessException("INVALID_STATUS", "Order is not in a manual review or unknown status");
    }
    TransferOrderEntity refunded = compensateSourceOnly(order, formatReason("ADMIN_MANUAL_REFUND", reason));
    manualReviewAuditLogRepository.save(new ManualReviewAuditLogEntity(
        UUID.randomUUID(), orderId, adminUserId, "FORCE_REFUND",
        reason != null ? reason : "Admin force refund"));
    step(orderId, "ADMIN_FORCE_REFUND", "SUCCESS", "Refunded by admin " + adminUserId + ": " + reason);
    return refunded;
  }

  private TransferOrderEntity markFailed(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.FAILED);
    order.setFailureReason(truncate(reason));
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "FAILED");
  }

  private TransferOrderEntity persist(TransferOrderEntity source) {
    TransferOrderEntity managed = reload(source.getId());
    managed.setStatus(source.getStatus());
    managed.setReconciliationStatus(source.getReconciliationStatus());
    managed.setReconciliationAttempts(source.getReconciliationAttempts());
    managed.setNextReconciliationAt(source.getNextReconciliationAt());
    managed.setNapasRrn(source.getNapasRrn());
    managed.setNapasTraceNo(source.getNapasTraceNo());
    managed.setFailureReason(source.getFailureReason());
    managed.setDebitEntryRef(source.getDebitEntryRef());
    managed.setCreditEntryRef(source.getCreditEntryRef());
    managed.setFeeEntryRef(source.getFeeEntryRef());
    managed.setProviderReferenceId(source.getProviderReferenceId());
    managed.setProviderStatus(source.getProviderStatus());
    managed.setProviderAttemptCount(source.getProviderAttemptCount());
    managed.setLastProviderQueryAt(source.getLastProviderQueryAt());
    managed.setUpdatedAt(source.getUpdatedAt());
    return transferOrderRepository.saveAndFlush(managed);
  }

  private TransferOrderEntity persistWithEvent(TransferOrderEntity source, String eventType) {
    TransferOrderEntity saved = persist(source);
    if ("COMPLETED".equals(eventType)) {
      enqueueCompleted(saved);
    } else if ("FAILED".equals(eventType)) {
      enqueueFailed(saved);
    } else if ("REVIEW".equals(eventType)) {
      enqueueReviewRequired(saved);
    }
    return saved;
  }

  private TransferOrderEntity reload(UUID transferId) {
    return transferOrderRepository.findById(transferId)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
  }

  protected void step(UUID transferId, String step, String status, String detail) {
    try {
      transactionTemplate.executeWithoutResult(
          tx -> sagaStepLogRepository.save(
              SagaStepLogEntity.of(transferId, step, status, detail)));
    } catch (RuntimeException ex) {
      // Step history is diagnostic. It must never trigger or alter a money compensation path.
      log.error("Cannot persist saga step transfer={} step={} status={}: {}",
          transferId, step, status, ex.getMessage());
    }
  }

  private void enqueueCompleted(TransferOrderEntity order) {
    Map<String, Object> payload = baseEvent("TRANSACTION_COMPLETED", order);
    outboxService.enqueue("TRANSACTION_COMPLETED", order.getId(), payload);
  }

  private void enqueueFailed(TransferOrderEntity order) {
    Map<String, Object> payload = baseEvent("TRANSACTION_FAILED", order);
    payload.put("failureReason", order.getFailureReason());
    payload.put("finalStatus", order.getStatus().name());
    outboxService.enqueue("TRANSACTION_FAILED", order.getId(), payload);
  }

  private void enqueueReviewRequired(TransferOrderEntity order) {
    Map<String, Object> payload = baseEvent("TRANSACTION_REVIEW_REQUIRED", order);
    payload.put("failureReason", order.getFailureReason());
    payload.put("finalStatus", order.getStatus().name());
    payload.put("providerReferenceId", order.getProviderReferenceId());
    outboxService.enqueue("TRANSACTION_REVIEW_REQUIRED", order.getId(), payload);
  }

  private Map<String, Object> baseEvent(String type, TransferOrderEntity order) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("transactionId", order.getId().toString());
    data.put("userId", order.getUserId().toString());
    data.put("fromAccountId", order.getFromAccountId().toString());
    data.put("toAccountId", order.getToAccountId() == null ? null : order.getToAccountId().toString());
    data.put("amount", order.getAmount());
    data.put("feeAmount", order.getFeeAmount() == null ? BigDecimal.ZERO : order.getFeeAmount());
    data.put("feeEntryRef", order.getFeeEntryRef());
    data.put("currency", order.getCurrency());
    data.put("description", order.getDescription());

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("eventId", UUID.nameUUIDFromBytes(
        (type + ":" + order.getId()).getBytes(StandardCharsets.UTF_8)).toString());
    root.put("eventType", type);
    root.put("occurredAt", Instant.now().toString());
    root.put("data", data);
    return root;
  }

  private MoneyResult callDebit(UUID accountId, TransferOrderEntity order) {
    BigDecimal fee = order.getFeeAmount() == null ? BigDecimal.ZERO : order.getFeeAmount();
    BigDecimal debitTotal = order.getAmount().add(fee);
    String desc = order.getDescription();
    if (fee.compareTo(BigDecimal.ZERO) > 0) {
      desc = (desc == null || desc.isBlank() ? "Transfer" : desc)
          + " (fee " + fee.toPlainString() + ")";
    }
    return callDebitAmount(accountId, debitTotal, "TX-DEBIT-" + order.getId(), desc, true);
  }

  private MoneyResult callDebitAmount(
      UUID accountId,
      BigDecimal amount,
      String referenceId,
      String description,
      boolean allowAutoSweep) {
    String commandId = referenceId + ":" + amount.stripTrailingZeros().toPlainString();
    MoneyResult result = accountGateway.debit(
        accountId, new MoneyCommand(amount, referenceId, description, commandId, allowAutoSweep));
    if (result == null) {
      throw new BusinessException("DEBIT_FAILED", "Debit failed");
    }
    return result;
  }

  /** Credit principal only (destination receives amount, never fee). */
  private MoneyResult callCredit(UUID accountId, TransferOrderEntity order, String referenceId) {
    return callCreditAmount(accountId, order.getAmount(), "TX-CREDIT-" + order.getId(), order.getDescription());
  }

  /** Compensation: reverse full source debit (amount + fee). */
  private MoneyResult callCreditTotal(UUID accountId, TransferOrderEntity order, String referenceId) {
    BigDecimal fee = order.getFeeAmount() == null ? BigDecimal.ZERO : order.getFeeAmount();
    BigDecimal total = order.getAmount().add(fee);
    MoneyResult result = accountGateway.compensateCredit(
        accountId,
        new MoneyCommand(total, "TX-REFUND-" + order.getId(), "Compensation " + order.getId(), "TX-REFUND-" + order.getId()));
    if (result == null) {
      throw new BusinessException("COMPENSATION_CREDIT_FAILED", "Compensation credit failed");
    }
    return result;
  }

  private MoneyResult callCreditAmount(
      UUID accountId,
      BigDecimal amount,
      String referenceId,
      String description) {
    MoneyResult result = accountGateway.credit(accountId, new MoneyCommand(amount, referenceId, description, referenceId));
    if (result == null) {
      throw new BusinessException("CREDIT_FAILED", "Credit failed");
    }
    return result;
  }

  /** Prefer "CODE: message" so clients can map business codes to i18n. */
  static String formatReason(Throwable ex) {
    if (ex instanceof BusinessException be) {
      return formatReason(be.getCode(), be.getMessage());
    }
    String msg = ex == null ? null : ex.getMessage();
    return formatReason("TRANSFER_FAILED", msg);
  }

  static String formatReason(String code, String message) {
    String c = code == null || code.isBlank() ? "TRANSFER_FAILED" : code.trim();
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) {
      return c;
    }
    // Avoid double-prefix when message already starts with CODE:
    if (m.regionMatches(true, 0, c + ":", 0, c.length() + 1)) {
      return m;
    }
    return c + ": " + m;
  }

  private String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() > 250 ? s.substring(0, 250) : s;
  }
}
