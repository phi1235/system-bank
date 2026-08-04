package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Posts transfer fee to the bank fee-income account (simple GL credit).
 * Policy: only call when fee &gt; 0; idempotent reference {@code {transferId}-fee}.
 */
@Service
public class TransferFeeGlService {

  private final AccountGateway accountGateway;
  private final String incomeAccountNumber;
  private final AtomicReference<UUID> incomeAccountId = new AtomicReference<>();

  public TransferFeeGlService(
      AccountGateway accountGateway,
      @Value("${bank.transfer.fee.income-account-number:1099999999}") String incomeAccountNumber) {
    this.accountGateway = accountGateway;
    if (incomeAccountNumber == null || incomeAccountNumber.isBlank()) {
      throw new IllegalStateException(
          "bank.transfer.fee.income-account-number must be set");
    }
    this.incomeAccountNumber = incomeAccountNumber.trim();
  }

  /** @return true when fee must be posted to GL for this order */
  public boolean requiresPosting(TransferOrderEntity order) {
    BigDecimal fee = order.getFeeAmount();
    return fee != null && fee.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Credits bank income account with fee amount.
   * @return ledger entry id
   */
  public String postFee(TransferOrderEntity order) {
    if (!requiresPosting(order)) {
      return null;
    }
    UUID incomeId = resolveIncomeAccountId();
    String referenceId = feeReference(order.getId());
    String description = "Transfer fee " + order.getId();
    try {
      MoneyResult res = accountGateway.credit(
          incomeId,
          new MoneyCommand(order.getFeeAmount(), referenceId, description, referenceId));
      if (res == null) {
        throw new BusinessException("FEE_GL_FAILED", "Fee GL credit failed");
      }
      return res.ledgerEntryId();
    } catch (BusinessException be) {
      throw be;
    } catch (Exception e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service error: " + e.getMessage());
    }
  }

  public String feeReference(UUID transferId) {
    return transferId + "-fee";
  }

  public UUID resolveIncomeAccountId() {
    UUID cached = incomeAccountId.get();
    if (cached != null) {
      return cached;
    }
    try {
      AccountView res = accountGateway.getAccountByNumber(incomeAccountNumber);
      if (res == null) {
        throw new BusinessException(
            "FEE_INCOME_ACCOUNT_MISSING",
            "Fee income account not found: " + incomeAccountNumber);
      }
      UUID id = res.idUuid();
      incomeAccountId.compareAndSet(null, id);
      return id;
    } catch (BusinessException be) {
      throw be;
    } catch (Exception e) {
      throw new BusinessException(
          "FEE_INCOME_ACCOUNT_MISSING",
          "Cannot resolve fee income account " + incomeAccountNumber + ": " + e.getMessage());
    }
  }

  public String incomeAccountNumber() {
    return incomeAccountNumber;
  }
}
