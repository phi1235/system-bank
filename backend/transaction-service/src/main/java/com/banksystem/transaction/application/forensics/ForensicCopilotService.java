package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseDetailResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotAnswerResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotCitationResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotProviderHealthResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotSessionResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicCopilotMessageEntity;
import com.banksystem.transaction.domain.forensics.ForensicCopilotMessageRepository;
import com.banksystem.transaction.domain.forensics.ForensicCopilotSessionEntity;
import com.banksystem.transaction.domain.forensics.ForensicCopilotSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicCopilotService {
  private final ForensicCopilotSessionRepository sessionRepository;
  private final ForensicCopilotMessageRepository messageRepository;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final ForensicCaseQueryService caseQueryService;
  private final ForensicEvidenceSanitizer sanitizer;
  private final ForensicArtifactCodec codec;
  private final ForensicAiProvider provider;
  private final AuditLogRepository auditRepository;
  private final Clock clock;
  private final Duration retention;
  private final ForensicPromptSanitizer promptSanitizer;
  private final ForensicCopilotClaimValidator claimValidator;
  private final int maxMessagesPerSession;
  private final ForensicTelemetry telemetry;
  private final ForensicCopilotToolRegistry toolRegistry;

  public ForensicCopilotService(
      ForensicCopilotSessionRepository sessionRepository,
      ForensicCopilotMessageRepository messageRepository,
      ForensicInvestigationQueryService investigationQueryService,
      ForensicCaseQueryService caseQueryService,
      ForensicEvidenceSanitizer sanitizer,
      ForensicArtifactCodec codec,
      ForensicAiProvider provider,
      AuditLogRepository auditRepository,
      Clock clock,
      @Value("${bank.forensics.ai.session-retention}") Duration retention,
      ForensicPromptSanitizer promptSanitizer,
      ForensicCopilotClaimValidator claimValidator,
      @Value("${bank.forensics.ai.max-messages-per-session}") int maxMessagesPerSession,
      ForensicTelemetry telemetry,
      ForensicCopilotToolRegistry toolRegistry) {
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.investigationQueryService = investigationQueryService;
    this.caseQueryService = caseQueryService;
    this.sanitizer = sanitizer;
    this.codec = codec;
    this.provider = provider;
    this.auditRepository = auditRepository;
    this.clock = clock;
    this.retention = retention;
    this.promptSanitizer = promptSanitizer;
    this.claimValidator = claimValidator;
    this.maxMessagesPerSession = maxMessagesPerSession;
    this.telemetry = telemetry;
    this.toolRegistry = toolRegistry;
  }

  @Transactional
  public CopilotSessionResponse create(UUID transactionId, UUID caseId, GatewayUser actor) {
    if (transactionId == null && caseId == null) throw new BusinessException("COPILOT_SCOPE_REQUIRED", "transactionId or caseId is required");
    Instant now = clock.instant();
    ForensicCopilotSessionEntity entity = ForensicCopilotSessionEntity.active(UUID.randomUUID(), transactionId, caseId, actor.userId(), now, now.plus(retention));
    sessionRepository.save(entity);
    auditRepository.save(AuditLogEntity.of(actor.userId(), "FORENSIC_COPILOT_SESSION_CREATED", "COPILOT_SESSION", entity.getId().toString(), "unknown", "transactionId=" + transactionId + ",caseId=" + caseId));
    return toSession(entity);
  }

  @Transactional
  public CopilotAnswerResponse ask(UUID sessionId, String question, GatewayUser actor) {
    ForensicCopilotSessionEntity session = requireOwned(sessionId, actor.userId());
    Instant now = clock.instant();
    if (!"ACTIVE".equals(session.getStatus()) || !session.getExpiresAt().isAfter(now)) throw new BusinessException("COPILOT_SESSION_EXPIRED", "Copilot session has expired");
    if (messageRepository.countBySessionId(sessionId) >= maxMessagesPerSession * 2L) {
      throw new BusinessException("COPILOT_SESSION_BUDGET_EXCEEDED", "Copilot session message budget is exhausted");
    }
    UUID transactionId = resolveTransactionId(session);
    String safeQuestion = promptSanitizer.sanitize(question.trim());
    messageRepository.save(ForensicCopilotMessageEntity.of(sessionId, "USER", safeQuestion, null, "[]", "[]", "{}", now));
    if (transactionId == null) return persistAnswer(session, actor, "No transaction evidence is linked to this case.", "INSUFFICIENT_EVIDENCE", List.of(), Map.of("grounded", false), now);
    InvestigationDetailResponse evidence = investigationQueryService.get(transactionId);
    List<CopilotCitationResponse> citations = new ArrayList<>(citations(evidence));

    String caseContextPrompt = "";
    if (session.getCaseId() != null) {
      try {
        ForensicCaseDetailResponse caseDetail = caseQueryService.get(session.getCaseId());
        if (caseDetail != null && caseDetail.forensicCase() != null) {
          var fc = caseDetail.forensicCase();
          citations.add(new CopilotCitationResponse("CASE", fc.id(), fc.caseNumber()));

          StringBuilder caseCtx = new StringBuilder("\n=== CASE CONTEXT ===\n");
          caseCtx.append("Case Number: ").append(fc.caseNumber()).append("\n");
          caseCtx.append("Status: ").append(fc.status());
          if (fc.investigationStage() != null) {
            caseCtx.append(" | Stage: ").append(fc.investigationStage());
          }
          caseCtx.append(" | Priority: ").append(fc.priority()).append("\n");
          caseCtx.append("Title: ").append(fc.title()).append("\n");
          if (fc.businessNarrative() != null) {
            var n = fc.businessNarrative();
            caseCtx.append("Business Narrative:\n");
            caseCtx.append("- Summary: ").append(n.summary()).append("\n");
            caseCtx.append("- Financial Impact: ").append(n.impactAnalysis()).append("\n");
            caseCtx.append("- Root Cause: ").append(n.rootCauseNarrative()).append("\n");
            caseCtx.append("- Suggested Remediation: ").append(n.suggestedRemediationNarrative()).append("\n");
          } else if (fc.summary() != null && !fc.summary().isBlank()) {
            caseCtx.append("Summary: ").append(fc.summary()).append("\n");
          }
          caseCtx.append("====================\n");
          caseContextPrompt = caseCtx.toString();
        }
      } catch (Exception ignored) {
        // Fallback to evidence without case context if case retrieval fails
      }
    }

    Map<String, Object> validation = new LinkedHashMap<>();
    validation.put("grounded", !citations.isEmpty());
    validation.put("writeToolsAvailable", false);
    validation.put("evidenceCompleteness", evidence.evidenceCompleteness());
    String answer;
    String status;
    try {
      ForensicCopilotToolRegistry.ToolContext toolContext = toolRegistry.collect(evidence);
      answer = provider.complete(systemPrompt(), "Question: " + safeQuestion
          + caseContextPrompt
          + "\nAllowlisted tool schemas and sanitized outputs:\n"
          + new String(codec.write(sanitizer.sanitize(toolContext)), StandardCharsets.UTF_8));

      // NOTE: Although case context and business narrative inputs have been sanitized and grounded,
      // the AI LLM output generated below MUST STILL BE STRICTLY VALIDATED by claimValidator
      // to guarantee that no hallucinated monetary amounts or fabricated IDs reach the end user.
      ForensicCopilotClaimValidator.ValidationResult result =
          claimValidator.validate(answer, evidence, citations);
      validation.put("providerValidated", result.valid());
      validation.put("claimValidation", result.reason());
      if (!result.valid()) {
        answer = rawFallback(evidence);
        status = "RAW_FALLBACK";
      } else {
        status = "ANSWERED";
      }
    } catch (BusinessException exception) {
      answer = rawFallback(evidence);
      status = citations.isEmpty() ? "INSUFFICIENT_EVIDENCE" : "RAW_FALLBACK";
      validation.put("providerValidated", false);
      validation.put("fallbackReason", exception.getCode());
    }
    return persistAnswer(session, actor, answer, status, citations, validation, now);
  }

  public CopilotProviderHealthResponse health() {
    ForensicAiProvider.ProviderHealth health = provider.health();
    return new CopilotProviderHealthResponse(health.enabled(), health.configured(), health.provider(), health.model(), health.status());
  }

  private CopilotAnswerResponse persistAnswer(ForensicCopilotSessionEntity session, GatewayUser actor, String answer, String status, List<CopilotCitationResponse> citations, Map<String, Object> validation, Instant now) {
    List<String> readTools = toolRegistry.names();
    ForensicCopilotMessageEntity message = ForensicCopilotMessageEntity.of(
        session.getId(), "ASSISTANT", answer, status, json(readTools), json(citations),
        json(validation), now);
    messageRepository.save(message);
    session.touch(now);
    sessionRepository.save(session);
    auditRepository.save(AuditLogEntity.of(actor.userId(), "FORENSIC_COPILOT_ANSWERED", "COPILOT_SESSION", session.getId().toString(), "unknown", "status=" + status + ",citations=" + citations.size()));
    telemetry.copilot(status);
    return new CopilotAnswerResponse(
        message.getId(), answer, status, readTools, citations, validation, now);
  }

  private UUID resolveTransactionId(ForensicCopilotSessionEntity session) {
    if (session.getTransactionId() != null) return session.getTransactionId();
    ForensicCaseDetailResponse forensicCase = caseQueryService.get(session.getCaseId());
    String transactionId = forensicCase.forensicCase().transactionId();
    return transactionId == null ? null : UUID.fromString(transactionId);
  }

  private List<CopilotCitationResponse> citations(InvestigationDetailResponse evidence) {
    List<CopilotCitationResponse> citations = new ArrayList<>();
    citations.add(new CopilotCitationResponse("TRANSFER", evidence.transaction().transactionId(), evidence.transaction().status()));
    evidence.ledgerEvidence().journals().forEach(journal -> citations.add(new CopilotCitationResponse("JOURNAL", journal.id(), journal.journalType())));
    evidence.violations().forEach(violation -> violation.evidenceIds().forEach(id -> citations.add(new CopilotCitationResponse("VIOLATION", id, violation.ruleCode()))));
    return List.copyOf(citations);
  }

  private String rawFallback(InvestigationDetailResponse evidence) {
    return "Transaction " + evidence.transaction().transactionId() + " is " + evidence.transaction().status() + ". Primary signal: " + evidence.transaction().primarySignal() + ". Financial violations: " + evidence.violations().size() + ". Evidence completeness: " + evidence.evidenceCompleteness() + ".";
  }

  private String systemPrompt() {
    return "You are a read-only bank financial forensics assistant. Use only supplied sanitized evidence. Never claim that you changed a ledger, case, rule, transaction, or production system. State uncertainty. Every factual conclusion must cite at least one supplied durable evidence ID verbatim. Never invent IDs, amounts or currencies.";
  }

  private ForensicCopilotSessionEntity requireOwned(UUID id, UUID actor) {
    ForensicCopilotSessionEntity session = sessionRepository.findById(id).orElseThrow(() -> new BusinessException("COPILOT_SESSION_NOT_FOUND", "Copilot session not found"));
    if (!session.getCreatedBy().equals(actor)) throw new BusinessException("FORBIDDEN_FORENSICS_ACCESS", "Copilot session belongs to another user");
    return session;
  }

  private CopilotSessionResponse toSession(ForensicCopilotSessionEntity entity) {
    return new CopilotSessionResponse(entity.getId(), entity.getTransactionId(), entity.getCaseId(), entity.getStatus(), entity.getCreatedAt(), entity.getExpiresAt());
  }

  private String json(Object value) { return new String(codec.write(value), StandardCharsets.UTF_8); }
}
