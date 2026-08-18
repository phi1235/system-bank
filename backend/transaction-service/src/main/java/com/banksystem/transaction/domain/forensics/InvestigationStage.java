package com.banksystem.transaction.domain.forensics;

public enum InvestigationStage {
  INITIALIZED,
  VIOLATION_DETECTED,
  CAUSAL_GRAPH_ATTACHED,
  ROOT_CAUSE_CONFIRMED,
  REPLAY_VERIFIED,
  INVESTIGATION_CONCLUDED
}
