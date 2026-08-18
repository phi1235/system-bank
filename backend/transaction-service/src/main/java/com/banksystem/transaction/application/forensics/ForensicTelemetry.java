package com.banksystem.transaction.application.forensics;

public interface ForensicTelemetry {
  void verification(String mode, String outcome, long durationNanos);
  void graphCache(boolean hit, String completeness);
  void batch(int processed, boolean success);
  void copilot(String status);
}
