package com.banksystem.common.api;

import java.time.Instant;

public record Meta(
    String correlationId,
    Instant timestamp
) {
  public static Meta now() {
    return new Meta(null, Instant.now());
  }

  public static Meta now(String correlationId) {
    return new Meta(correlationId, Instant.now());
  }
}
