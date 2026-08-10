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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferSagaOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

  private final TransferOrderRepository transferOrderRepository;
  private final SagaStepLogRepository sagaStepLogRepository;
  private final AccountGateway accountGateway;
  private final OutboxService outboxService;
  private final TransferFeeGlService feeGlService;
  private final NapasSwitchClient napasSwitchClient;
  private final TransactionTemplate transactionTemplate;
  private final boolean failCredit;

  public TransferSagaOrchestrator(
      TransferOrderRepository transferOrderRepository,
      SagaStepLogRepository sagaStepLogRepository,
      AccountGateway accountGateway,
      OutboxService outboxService,
      TransferFeeGlService feeGlService,
      NapasSwitchClient napasSwitchClient,
      TransactionTemplate transactionTemplate,
      @Value("${bank.saga.fail-credit:false}") boolean failCredit) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaStepLogRepository = sagaStepLogRepository;
    this.accountGateway = accountGateway;
    this.outboxService = outboxService;
    this.feeGlService = feeGlService;
    this.napasSwitchClient = napasSwitchClient;
    this.transactionTemplate = transactionTemplate;
    this.failCredit = failCredit;
  }

  public TransferOrderEntity run(TransferOrderEntity order) {
    order = reload(order.getId());
    MoneyResult debit;
    try {
      debit = callDebit(order.getFromAccountId(), order);
    } catch (BusinessException ex) {
      order = markFailed(order, formatReason(ex));
      step(order.getId(), "DEBIT_SOURCE", "FAILED", ex.getMessage());
      return order;
    } catch (Exception ex) {
      order = markFailed(order, formatReason("DEBIT_ERROR", ex.getMessage()));
      step(order.getId(), "DEBIT_SOURCE", "FAILED", ex.getMessage());
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

    MoneyResult credit;
    try {
      if (failCredit) {
        throw new BusinessException("SAGA_INJECTED_FAIL", "Injected credit failure for demo");
      }
      credit = callCredit(order.getToAccountId(), order, order.getId().toString());
    } catch (Exception ex) {
      log.warn("Credit failed for transfer {}, compensating", order.getId());
      step(order.getId(), "CREDIT_DEST", "FAILED", ex.getMessage());
      return compensateSourceOnly(order, formatReason(ex));
    }
    // Credit succeeded. Never refund source merely because DB/audit persistence later fails.
    order.setCreditEntryRef(credit.ledgerEntryId());
    order.setUpdatedAt(Instant.now());
    order = persist(order);
    step(order.getId(), "CREDIT_DEST", "SUCCESS", "ledger=" + credit.ledgerEntryId());

    // STEP 3 — fee GL: credit bank income account (skip when fee = 0)
    if (feeGlService.requiresPosting(order)) {
      String feeLedgerId;
      try {
        feeLedgerId = feeGlService.postFee(order);
      } catch (Exception ex) {
        log.warn("Fee GL failed for transfer {}, reversing dest and refunding source", order.getId());
        step(order.getId(), "CREDIT_FEE_INCOME", "FAILED", ex.getMessage());
        return compensateAfterDestCredit(order, formatReason(ex));
      }
      order.setFeeEntryRef(feeLedgerId);
      order.setUpdatedAt(Instant.now());
      order = persist(order);
      step(order.getId(), "CREDIT_FEE_INCOME", "SUCCESS", "ledger=" + feeLedgerId);
    } else {
      step(order.getId(), "CREDIT_FEE_INCOME", "SKIPPED", "fee=0");
    }

    order.setStatus(TransferStatus.COMPLETED);
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "COMPLETED");
  }

  /** Dest not credited yet — refund full debit (principal + fee) to source. */
  private TransferOrderEntity compensateSourceOnly(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.COMPENSATING);
    order.setFailureReason(reason);
    order.setUpdatedAt(Instant.now());
    order = persist(order);
    try {
      String ref = order.getId() + "-compensation";
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
      String revRef = order.getId() + "-reverse-dest";
      MoneyResult rev = callDebitAmount(
          order.getToAccountId(),
          order.getAmount(),
          revRef,
          "Reverse dest " + order.getId());
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
      String ref = order.getId() + "-compensation";
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
          order.getId().toString());
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
      try {
        String feeLedgerId = feeGlService.postFee(order);
        order.setFeeEntryRef(feeLedgerId);
        step(order.getId(), "CREDIT_FEE_INCOME", "SUCCESS", "ledger=" + feeLedgerId);
      } catch (Exception ex) {
        order.setStatus(TransferStatus.REVIEW_REQUIRED);
        order.setFailureReason(truncate(formatReason("FEE_GL_AFTER_NAPAS_FAILED", ex.getMessage())));
        order.setUpdatedAt(Instant.now());
        order = persistWithEvent(order, "REVIEW");
        step(order.getId(), "CREDIT_FEE_INCOME", "FAILED", ex.getMessage());
        return order;
      }
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
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "REVIEW");
  }

  public TransferOrderEntity escalateExternalReview(TransferOrderEntity order, String reason) {
    order = reload(order.getId());
    order.setStatus(TransferStatus.REVIEW_REQUIRED);
    order.setFailureReason(truncate(reason));
    order.setUpdatedAt(Instant.now());
    order = persistWithEvent(order, "REVIEW");
    step(order.getId(), "NAPAS_MANUAL_REVIEW", "REQUIRED", reason);
    return order;
  }

  private MoneyResult callDebit(UUID accountId, TransferOrderEntity order) {
    BigDecimal fee = order.getFeeAmount() == null ? BigDecimal.ZERO : order.getFeeAmount();
    BigDecimal debitTotal = order.getAmount().add(fee);
    String desc = order.getDescription();
    if (fee.compareTo(BigDecimal.ZERO) > 0) {
      desc = (desc == null || desc.isBlank() ? "Transfer" : desc)
          + " (fee " + fee.toPlainString() + ")";
    }
    return callDebitAmount(accountId, debitTotal, order.getId().toString(), desc);
  }

  private MoneyResult callDebitAmount(UUID accountId, BigDecimal amount, String referenceId, String description) {
    MoneyResult result = accountGateway.debit(accountId, new MoneyCommand(amount, referenceId, description, referenceId));
    if (result == null) {
      throw new BusinessException("DEBIT_FAILED", "Debit failed");
    }
    return result;
  }

  /** Credit principal only (destination receives amount, never fee). */
  private MoneyResult callCredit(UUID accountId, TransferOrderEntity order, String referenceId) {
    return callCreditAmount(accountId, order.getAmount(), referenceId, order.getDescription());
  }

  /** Compensation: reverse full source debit (amount + fee). */
  private MoneyResult callCreditTotal(UUID accountId, TransferOrderEntity order, String referenceId) {
    BigDecimal fee = order.getFeeAmount() == null ? BigDecimal.ZERO : order.getFeeAmount();
    BigDecimal total = order.getAmount().add(fee);
    return callCreditAmount(accountId, total, referenceId, "Compensation " + order.getId());
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

  private TransferOrderEntity markFailed(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.FAILED);
    order.setFailureReason(truncate(reason));
    order.setUpdatedAt(Instant.now());
    return persistWithEvent(order, "FAILED");
  }

  private TransferOrderEntity persist(TransferOrderEntity order) {
    TransferOrderEntity saved = transactionTemplate.execute(tx -> persistInCurrentTransaction(order));
    if (saved == null) {
      throw new IllegalStateException("Transfer state transaction returned no result");
    }
    return saved;
  }

  /** Persists the terminal state and its outbox event in one local database transaction. */
  private TransferOrderEntity persistWithEvent(TransferOrderEntity order, String eventKind) {
    TransferOrderEntity saved = transactionTemplate.execute(tx -> {
      TransferOrderEntity managed = persistInCurrentTransaction(order);
      switch (eventKind) {
        case "COMPLETED" -> enqueueCompleted(managed);
        case "FAILED" -> enqueueFailed(managed);
        case "REVIEW" -> enqueueReviewRequired(managed);
        default -> throw new IllegalArgumentException("Unsupported event kind: " + eventKind);
      }
      return managed;
    });
    if (saved == null) {
      throw new IllegalStateException("Transfer terminal transaction returned no result");
    }
    return saved;
  }

  private TransferOrderEntity persistInCurrentTransaction(TransferOrderEntity source) {
    TransferOrderEntity managed = transferOrderRepository.findById(source.getId())
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    managed.setStatus(source.getStatus());
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

  private TransferOrderEntity reload(UUID transferId) {
    return transferOrderRepository.findById(transferId)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
  }

  protected void step(UUID transferId, String step, String status, String detail) {
    try {
      transactionTemplate.executeWithoutResult(
          tx -> sagaStepLogRepository.save(SagaStepLogEntity.of(transferId, step, status, detail)));
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

  private String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() > 250 ? s.substring(0, 250) : s;
  }
}
