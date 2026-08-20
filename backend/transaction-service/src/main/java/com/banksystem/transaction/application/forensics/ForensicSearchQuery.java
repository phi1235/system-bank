package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

public record ForensicSearchQuery(
    String q,
    UUID transactionId,
    UUID accountId,
    TransferStatus status,
    String riskDecision,
    Instant from,
    Instant to,
    int page,
    int size
) {

  public static ForensicSearchQuery of(
      String q,
      UUID transactionId,
      UUID accountId,
      String transferStatus,
      String riskDecision,
      Instant requestedFrom,
      Instant requestedTo,
      Integer requestedPage,
      Integer requestedSize,
      Instant now) {
    Instant to = requestedTo != null ? requestedTo : now;
    Instant from = requestedFrom != null
        ? requestedFrom
        : transactionId == null ? to.minus(90, ChronoUnit.DAYS) : Instant.EPOCH;
    if (from.isAfter(to)) {
      throw new BusinessException("INVALID_DATE_RANGE", "from must be before or equal to to");
    }
    return new ForensicSearchQuery(
        trimToNull(q),
        transactionId,
        accountId,
        parseStatus(transferStatus),
        upperToNull(riskDecision),
        from,
        to,
        requestedPage == null ? 0 : Math.max(requestedPage, 0),
        requestedSize == null ? 20 : Math.min(Math.max(requestedSize, 1), 100));
  }

  private static TransferStatus parseStatus(String value) {
    String normalized = upperToNull(value);
    if (normalized == null) {
      return null;
    }
    try {
      return TransferStatus.valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("INVALID_TRANSFER_STATUS", "Unsupported transfer status: " + value);
    }
  }

  private static String upperToNull(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
