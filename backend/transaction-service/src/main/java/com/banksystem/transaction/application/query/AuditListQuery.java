package com.banksystem.transaction.application.query;

import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Admin audit list filters + paging policy (kept out of controller).
 */
public record AuditListQuery(
    String action,
    String resourceType,
    UUID actorUserId,
    String resourceId,
    Instant from,
    Instant to,
    int page,
    int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 5000;
  public static final Instant EPOCH = Instant.EPOCH;
  public static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

  public static AuditListQuery of(
      String action,
      String resourceType,
      String actorUserId,
      String resourceId,
      Instant from,
      Instant to,
      Integer page,
      Integer size) {
    Instant fromTs = from == null ? EPOCH : from;
    Instant toTs = to == null ? FAR_FUTURE : to;
    if (fromTs.isAfter(toTs)) {
      throw new BusinessException(
          "INVALID_DATE_RANGE",
          "from must be before or equal to to",
          HttpStatus.BAD_REQUEST);
    }

    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_SIZE : size;
    if (s < 1) {
      s = DEFAULT_SIZE;
    }
    if (s > MAX_SIZE) {
      s = MAX_SIZE;
    }

    return new AuditListQuery(
        blankToNull(action),
        blankToNull(resourceType),
        parseUuid(actorUserId),
        blankToNull(resourceId),
        fromTs,
        toTs,
        p,
        s);
  }

  public boolean hasAction() {
    return action != null;
  }

  public boolean hasResourceType() {
    return resourceType != null;
  }

  public boolean hasActor() {
    return actorUserId != null;
  }

  public boolean hasResourceId() {
    return resourceId != null;
  }

  private static String blankToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    return t.isEmpty() ? null : t;
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
          "INVALID_ACTOR_ID",
          "actorUserId must be a valid UUID",
          HttpStatus.BAD_REQUEST);
    }
  }
}
