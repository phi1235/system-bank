package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Fixed, read-only tool registry exposed to the AI boundary. */
@Component
class ForensicCopilotToolRegistry {
  private static final String SCHEMA_VERSION = "1.0";
  private static final List<ToolDefinition> DEFINITIONS = List.of(
      definition("get_investigation_summary", "transactionId", "InvestigationItemResponse"),
      definition("get_evidence_timeline", "transactionId", "TimelineEvidenceResponse[]"),
      definition("get_financial_journal", "journalId", "LedgerJournalEvidenceResponse[]"),
      definition("get_verification_findings", "transactionId", "FinancialViolationResponse[]"),
      definition("get_causal_graph", "transactionId", "CausalGraphResponse"));

  List<String> names() {
    return DEFINITIONS.stream().map(ToolDefinition::name).toList();
  }

  ToolContext collect(InvestigationDetailResponse evidence) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    outputs.put("get_investigation_summary", evidence.transaction());
    outputs.put("get_evidence_timeline", evidence.timeline());
    outputs.put("get_financial_journal", evidence.ledgerEvidence().journals());
    outputs.put("get_verification_findings", evidence.violations());
    outputs.put("get_causal_graph", evidence.causalGraph());
    return new ToolContext(DEFINITIONS, Map.copyOf(outputs));
  }

  private static ToolDefinition definition(
      String name, String requiredInput, String outputType) {
    Map<String, Object> inputSchema = Map.of(
        "type", "object",
        "additionalProperties", false,
        "required", List.of(requiredInput),
        "properties", Map.of(requiredInput, Map.of("type", "string")));
    Map<String, Object> outputSchema = Map.of(
        "type", "object",
        "javaContract", outputType,
        "additionalProperties", false);
    return new ToolDefinition(name, SCHEMA_VERSION, inputSchema, outputSchema, true);
  }

  record ToolDefinition(
      String name,
      String schemaVersion,
      Map<String, Object> inputSchema,
      Map<String, Object> outputSchema,
      boolean readOnly) {}

  record ToolContext(List<ToolDefinition> definitions, Map<String, Object> outputs) {}
}
