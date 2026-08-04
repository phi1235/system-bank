package com.banksystem.transaction.application.query;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin transfer list filters + paging (kept out of controller).
 */
public record AdminTransferListQuery(
    TransferStatus status,
    UUID transferId,
    String q,
    Instant from,
    Instant to,
    int page,
    int size,
    Instant lastCreatedAt) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 5000;
  public static final Instant EPOCH = Instant.EPOCH;
  public static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

  public static AdminTransferListQuery of(
      String status,
      String transferId,
      String q,
      Instant from,
      Instant to,
      Integer page,
      Integer size) {
    return of(status, transferId, q, from, to, page, size, null);
  }

  public static AdminTransferListQuery of(
      String status,
      String transferId,
      String q,
      Instant from,
      Instant to,
      Integer page,
      Integer size,
      Instant lastCreatedAt) {
    Instant fromTs = from == null ? EPOCH : from;
    Instant toTs = to == null ? FAR_FUTURE : to;
    if (fromTs.isAfter(toTs)) {
      throw new BusinessException(
          "INVALID_DATE_RANGE",
          "from must be before or equal to to");
    }

    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_SIZE : size;
    if (s < 1) {
      s = DEFAULT_SIZE;
    }
    if (s > MAX_SIZE) {
      s = MAX_SIZE;
    }

    return new AdminTransferListQuery(
        parseStatus(status),
        parseUuid(transferId),
        blankToNull(q),
        fromTs,
        toTs,
        p,
        s,
        lastCreatedAt);
  }

  public boolean hasLastCreatedAt() {
    return lastCreatedAt != null;
  }

  public boolean hasStatus() {
    return status != null;
  }

  public boolean hasTransferId() {
    return transferId != null;
  }

  public boolean hasQ() {
    return q != null;
  }

  private static String blankToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    return t.isEmpty() ? null : t;
  }

  private static TransferStatus parseStatus(String raw) {
    String t = blankToNull(raw);
    if (t == null) {
      return null;
    }
    try {
      return TransferStatus.valueOf(t.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_STATUS",
          "status must be a valid transfer status");
    }
  }

  private static UUID parseUuid(String raw) {
    String t = blankToNull(raw);
    if (t == null) {
      return null;
    }
    try {
      return UUID.fromString(t);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_TRANSFER_ID",
          "transferId must be a valid UUID");
    }
  }
}
