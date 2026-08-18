package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.CreateRemediationProposalRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.RejectRemediationProposalRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.RemediationProposalResponse;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.UpdateRemediationProposalRequest;
import com.banksystem.transaction.domain.forensics.CanonicalProposalPayload;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import com.banksystem.transaction.domain.forensics.ProposalFingerprintSupport;
import com.banksystem.transaction.domain.forensics.RemediationOutboxEntity;
import com.banksystem.transaction.domain.forensics.RemediationOutboxRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalEntity;
import com.banksystem.transaction.domain.forensics.RemediationProposalRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemediationProposalCommandService {

  private final RemediationProposalRepository proposalRepository;
  private final ForensicCaseRepository caseRepository;
  private final RemediationOutboxRepository outboxRepository;
  private final ProposalFingerprintSupport fingerprintSupport;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public RemediationProposalCommandService(
      RemediationProposalRepository proposalRepository,
      ForensicCaseRepository caseRepository,
      RemediationOutboxRepository outboxRepository,
      ProposalFingerprintSupport fingerprintSupport,
      Clock clock,
      ObjectMapper objectMapper) {
    this.proposalRepository = proposalRepository;
    this.caseRepository = caseRepository;
    this.outboxRepository = outboxRepository;
    this.fingerprintSupport = fingerprintSupport;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public RemediationProposalResponse createDraft(CreateRemediationProposalRequest request, UUID proposedBy) {
    ForensicCaseEntity forensicCase = caseRepository.findById(request.caseId())
        .orElseThrow(() -> new BusinessException("CASE_NOT_FOUND", "Forensic Case not found", HttpStatus.NOT_FOUND));

    Instant now = clock.instant();
    RemediationProposalEntity proposal = RemediationProposalEntity.createDraft(
        UUID.randomUUID(),
        request.caseId(),
        forensicCase.getInvestigationCycle(),
        request.sourceTransactionId(),
        request.targetAccountId(),
        request.direction(),
        request.amount(),
        request.currency(),
        request.reason(),
        proposedBy,
        now);

    proposalRepository.save(proposal);
    return toResponse(proposal);
  }

  @Transactional
  public RemediationProposalResponse updateDraft(
      UUID proposalId, UpdateRemediationProposalRequest request, UUID proposedBy) {
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found", HttpStatus.NOT_FOUND));

    if (proposal.getVersion() != request.expectedVersion()) {
      throw new BusinessException("STALE_PROPOSAL_VERSION", "Proposal has been updated by another transaction", HttpStatus.CONFLICT);
    }
    if (!proposal.getProposedBy().equals(proposedBy)) {
      throw new BusinessException("PROPOSAL_FORBIDDEN", "Only the proposer can update this draft", HttpStatus.FORBIDDEN);
    }
    if (proposal.getStatus() != RemediationProposalStatus.DRAFT) {
      throw new BusinessException("PROPOSAL_IMMUTABLE", "Only DRAFT proposals can be updated", HttpStatus.CONFLICT);
    }

    // Re-create draft to re-normalize amount and fields
    Instant now = clock.instant();
    RemediationProposalEntity updated = RemediationProposalEntity.createDraft(
        proposal.getId(),
        proposal.getCaseId(),
        proposal.getInvestigationCycle(),
        proposal.getSourceTransactionId(),
        request.targetAccountId(),
        request.direction(),
        request.amount(),
        request.currency(),
        request.reason(),
        proposedBy,
        now);

    proposalRepository.save(updated);
    return toResponse(updated);
  }

  @Transactional
  public RemediationProposalResponse submit(UUID proposalId, UUID proposedBy, long expectedVersion) {
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found", HttpStatus.NOT_FOUND));

    if (proposal.getVersion() != expectedVersion) {
      throw new BusinessException("STALE_PROPOSAL_VERSION", "Proposal has been updated by another transaction", HttpStatus.CONFLICT);
    }
    if (!proposal.getProposedBy().equals(proposedBy)) {
      throw new BusinessException("PROPOSAL_FORBIDDEN", "Only the proposer can submit this proposal", HttpStatus.FORBIDDEN);
    }

    Instant now = clock.instant();
    String canonicalHash = fingerprintSupport.calculateCanonicalHash(proposal);
    proposal.submit(canonicalHash, now);

    proposalRepository.save(proposal);
    return toResponse(proposal);
  }

  @Transactional
  public RemediationProposalResponse approveProposal(UUID proposalId, UUID checkerUserId, long expectedVersion) {
    Instant now = clock.instant();

    // 1. Pessimistic Lock on Proposal
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found", HttpStatus.NOT_FOUND));

    // 2. Concurrency Stale Version Protection
    if (proposal.getVersion() != expectedVersion) {
      throw new BusinessException("STALE_PROPOSAL_VERSION", "Proposal version mismatch! Data in UI is stale.", HttpStatus.CONFLICT);
    }

    // 3. Status Validation
    if (proposal.getStatus() != RemediationProposalStatus.PENDING_APPROVAL) {
      throw new BusinessException("INVALID_PROPOSAL_STATUS", "Proposal is not pending approval (Current: " + proposal.getStatus() + ")", HttpStatus.CONFLICT);
    }

    // 4. Segregation of Duty Check (Maker != Checker)
    if (proposal.getProposedBy().equals(checkerUserId)) {
      throw new BusinessException("MAKER_CHECKER_SAME_USER", "Maker and Checker must be different users", HttpStatus.CONFLICT);
    }

    // 5. Payload SHA-256 Fingerprint Verification (Anti-tampering Check)
    fingerprintSupport.verifyFingerprint(proposal, proposal.getProposalPayloadHash());

    // 6. Transition Proposal State to APPROVED and EXECUTION_PENDING
    proposal.approve(checkerUserId, now);
    proposal.markExecutionPending(now);
    proposalRepository.save(proposal);

    // 7. Insert Transactional Outbox Event in SAME Local Database Transaction
    enqueueAdjustmentRequestedOutboxEvent(proposal, now);

    return toResponse(proposal);
  }

  @Transactional
  public RemediationProposalResponse reject(UUID proposalId, RejectRemediationProposalRequest request, UUID checkerUserId) {
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found", HttpStatus.NOT_FOUND));

    if (proposal.getVersion() != request.expectedVersion()) {
      throw new BusinessException("STALE_PROPOSAL_VERSION", "Proposal version mismatch", HttpStatus.CONFLICT);
    }
    if (proposal.getProposedBy().equals(checkerUserId)) {
      throw new BusinessException("MAKER_CHECKER_SAME_USER", "Maker and Checker must be different users", HttpStatus.CONFLICT);
    }

    Instant now = clock.instant();
    proposal.reject(checkerUserId, request.reason(), now);
    proposalRepository.save(proposal);
    return toResponse(proposal);
  }

  @Transactional
  public RemediationProposalResponse cancel(UUID proposalId, UUID actorUserId, long expectedVersion) {
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found", HttpStatus.NOT_FOUND));

    if (proposal.getVersion() != expectedVersion) {
      throw new BusinessException("STALE_PROPOSAL_VERSION", "Proposal version mismatch", HttpStatus.CONFLICT);
    }
    if (!proposal.getProposedBy().equals(actorUserId)) {
      throw new BusinessException("PROPOSAL_FORBIDDEN", "Only the proposer can cancel this proposal", HttpStatus.FORBIDDEN);
    }

    Instant now = clock.instant();
    proposal.cancel(now);
    proposalRepository.save(proposal);
    return toResponse(proposal);
  }

  private void enqueueAdjustmentRequestedOutboxEvent(RemediationProposalEntity proposal, Instant now) {
    UUID eventId = UUID.randomUUID();
    CanonicalProposalPayload canonicalPayload = CanonicalProposalPayload.fromEntity(proposal);

    Map<String, Object> eventPayloadMap = Map.ofEntries(
        Map.entry("eventId", eventId.toString()),
        Map.entry("eventType", "ADJUSTMENT_REQUESTED"),
        Map.entry("schemaVersion", 1),
        Map.entry("correlationId", proposal.getCaseId().toString()),
        Map.entry("causationId", proposal.getCheckerId() != null ? proposal.getCheckerId().toString() : ""),
        Map.entry("proposalId", proposal.getId().toString()),
        Map.entry("caseId", proposal.getCaseId().toString()),
        Map.entry("investigationCycle", proposal.getInvestigationCycle()),
        Map.entry("referenceId", proposal.getExecutionReferenceId()),
        Map.entry("targetAccountId", proposal.getTargetAccountId().toString()),
        Map.entry("direction", proposal.getDirection().name()),
        Map.entry("amount", canonicalPayload.amount()),
        Map.entry("currency", canonicalPayload.currency()),
        Map.entry("proposalPayloadHash", proposal.getProposalPayloadHash()),
        Map.entry("occurredAt", now.toString())
    );

    try {
      String jsonPayload = objectMapper.writeValueAsString(eventPayloadMap);
      RemediationOutboxEntity outbox = RemediationOutboxEntity.create(
          eventId, proposal.getId(), "ADJUSTMENT_REQUESTED", 1, jsonPayload, now);
      outboxRepository.save(outbox);
    } catch (JsonProcessingException e) {
      throw new BusinessException("OUTBOX_SERIALIZATION_FAILED", "Failed to serialize outbox event payload", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private RemediationProposalResponse toResponse(RemediationProposalEntity entity) {
    return new RemediationProposalResponse(
        entity.getId().toString(),
        entity.getCaseId().toString(),
        entity.getInvestigationCycle(),
        entity.getSourceTransactionId() != null ? entity.getSourceTransactionId().toString() : null,
        entity.getTargetAccountId().toString(),
        entity.getDirection().name(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getReason(),
        entity.getProposalPayloadHash(),
        entity.getExecutionReferenceId(),
        entity.getStatus(),
        entity.getProposedBy().toString(),
        entity.getCheckerId() != null ? entity.getCheckerId().toString() : null,
        entity.getSubmittedAt(),
        entity.getApprovedAt(),
        entity.getRejectedAt(),
        entity.getRejectionReason(),
        entity.getFailureReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }
}
