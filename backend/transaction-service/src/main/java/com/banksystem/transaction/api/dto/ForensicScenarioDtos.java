package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public final class ForensicScenarioDtos {
  private ForensicScenarioDtos() {}

  public record CreateScenarioRequest(
      @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,99}$") String scenarioId,
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 80) String engineKey,
      @NotBlank @Size(max = 100) String sourceIncidentId,
      @NotBlank @Size(max = 200) String sourceEvidenceRef,
      @NotNull Map<String, Object> definition,
      boolean sanitized) {}

  public record ConfirmScenarioRequest(@Min(0) long expectedVersion) {}

  public record ScenarioResponse(
      String scenarioId, String title, String engineKey, String sourceIncidentId,
      String sourceEvidenceRef, Map<String, Object> definition, boolean sanitized,
      String status, String createdBy, String confirmedBy, Instant confirmedAt,
      Instant createdAt, Instant updatedAt, long version) {}
}
