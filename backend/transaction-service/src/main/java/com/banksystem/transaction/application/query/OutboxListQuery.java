package com.banksystem.transaction.application.query;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.OutboxStatus;
import org.springframework.http.HttpStatus;

/**
 * Admin outbox list filters + paging policy (kept out of controller).
 */
public record OutboxListQuery(OutboxStatus status, int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static OutboxListQuery of(String status, Integer page, Integer size) {
    OutboxStatus st = parseStatus(status);
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_SIZE : size;
    if (s < 1) {
      s = DEFAULT_SIZE;
    }
    if (s > MAX_SIZE) {
      s = MAX_SIZE;
    }
    return new OutboxListQuery(st, p, s);
  }

  private static OutboxStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return OutboxStatus.DEAD;
    }
    try {
      return OutboxStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_STATUS",
          "status must be PENDING, PUBLISHED, or DEAD",
          HttpStatus.BAD_REQUEST);
    }
  }
}
