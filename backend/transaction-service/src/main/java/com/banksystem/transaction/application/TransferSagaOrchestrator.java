package com.banksystem.transaction.application;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.SagaStepLogEntity;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferSagaOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

  private final TransferOrderRepository transferOrderRepository;
  private final SagaStepLogRepository sagaStepLogRepository;
  private final AccountClient accountClient;
  private final OutboxService outboxService;
  private final TransferFeeGlService feeGlService;
  private final String internalApiKey;
  private final boolean failCredit;

  public TransferSagaOrchestrator(
      TransferOrderRepository transferOrderRepository,
      SagaStepLogRepository sagaStepLogRepository,
      AccountClient accountClient,
      OutboxService outboxService,
      TransferFeeGlService feeGlService,
      @Value("${bank.internal.account-api-key}") String internalApiKey,
      @Value("${bank.saga.fail-credit:false}") boolean failCredit) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaStepLogRepository = sagaStepLogRepository;
    this.accountClient = accountClient;
    this.outboxService = outboxService;
    this.feeGlService = feeGlService;
    this.internalApiKey = internalApiKey;
    this.failCredit = failCredit;
  }

  public TransferOrderEntity run(TransferOrderEntity order) {
    // STEP 1 — debit source (principal + fee)
    try {
      MoneyResult debit = callDebit(order.getFromAccountId(), order);
      order.setDebitEntryRef(debit.ledgerEntryId());
      order.setStatus(TransferStatus.DEBITED);
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "DEBIT_SOURCE", "SUCCESS", "ledger=" + debit.ledgerEntryId());
    } catch (BusinessException ex) {
      markFailed(order, ex.getCode() + ": " + ex.getMessage());
      step(order.getId(), "DEBIT_SOURCE", "FAILED", ex.getMessage());
      enqueueFailed(order);
      return order;
    } catch (Exception ex) {
      markFailed(order, "DEBIT_ERROR: " + ex.getMessage());
      step(order.getId(), "DEBIT_SOURCE", "FAILED", ex.getMessage());
      enqueueFailed(order);
      return order;
    }

    // STEP 2 — credit destination principal only
    try {
      if (failCredit) {
        throw new BusinessException("SAGA_INJECTED_FAIL", "Injected credit failure for demo",
            HttpStatus.SERVICE_UNAVAILABLE);
      }
      MoneyResult credit = callCredit(order.getToAccountId(), order, order.getId().toString());
      order.setCreditEntryRef(credit.ledgerEntryId());
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "CREDIT_DEST", "SUCCESS", "ledger=" + credit.ledgerEntryId());
    } catch (Exception ex) {
      log.warn("Credit failed for transfer {}, compensating", order.getId());
      step(order.getId(), "CREDIT_DEST", "FAILED", ex.getMessage());
      compensateSourceOnly(order, ex.getMessage());
      return order;
    }

    // STEP 3 — fee GL: credit bank income account (skip when fee = 0)
    if (feeGlService.requiresPosting(order)) {
      try {
        String feeLedgerId = feeGlService.postFee(order);
        order.setFeeEntryRef(feeLedgerId);
        order.setUpdatedAt(Instant.now());
        transferOrderRepository.save(order);
        step(order.getId(), "CREDIT_FEE_INCOME", "SUCCESS", "ledger=" + feeLedgerId);
      } catch (Exception ex) {
        log.warn("Fee GL failed for transfer {}, reversing dest and refunding source", order.getId());
        step(order.getId(), "CREDIT_FEE_INCOME", "FAILED", ex.getMessage());
        compensateAfterDestCredit(order, ex.getMessage());
        return order;
      }
    } else {
      step(order.getId(), "CREDIT_FEE_INCOME", "SKIPPED", "fee=0");
    }

    order.setStatus(TransferStatus.COMPLETED);
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);
    enqueueCompleted(order);
    return order;
  }

  /** Dest not credited yet — refund full debit (principal + fee) to source. */
  private void compensateSourceOnly(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.COMPENSATING);
    order.setFailureReason(reason);
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);
    try {
      String ref = order.getId() + "-compensation";
      MoneyResult refund = callCreditTotal(order.getFromAccountId(), order, ref);
      order.setStatus(TransferStatus.COMPENSATED);
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "COMPENSATE_SOURCE", "SUCCESS", "ledger=" + refund.ledgerEntryId());
      enqueueFailed(order);
    } catch (Exception ex) {
      order.setStatus(TransferStatus.COMPENSATED);
      order.setFailureReason("COMPENSATION_PARTIAL: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "COMPENSATE_SOURCE", "FAILED", ex.getMessage());
      enqueueFailed(order);
    }
  }

  /**
   * Dest already credited; fee GL failed.
   * Reverse dest principal, then refund source principal+fee.
   * Fee income was not posted (or failed before commit) so no fee reverse.
   */
  private void compensateAfterDestCredit(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.COMPENSATING);
    order.setFailureReason(reason);
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);

    try {
      String revRef = order.getId() + "-reverse-dest";
      MoneyResult rev = callDebitAmount(
          order.getToAccountId(),
          order.getAmount(),
          revRef,
          "Reverse dest " + order.getId());
      step(order.getId(), "REVERSE_DEST", "SUCCESS", "ledger=" + rev.ledgerEntryId());
    } catch (Exception ex) {
      order.setStatus(TransferStatus.COMPENSATED);
      order.setFailureReason("COMPENSATION_PARTIAL: reverse dest failed: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "REVERSE_DEST", "FAILED", ex.getMessage());
      enqueueFailed(order);
      return;
    }

    try {
      String ref = order.getId() + "-compensation";
      MoneyResult refund = callCreditTotal(order.getFromAccountId(), order, ref);
      order.setStatus(TransferStatus.COMPENSATED);
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "COMPENSATE_SOURCE", "SUCCESS", "ledger=" + refund.ledgerEntryId());
      enqueueFailed(order);
    } catch (Exception ex) {
      order.setStatus(TransferStatus.COMPENSATED);
      order.setFailureReason("COMPENSATION_PARTIAL: " + reason + " | " + ex.getMessage());
      order.setUpdatedAt(Instant.now());
      transferOrderRepository.save(order);
      step(order.getId(), "COMPENSATE_SOURCE", "FAILED", ex.getMessage());
      enqueueFailed(order);
    }
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
    try {
      ApiResponse<MoneyResult> res = accountClient.debit(
          accountId,
          new MoneyCommand(amount, referenceId, description, referenceId),
          internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        String code = res != null && res.error() != null ? res.error().code() : "DEBIT_FAILED";
        String msg = res != null && res.error() != null ? res.error().message() : "Debit failed";
        throw new BusinessException(code, msg, HttpStatus.UNPROCESSABLE_ENTITY);
      }
      return res.data();
    } catch (FeignException.UnprocessableEntity e) {
      throw mapFeignBusiness(e);
    } catch (FeignException e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service error: " + e.status(),
          HttpStatus.SERVICE_UNAVAILABLE);
    }
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
    try {
      ApiResponse<MoneyResult> res = accountClient.credit(
          accountId,
          new MoneyCommand(amount, referenceId, description, referenceId),
          internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        String code = res != null && res.error() != null ? res.error().code() : "CREDIT_FAILED";
        String msg = res != null && res.error() != null ? res.error().message() : "Credit failed";
        throw new BusinessException(code, msg, HttpStatus.UNPROCESSABLE_ENTITY);
      }
      return res.data();
    } catch (FeignException.UnprocessableEntity e) {
      throw mapFeignBusiness(e);
    } catch (FeignException e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service error: " + e.status(),
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private BusinessException mapFeignBusiness(FeignException e) {
    String body = e.contentUTF8();
    if (body != null && body.contains("INSUFFICIENT_BALANCE")) {
      return new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (body != null && body.contains("ACCOUNT_FROZEN")) {
      return new BusinessException("ACCOUNT_FROZEN", "Account is frozen", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (body != null && body.contains("ACCOUNT_NOT_FOUND")) {
      return new BusinessException("ACCOUNT_NOT_FOUND", "Account not found", HttpStatus.NOT_FOUND);
    }
    return new BusinessException("ACCOUNT_SERVICE_ERROR", body == null ? e.getMessage() : body,
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  private void markFailed(TransferOrderEntity order, String reason) {
    order.setStatus(TransferStatus.FAILED);
    order.setFailureReason(truncate(reason));
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);
  }

  @Transactional
  protected void step(UUID transferId, String step, String status, String detail) {
    sagaStepLogRepository.save(SagaStepLogEntity.of(transferId, step, status, detail));
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
    root.put("eventId", UUID.randomUUID().toString());
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
