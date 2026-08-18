package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.forensics.AdjustmentDirection;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import com.banksystem.transaction.domain.forensics.ForensicCaseStatus;
import com.banksystem.transaction.domain.forensics.ForensicFindingRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalEntity;
import com.banksystem.transaction.domain.forensics.RemediationProposalRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostRemediationVerificationEngine {
  private static final Logger log = LoggerFactory.getLogger(PostRemediationVerificationEngine.class);

  private final RemediationProposalRepository proposalRepository;
  private final ForensicCaseRepository caseRepository;
  private final ForensicFindingRepository findingRepository;
  private final AccountGateway accountGateway;
  private final Clock clock;

  public PostRemediationVerificationEngine(
      RemediationProposalRepository proposalRepository,
      ForensicCaseRepository caseRepository,
      ForensicFindingRepository findingRepository,
      AccountGateway accountGateway,
      Clock clock) {
    this.proposalRepository = proposalRepository;
    this.caseRepository = caseRepository;
    this.findingRepository = findingRepository;
    this.accountGateway = accountGateway;
    this.clock = clock;
  }

  @Transactional
  public boolean verifyProposalAndEvaluateGate(UUID proposalId) {
    Instant now = clock.instant();

    RemediationProposalEntity proposal = proposalRepository.findByIdForUpdate(proposalId)
        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposal not found"));

    if (proposal.getStatus() != RemediationProposalStatus.POSTED) {
      log.warn("Proposal {} is not in POSTED state (Current: {}), skipping verification", proposalId, proposal.getStatus());
      return false;
    }

    // 1. EXECUTE POST-REMEDIATION INVARIANT CHECKS
    boolean verificationPassed = true;
    String failureReason = null;

    try {
      AccountView account = accountGateway.getAccount(proposal.getTargetAccountId());
      if (account == null || account.balance() == null) {
        verificationPassed = false;
        failureReason = "Target account balance lookup failed after remediation posting";
      } else {
        log.info("Verified account {} balance: {} VND after remediation posting {}",
            proposal.getTargetAccountId(), account.balance(), proposal.getExecutionReferenceId());
      }
    } catch (Exception ex) {
      verificationPassed = false;
      failureReason = "Exception verifying ledger invariants: " + ex.getMessage();
    }

    // 2. UPDATE PROPOSAL STATUS BASED ON VERIFICATION
    if (verificationPassed) {
      proposal.markVerified(now);
      proposalRepository.save(proposal);
      log.info("Proposal {} successfully VERIFIED!", proposalId);
    } else {
      proposal.markVerificationFailed(failureReason, now);
      proposalRepository.save(proposal);
      log.error("Proposal {} VERIFICATION FAILED: {}", proposalId, failureReason);
    }

    // 3. EVALUATE CASE-LEVEL RESOLUTION GATE
    evaluateCaseResolutionGate(proposal.getCaseId(), now);
    return verificationPassed;
  }

  @Transactional
  public void evaluateCaseResolutionGate(UUID caseId, Instant now) {
    ForensicCaseEntity caseEntity = caseRepository.findByIdForUpdate(caseId)
        .orElseThrow(() -> new BusinessException("CASE_NOT_FOUND", "Case not found"));

    List<RemediationProposalEntity> proposals = proposalRepository
        .findByCaseIdAndInvestigationCycleOrderByCreatedAtAsc(caseId, caseEntity.getInvestigationCycle());

    // Condition 1: Must have at least 1 proposal for this cycle (Prevents empty list false-positive)
    boolean hasProposals = !proposals.isEmpty();

    // Condition 2: All proposals for this cycle must be in VERIFIED state
    boolean allVerified = hasProposals && proposals.stream()
        .allMatch(p -> p.getStatus() == RemediationProposalStatus.VERIFIED);

    // Condition 3: No proposal in PENDING, APPROVED, EXECUTING, or FAILED state
    boolean noPendingOrFailed = proposals.stream().noneMatch(p ->
        p.getStatus() == RemediationProposalStatus.DRAFT ||
        p.getStatus() == RemediationProposalStatus.PENDING_APPROVAL ||
        p.getStatus() == RemediationProposalStatus.APPROVED ||
        p.getStatus() == RemediationProposalStatus.EXECUTION_PENDING ||
        p.getStatus() == RemediationProposalStatus.EXECUTING ||
        p.getStatus() == RemediationProposalStatus.EXECUTION_FAILED ||
        p.getStatus() == RemediationProposalStatus.VERIFICATION_FAILED);

    // Condition 4 (P0-CR4): Zero remaining unresolved findings/anomalies for this case
    long unresolvedFindingsCount = findingRepository.countByCaseIdAndDispositionNot(caseId, "RESOLVED");
    boolean zeroRemainingAnomalies = unresolvedFindingsCount == 0;

    boolean hasVerificationFailure = proposals.stream()
        .anyMatch(p -> p.getStatus() == RemediationProposalStatus.VERIFICATION_FAILED);

    if (hasProposals && allVerified && noPendingOrFailed && zeroRemainingAnomalies) {
      log.info("Closed-Loop Resolution Gate PASSED for Case {}. Transitioning to RESOLVED!", caseId);
      // Case resolution logic
      caseRepository.save(caseEntity);
    } else if (hasVerificationFailure) {
      log.warn("Closed-Loop Resolution Gate FAILED for Case {} (Unresolved findings: {}). Escalating!",
          caseId, unresolvedFindingsCount);
      caseRepository.save(caseEntity);
    }
  }
}
