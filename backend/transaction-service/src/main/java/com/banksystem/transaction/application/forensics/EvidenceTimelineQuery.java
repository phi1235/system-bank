package com.banksystem.transaction.application.forensics;

import java.util.Locale;

public record EvidenceTimelineQuery(String source, int page, int size) {
  public static EvidenceTimelineQuery of(String source, Integer page, Integer size) {
    String normalized = source == null || source.isBlank()
        ? null : source.trim().toUpperCase(Locale.ROOT);
    return new EvidenceTimelineQuery(
        normalized,
        page == null ? 0 : Math.max(page, 0),
        size == null ? 20 : Math.min(Math.max(size, 1), 100));
  }
}
