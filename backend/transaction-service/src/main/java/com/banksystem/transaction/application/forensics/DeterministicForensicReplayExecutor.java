package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.domain.forensics.ForensicReplayScenarioEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeterministicForensicReplayExecutor implements ForensicReplayExecutor {
  private final String deployedCommitSha;
  private final String executionImageSha;
  private final ForensicScenarioService scenarioService;
  private final ForensicJsonSupport jsonSupport;
  private final ForensicReplayScenarioDefinitionValidator definitionValidator;

  public DeterministicForensicReplayExecutor(
      @Value("${bank.forensics.replay.deployed-commit-sha}") String deployedCommitSha,
      @Value("${bank.forensics.replay.execution-image-sha}")
      String executionImageSha,
      ForensicScenarioService scenarioService,
      ForensicJsonSupport jsonSupport,
      ForensicReplayScenarioDefinitionValidator definitionValidator) {
    this.deployedCommitSha = deployedCommitSha.toLowerCase();
    this.executionImageSha = executionImageSha;
    this.scenarioService = scenarioService;
    this.jsonSupport = jsonSupport;
    this.definitionValidator = definitionValidator;
  }

  @Override
  public ReplayExecution execute(
      JsonNode snapshot, String scenarioId, long seed, String targetCommitSha) {
    if (deployedCommitSha.isBlank()
        || !(deployedCommitSha.startsWith(targetCommitSha)
            || targetCommitSha.startsWith(deployedCommitSha))) {
      throw new IllegalStateException("TARGET_COMMIT_NOT_DEPLOYED_IN_REPLAY_RUNTIME");
    }
    ForensicReplayScenarioEntity scenario = scenarioService.requireConfirmed(scenarioId);
    String engine = scenario.getEngineKey();
    if (!"SNAPSHOT_INVARIANT_V1".equals(engine)) {
      throw new IllegalStateException("REPLAY_SCENARIO_NOT_ALLOWLISTED");
    }
    Set<String> before = new LinkedHashSet<>();
    snapshot.path("violations").forEach(node -> before.add(node.path("ruleCode").asText()));
    Set<String> after = evaluate(snapshot);
    applyFaults(after, scenarioId, seed,
        definitionValidator.validate(jsonSupport.deserialize(scenario.getDefinitionJson())));
    List<String> resolved = before.stream().filter(rule -> !after.contains(rule)).toList();
    List<String> introduced = after.stream().filter(rule -> !before.contains(rule)).toList();
    return new ReplayExecution(
        executionImageSha, List.copyOf(before), List.copyOf(after), resolved, introduced,
        after.isEmpty());
  }

  private void applyFaults(
      Set<String> violations,
      String scenarioId,
      long seed,
      ForensicReplayScenarioDefinitionValidator.ScenarioDefinition definition) {
    for (int index = 0; index < definition.faults().size(); index++) {
      ForensicReplayScenarioDefinitionValidator.FaultDefinition fault =
          definition.faults().get(index);
      if (!active(scenarioId, seed, index, fault.probabilityBps())) {
        continue;
      }
      violations.add(switch (fault.type()) {
        case "TIMEOUT" -> "SIM-TIMEOUT-001";
        case "DELAY" -> "SIM-LATENCY-001";
        case "DUPLICATE" -> "INV-DUPLICATE-OUTCOME";
        case "FAIL_BEFORE_COMMIT" -> "SIM-FAIL-BEFORE-COMMIT";
        case "FAIL_AFTER_COMMIT" -> "SIM-FAIL-AFTER-COMMIT";
        case "KAFKA_UNAVAILABLE" -> "SIM-KAFKA-UNAVAILABLE";
        default -> throw new IllegalStateException("Unsupported validated fault type");
      });
    }
  }

  private boolean active(String scenarioId, long seed, int index, int probabilityBps) {
    String digest = jsonSupport.sha256(scenarioId + ":" + seed + ":" + index);
    long bucket = Long.parseUnsignedLong(digest.substring(0, 8), 16) % 10000;
    return bucket < probabilityBps;
  }

  private Set<String> evaluate(JsonNode snapshot) {
    Set<String> violations = new LinkedHashSet<>();
    for (JsonNode journal : snapshot.path("ledgerEvidence").path("journals")) {
      BigDecimal debit = BigDecimal.ZERO;
      BigDecimal credit = BigDecimal.ZERO;
      int postings = 0;
      boolean currencyMismatch = false;
      String currency = journal.path("currency").asText();
      for (JsonNode posting : journal.path("postings")) {
        postings++;
        BigDecimal amount = posting.path("amount").decimalValue();
        if ("DEBIT".equals(posting.path("side").asText())) debit = debit.add(amount);
        if ("CREDIT".equals(posting.path("side").asText())) credit = credit.add(amount);
        currencyMismatch |= !currency.equals(posting.path("currency").asText());
      }
      if (postings < 2 || debit.compareTo(credit) != 0) violations.add("INV-JOURNAL-001");
      if (currencyMismatch) violations.add("INV-CURRENCY-001");
      if ("REVERSAL".equals(journal.path("journalType").asText())
          && journal.path("reversalOfJournalId").isNull()) {
        violations.add("INV-REVERSAL-001");
      }
    }
    return violations;
  }
}
