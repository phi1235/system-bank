package com.banksystem.transaction.domain.forensics;

import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record CanonicalProposalPayload(
    UUID caseId,
    int cycle,
    UUID sourceTransactionId,
    UUID targetAccountId,
    String direction,
    String amount,
    String currency,
    String reason) {

  public static int getCurrencyScale(String currency) {
    if (currency == null) return 2;
    String c = currency.trim().toUpperCase();
    if ("VND".equals(c) || "JPY".equals(c) || "KRW".equals(c)) {
      return 0;
    }
    return 2;
  }

  public static BigDecimal normalizeAmount(BigDecimal amount, String currency) {
    if (amount == null || amount.signum() <= 0) {
      throw new BusinessException("INVALID_ADJUSTMENT_AMOUNT", "Amount must be strictly greater than zero", HttpStatus.BAD_REQUEST);
    }
    int expectedScale = getCurrencyScale(currency);
    try {
      return amount.setScale(expectedScale, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException ex) {
      throw new BusinessException(
          "INVALID_AMOUNT_SCALE",
          "Amount scale mismatch for currency " + currency + ". Maximum allowed decimal places: " + expectedScale,
          HttpStatus.BAD_REQUEST);
    }
  }

  public static CanonicalProposalPayload from(
      UUID caseId,
      int cycle,
      UUID sourceTransactionId,
      UUID targetAccountId,
      AdjustmentDirection direction,
      BigDecimal amount,
      String currency,
      String reason) {
    String normalizedCurrency = currency != null ? currency.trim().toUpperCase() : "VND";
    BigDecimal validAmount = normalizeAmount(amount, normalizedCurrency);
    String normalizedAmountStr = validAmount.toPlainString();
    String normalizedDirection = direction != null ? direction.name() : "CREDIT";
    String normalizedReason = reason != null ? reason.trim() : "";

    return new CanonicalProposalPayload(
        caseId,
        cycle > 0 ? cycle : 1,
        sourceTransactionId,
        targetAccountId,
        normalizedDirection,
        normalizedAmountStr,
        normalizedCurrency,
        normalizedReason);
  }

  public static CanonicalProposalPayload fromEntity(RemediationProposalEntity entity) {
    return from(
        entity.getCaseId(),
        entity.getInvestigationCycle(),
        entity.getSourceTransactionId(),
        entity.getTargetAccountId(),
        entity.getDirection(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getReason());
  }
}
