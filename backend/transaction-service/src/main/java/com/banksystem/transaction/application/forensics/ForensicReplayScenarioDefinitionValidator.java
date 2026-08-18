package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates the versioned, provider-independent replay scenario contract. */
@Component
class ForensicReplayScenarioDefinitionValidator {
  private static final Set<String> FAULT_TYPES = Set.of(
      "TIMEOUT", "DELAY", "DUPLICATE", "FAIL_BEFORE_COMMIT", "FAIL_AFTER_COMMIT",
      "KAFKA_UNAVAILABLE");

  ScenarioDefinition validate(Map<String, Object> definition) {
    int schemaVersion = integer(definition.get("schemaVersion"), "schemaVersion", 1, 1);
    Object rawFaults = definition.get("faults");
    if (!(rawFaults instanceof List<?> faultItems) || faultItems.size() > 20) {
      throw invalid("faults must be an array with at most 20 entries");
    }
    List<FaultDefinition> faults = new ArrayList<>();
    for (int index = 0; index < faultItems.size(); index++) {
      Object item = faultItems.get(index);
      if (!(item instanceof Map<?, ?> rawFault)) {
        throw invalid("faults[" + index + "] must be an object");
      }
      faults.add(fault(rawFault, index));
    }
    return new ScenarioDefinition(schemaVersion, List.copyOf(faults));
  }

  Set<String> faultTypes() {
    return FAULT_TYPES;
  }

  private FaultDefinition fault(Map<?, ?> raw, int index) {
    String type = text(raw.get("type"), "faults[" + index + "].type")
        .toUpperCase(Locale.ROOT);
    if (!FAULT_TYPES.contains(type)) {
      throw invalid("Unsupported fault type: " + type);
    }
    String target = text(raw.get("target"), "faults[" + index + "].target");
    int occurrence = optionalInteger(raw.get("occurrence"), "occurrence", 1, 1000, 1);
    int probabilityBps = optionalInteger(
        raw.get("probabilityBps"), "probabilityBps", 0, 10000, 10000);
    int delayMs = optionalInteger(raw.get("delayMs"), "delayMs", 0, 60000, 0);
    if (!"DELAY".equals(type) && delayMs != 0) {
      throw invalid("delayMs is only valid for DELAY faults");
    }
    if ("DELAY".equals(type) && delayMs == 0) {
      throw invalid("DELAY faults require delayMs greater than zero");
    }
    return new FaultDefinition(type, target, occurrence, probabilityBps, delayMs);
  }

  private int optionalInteger(
      Object value, String field, int minimum, int maximum, int defaultValue) {
    return value == null ? defaultValue : integer(value, field, minimum, maximum);
  }

  private int integer(Object value, String field, int minimum, int maximum) {
    if (!(value instanceof Number number)) {
      throw invalid(field + " must be an integer");
    }
    int parsed = number.intValue();
    if (number.doubleValue() != parsed || parsed < minimum || parsed > maximum) {
      throw invalid(field + " is outside the allowed range");
    }
    return parsed;
  }

  private String text(Object value, String field) {
    if (!(value instanceof String text) || text.isBlank() || text.length() > 120) {
      throw invalid(field + " must be a non-blank string up to 120 characters");
    }
    return text.trim();
  }

  private BusinessException invalid(String detail) {
    return new BusinessException("FORENSIC_SCENARIO_INVALID", detail);
  }

  record ScenarioDefinition(int schemaVersion, List<FaultDefinition> faults) {}

  record FaultDefinition(
      String type, String target, int occurrence, int probabilityBps, int delayMs) {}
}
