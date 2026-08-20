package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.forensics.ForensicCasePriority;
import com.banksystem.transaction.domain.forensics.ForensicCaseStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

public record ForensicCaseSearchQuery(
    String q,
    ForensicCaseStatus status,
    ForensicCasePriority priority,
    UUID assignedTo,
    UUID transactionId,
    Instant from,
    Instant to,
    int page,
    int size) {

  public static ForensicCaseSearchQuery of(
      String q,
      String status,
      String priority,
      UUID assignedTo,
      UUID transactionId,
      Instant from,
      Instant to,
      Integer page,
      Integer size,
      Instant now) {
    Instant upper = to == null ? now : to;
    Instant lower = from == null ? upper.minus(90, ChronoUnit.DAYS) : from;
    if (lower.isAfter(upper)) {
      throw new BusinessException("INVALID_DATE_RANGE", "from must be before or equal to to");
    }
    return new ForensicCaseSearchQuery(
        trim(q),
        parse(status, ForensicCaseStatus.class, "INVALID_FORENSIC_CASE_STATUS"),
        parse(priority, ForensicCasePriority.class, "INVALID_FORENSIC_CASE_PRIORITY"),
        assignedTo,
        transactionId,
        lower,
        upper,
        page == null ? 0 : Math.max(page, 0),
        size == null ? 20 : Math.min(Math.max(size, 1), 100));
  }

  private static <E extends Enum<E>> E parse(String value, Class<E> type, String errorCode) {
    String normalized = trim(value);
    if (normalized == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, normalized.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(errorCode, "Unsupported value: " + value);
    }
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
