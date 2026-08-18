package com.banksystem.transaction.application.forensics;

public interface ForensicAiProvider {
  ProviderHealth health();
  String complete(String systemPrompt, String evidencePrompt);
  record ProviderHealth(boolean enabled, boolean configured, String provider, String model, String status) {}
}
