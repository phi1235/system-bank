package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicBusinessNarrativeResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseHistoryResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RemediationActionResponse;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ForensicCaseMapper {
  private final ForensicJsonSupport jsonSupport;

  ForensicCaseMapper(ForensicJsonSupport jsonSupport) {
    this.jsonSupport = jsonSupport;
  }

  ForensicCaseResponse toResponse(ForensicCaseEntity entity) {
    List<RemediationActionResponse> actions = parseRemediationActions(entity.getRemediationJson());
    ForensicBusinessNarrativeResponse narrative = parseNarrative(entity.getNarrativeJson());
    return new ForensicCaseResponse(
        entity.getId().toString(), entity.getCaseNumber(), string(entity.getTransactionId()),
        string(entity.getAccountId()), entity.getSourceType(), entity.getSourceReferenceId(),
        entity.getStatus().name(),
        entity.getInvestigationStage() == null ? null : entity.getInvestigationStage().name(),
        entity.getPriority().name(), entity.getTitle(), entity.getSummary(),
        entity.getEvidenceCompleteness().name(), string(entity.getAssignedTo()),
        string(entity.getCreatedBy()), string(entity.getSubmittedBy()), string(entity.getCheckerId()),
        entity.getResolutionCode() == null ? null : entity.getResolutionCode().name(),
        entity.getResolutionNote(), entity.getRemediationStatus(), actions, narrative, entity.isSystemic(),
        entity.getInvestigationCycle(), entity.getVersion(), entity.getSubmittedAt(), entity.getResolvedAt(),
        entity.getCreatedAt(), entity.getUpdatedAt());
  }

  ForensicCaseHistoryResponse toHistory(ForensicCaseHistoryEntity entity) {
    return new ForensicCaseHistoryResponse(
        entity.getId().toString(), entity.getActorUserId().toString(), entity.getAction(),
        entity.getFromStatus(), entity.getToStatus(), entity.getDecision(), entity.getNote(),
        entity.getCaseVersion(), entity.getCreatedAt());
  }

  private String string(Object value) { return value == null ? null : value.toString(); }

  @SuppressWarnings("unchecked")
  private List<RemediationActionResponse> parseRemediationActions(String json) {
    if (json == null || json.isBlank()) return List.of();
    Object parsed = jsonSupport.deserializeAny(json);
    if (parsed instanceof List<?> list) {
      return list.stream()
          .filter(item -> item instanceof Map)
          .map(item -> {
            Map<String, Object> map = (Map<String, Object>) item;
            String completedAtStr = map.get("completedAt") == null ? null : map.get("completedAt").toString();
            Instant completedAt = completedAtStr == null ? null : Instant.parse(completedAtStr);
            return new RemediationActionResponse(
                string(map.get("actionType")), string(map.get("referenceId")),
                string(map.get("description")),
                Boolean.TRUE.equals(map.get("completed")), completedAt);
          }).toList();
    }
    return List.of();
  }

  @SuppressWarnings("unchecked")
  private ForensicBusinessNarrativeResponse parseNarrative(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      Object parsed = jsonSupport.deserializeAny(json);
      if (parsed instanceof Map<?, ?> map) {
        String summary = string(map.get("summary"));
        String impactAnalysis = string(map.get("impactAnalysis"));
        String rootCauseNarrative = string(map.get("rootCauseNarrative"));
        String suggestedRemediationNarrative = string(map.get("suggestedRemediationNarrative"));
        String generatedBy = string(map.get("generatedBy"));
        String generatedAtStr = string(map.get("generatedAt"));
        Instant generatedAt = generatedAtStr == null ? null : Instant.parse(generatedAtStr);
        List<String> evidenceKeys = List.of();
        if (map.get("groundedEvidenceKeys") instanceof List<?> list) {
          evidenceKeys = list.stream().map(Object::toString).toList();
        }
        return new ForensicBusinessNarrativeResponse(
            summary, impactAnalysis, rootCauseNarrative, suggestedRemediationNarrative,
            evidenceKeys, generatedBy, generatedAt);
      }
    } catch (Exception ignored) {
      // Return null on parsing failure
    }
    return null;
  }
}
