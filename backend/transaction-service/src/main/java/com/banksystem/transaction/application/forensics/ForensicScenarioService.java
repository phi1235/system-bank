package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ForensicScenarioDtos.CreateScenarioRequest;
import com.banksystem.transaction.api.dto.ForensicScenarioDtos.ScenarioResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicReplayScenarioEntity;
import com.banksystem.transaction.domain.forensics.ForensicReplayScenarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicScenarioService {
  private static final List<String> ENGINES = List.of("SNAPSHOT_INVARIANT_V1");
  private final ForensicReplayScenarioRepository repository;
  private final ForensicJsonSupport jsonSupport;
  private final AuditLogRepository auditRepository;
  private final Clock clock;
  private final ForensicReplayScenarioDefinitionValidator definitionValidator;

  public ForensicScenarioService(
      ForensicReplayScenarioRepository repository, ForensicJsonSupport jsonSupport,
      AuditLogRepository auditRepository, Clock clock,
      ForensicReplayScenarioDefinitionValidator definitionValidator) {
    this.repository = repository;
    this.jsonSupport = jsonSupport;
    this.auditRepository = auditRepository;
    this.clock = clock;
    this.definitionValidator = definitionValidator;
  }

  @Transactional(readOnly = true)
  public List<ScenarioResponse> confirmed() {
    return repository.findByStatusOrderByUpdatedAtDesc("CONFIRMED").stream().map(this::response).toList();
  }

  @Transactional(readOnly = true)
  public List<ScenarioResponse> all() {
    return repository.findAll().stream().map(this::response).toList();
  }

  public List<String> engines() { return ENGINES; }

  public List<String> faultTypes() {
    return definitionValidator.faultTypes().stream().sorted().toList();
  }

  @Transactional
  public ScenarioResponse create(CreateScenarioRequest request, UUID actor) {
    String engine = request.engineKey().trim().toUpperCase(Locale.ROOT);
    if (!ENGINES.contains(engine)) {
      throw new BusinessException("FORENSIC_SCENARIO_ENGINE_NOT_ALLOWED", "Scenario engine is not allowlisted");
    }
    if (!request.sanitized()) {
      throw new BusinessException("FORENSIC_SCENARIO_NOT_SANITIZED", "Scenario dataset must be sanitized");
    }
    definitionValidator.validate(request.definition());
    Instant now = clock.instant();
    repository.insertDraft(request.scenarioId(), request.title().trim(), engine,
        request.sourceIncidentId().trim(), request.sourceEvidenceRef().trim(),
        jsonSupport.serialize(request.definition()), true, actor, now);
    auditRepository.save(AuditLogEntity.of(actor, "FORENSIC_SCENARIO_CREATED",
        "FORENSIC_SCENARIO", request.scenarioId(), null, "engine=" + engine));
    return response(require(request.scenarioId()));
  }

  @Transactional
  public ScenarioResponse confirm(String id, long version, UUID actor) {
    require(id);
    if (repository.confirm(id, actor, clock.instant(), version) == 0) {
      throw new BusinessException("FORENSIC_MAKER_CHECKER_REQUIRED",
          "Scenario must be sanitized, unchanged and confirmed by a different user");
    }
    auditRepository.save(AuditLogEntity.of(actor, "FORENSIC_SCENARIO_CONFIRMED",
        "FORENSIC_SCENARIO", id, null, "version=" + version));
    return response(require(id));
  }

  @Transactional
  public void generateFromCase(ForensicCaseEntity forensicCase, UUID actor) {
    String scenarioId = "SYSTEMIC-" + forensicCase.getCaseNumber();
    if (repository.findById(scenarioId).isPresent()) {
      return;
    }
    String title = "Regression: " + forensicCase.getTitle();
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("schemaVersion", 1);
    definition.put("sourceType", "AUTO_GENERATED");
    definition.put("caseNumber", forensicCase.getCaseNumber());
    definition.put("transactionId", forensicCase.getTransactionId() == null
        ? null : forensicCase.getTransactionId().toString());
    definition.put("faults", List.of());
    Instant now = clock.instant();
    repository.insertDraft(scenarioId, title, "SNAPSHOT_INVARIANT_V1",
        forensicCase.getId().toString(),
        forensicCase.getTransactionId() == null ? "" : forensicCase.getTransactionId().toString(),
        jsonSupport.serialize(definition), true, actor, now);
    auditRepository.save(AuditLogEntity.of(actor, "FORENSIC_SCENARIO_AUTO_GENERATED",
        "FORENSIC_SCENARIO", scenarioId, null,
        "caseId=" + forensicCase.getId() + ",caseNumber=" + forensicCase.getCaseNumber()));
  }

  public ForensicReplayScenarioEntity requireConfirmed(String id) {
    return repository.findByScenarioIdAndStatus(id, "CONFIRMED").orElseThrow(() ->
        new BusinessException("FORENSIC_SCENARIO_NOT_CONFIRMED", "Replay scenario is not confirmed"));
  }

  private ForensicReplayScenarioEntity require(String id) {
    return repository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_SCENARIO_NOT_FOUND", "Replay scenario not found"));
  }

  private ScenarioResponse response(ForensicReplayScenarioEntity entity) {
    return new ScenarioResponse(entity.getScenarioId(), entity.getTitle(), entity.getEngineKey(),
        entity.getSourceIncidentId(), entity.getSourceEvidenceRef(),
        jsonSupport.deserialize(entity.getDefinitionJson()), entity.isSanitized(), entity.getStatus(),
        text(entity.getCreatedBy()), text(entity.getConfirmedBy()), entity.getConfirmedAt(),
        entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
  }

  private String text(UUID value) { return value == null ? null : value.toString(); }
}
