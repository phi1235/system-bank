package com.banksystem.transaction.application.forensics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ForensicReplayExecutor {
  ReplayExecution execute(
      JsonNode sanitizedSnapshot, String scenarioId, long seed, String targetCommitSha);

  record ReplayExecution(
      String executionImageSha,
      List<String> beforeViolations,
      List<String> afterViolations,
      List<String> resolvedViolations,
      List<String> newViolations,
      boolean passed) {}
}
