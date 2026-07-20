package com.banksystem.transaction.application;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Posts transfer fee to the bank fee-income account (simple GL credit).
 * Policy: only call when fee &gt; 0; idempotent reference {@code {transferId}-fee}.
 */
@Service
public class TransferFeeGlService {

  private final AccountClient accountClient;
  private final String internalApiKey;
  private final String incomeAccountNumber;
  private final AtomicReference<UUID> incomeAccountId = new AtomicReference<>();

  public TransferFeeGlService(
      AccountClient accountClient,
      @Value("${bank.internal.account-api-key}") String internalApiKey,
      @Value("${bank.transfer.fee.income-account-number:1099999999}") String incomeAccountNumber) {
    this.accountClient = accountClient;
    this.internalApiKey = internalApiKey;
    this.incomeAccountNumber = incomeAccountNumber == null || incomeAccountNumber.isBlank()
        ? "1099999999"
        : incomeAccountNumber.trim();
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
      ApiResponse<MoneyResult> res = accountClient.credit(
          incomeId,
          new MoneyCommand(order.getFeeAmount(), referenceId, description, referenceId),
          internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        String code = res != null && res.error() != null ? res.error().code() : "FEE_GL_FAILED";
        String msg = res != null && res.error() != null ? res.error().message() : "Fee GL credit failed";
        throw new BusinessException(code, msg, HttpStatus.UNPROCESSABLE_ENTITY);
      }
      return res.data().ledgerEntryId();
    } catch (FeignException.UnprocessableEntity e) {
      throw new BusinessException("FEE_GL_FAILED", e.contentUTF8() == null ? e.getMessage() : e.contentUTF8(),
          HttpStatus.UNPROCESSABLE_ENTITY);
    } catch (FeignException e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service error: " + e.status(),
          HttpStatus.SERVICE_UNAVAILABLE);
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
      ApiResponse<AccountView> res = accountClient.getByNumber(incomeAccountNumber, internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        throw new BusinessException(
            "FEE_INCOME_ACCOUNT_MISSING",
            "Fee income account not found: " + incomeAccountNumber,
            HttpStatus.SERVICE_UNAVAILABLE);
      }
      UUID id = res.data().idUuid();
      incomeAccountId.compareAndSet(null, id);
      return id;
    } catch (FeignException e) {
      throw new BusinessException(
          "FEE_INCOME_ACCOUNT_MISSING",
          "Cannot resolve fee income account " + incomeAccountNumber + ": " + e.status(),
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  public String incomeAccountNumber() {
    return incomeAccountNumber;
  }
}
