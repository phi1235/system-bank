package com.banksystem.transaction.infrastructure.forensics;

import com.banksystem.transaction.application.forensics.ForensicTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MicrometerForensicTelemetry implements ForensicTelemetry {
  private final MeterRegistry registry;

  public MicrometerForensicTelemetry(MeterRegistry registry) { this.registry = registry; }

  @Override
  public void verification(String mode, String outcome, long durationNanos) {
    registry.counter("forensic.verification.runs", "mode", mode, "outcome", outcome).increment();
    Timer.builder("forensic.verification.duration").tag("mode", mode)
        .register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void graphCache(boolean hit, String completeness) {
    registry.counter("forensic.causal.graph.cache", "result", hit ? "hit" : "miss",
        "completeness", completeness).increment();
  }

  @Override
  public void batch(int processed, boolean success) {
    registry.counter("forensic.verification.batch", "outcome", success ? "success" : "failed")
        .increment();
    registry.summary("forensic.verification.batch.processed").record(processed);
  }

  @Override
  public void copilot(String status) {
    registry.counter("forensic.copilot.answers", "status", status).increment();
  }
}
