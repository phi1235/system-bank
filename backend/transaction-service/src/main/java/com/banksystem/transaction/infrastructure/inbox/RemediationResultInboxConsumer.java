package com.banksystem.transaction.infrastructure.inbox;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.forensics.PostRemediationVerificationEngine;
import com.banksystem.transaction.domain.forensics.RemediationInboxRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalEntity;
import com.banksystem.transaction.domain.forensics.RemediationProposalRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RemediationResultInboxConsumer {
  private static final Logger log = LoggerFactory.getLogger(RemediationResultInboxConsumer.class);

  private final RemediationInboxRepository inboxRepository;
  private final RemediationProposalRepository proposalRepository;
  private final PostRemediationVerificationEngine verificationEngine;
  private final Clock clock;

  public RemediationResultInboxConsumer(
      RemediationInboxRepository inboxRepository,
      RemediationProposalRepository proposalRepository,
      PostRemediationVerificationEngine verificationEngine,
      Clock clock) {
    this.inboxRepository = inboxRepository;
    this.proposalRepository = proposalRepository;
    this.verificationEngine = verificationEngine;
    this.clock = clock;
  }

  @Transactional
  public void consumeRemediationPostedEvent(
      UUID eventId, UUID proposalId, UUID caseId, int cycle, String referenceId, String targetAccountId) {
    Instant now = clock.instant();

    // 1. ATOMIC DB INBOX DEDUPLICATION (ON CONFLICT DO NOTHING)
    int inserted = inboxRepository.insertIfNotExistsNative(eventId, "REMEDIATION_POSTED", now);
    if (inserted == 0) {
      log.info("Duplicate REMEDIATION_POSTED inbox event {} received, acknowledging safely", eventId);
      return;
    }

    // 2. LOCK PROPOSAL ROW
    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found for posted result"));

    // Check investigation cycle matches current case cycle
    if (proposal.getInvestigationCycle() != cycle) {
      log.warn("Stale cycle result received (Event cycle {}, Proposal cycle {}). Ignoring stale result event {}",
          cycle, proposal.getInvestigationCycle(), eventId);
      return;
    }

    // 3. TRANSITION PROPOSAL TO POSTED STATE
    if (proposal.getStatus() == RemediationProposalStatus.EXECUTION_PENDING
        || proposal.getStatus() == RemediationProposalStatus.APPROVED
        || proposal.getStatus() == RemediationProposalStatus.EXECUTING) {
      proposal.markPosted(now);
      proposalRepository.save(proposal);
      log.info("Proposal {} successfully transitioned to POSTED state", proposalId);
    }

    // 4. TRIGGER POST-REMEDIATION VERIFICATION ENGINE & CLOSED-LOOP RESOLUTION GATE
    verificationEngine.verifyProposalAndEvaluateGate(proposalId);
  }
}
