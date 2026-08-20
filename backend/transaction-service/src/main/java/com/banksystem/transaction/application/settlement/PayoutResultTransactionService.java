package com.banksystem.transaction.application.settlement;

import com.banksystem.transaction.domain.settlement.B2bPayoutEntity;
import com.banksystem.transaction.domain.settlement.B2bPayoutRepository;
import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import com.banksystem.transaction.domain.settlement.SettlementLegEntity;
import com.banksystem.transaction.domain.settlement.SettlementLegRepository;
import com.banksystem.transaction.domain.settlement.SettlementLegStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutResultTransactionService {

  private static final Logger log = LoggerFactory.getLogger(PayoutResultTransactionService.class);

  private final B2bPayoutRepository payoutRepository;
  private final SettlementLegRepository legRepository;

  public PayoutResultTransactionService(
      B2bPayoutRepository payoutRepository,
      SettlementLegRepository legRepository) {
    this.payoutRepository = payoutRepository;
    this.legRepository = legRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markSwitchSuccess(UUID payoutId, UUID claimToken, String providerReference) {
    Instant now = Instant.now();
    Optional<B2bPayoutEntity> payoutOpt = payoutRepository.findByIdForUpdate(payoutId);
    if (payoutOpt.isEmpty()) {
      return false;
    }

    B2bPayoutEntity payout = payoutOpt.get();
    if (!ownsClaim(payout, claimToken)
        || (payout.getStatus() != B2bPayoutStatus.DISPATCHING
            && payout.getStatus() != B2bPayoutStatus.PENDING_RECON)) {
      log.warn("[PAYOUT-RESULT] Ignoring stale switch success for payout [{}]", payoutId);
      return false;
    }
    payout.setStatus(B2bPayoutStatus.SWITCH_SUCCESS_LEDGER_PENDING);
    payout.setProviderReference(providerReference);
    payout.setNextRetryAt(null);
    payout.setUpdatedAt(now);
    payoutRepository.save(payout);
    log.info("[PAYOUT-RESULT] Payout [{}] switch SUCCESS, set SWITCH_SUCCESS_LEDGER_PENDING (ref={})",
        payoutId, providerReference);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID markClearingSuccess(UUID payoutId, UUID claimToken, UUID clearingJournalId) {
    Instant now = Instant.now();
    Optional<B2bPayoutEntity> payoutOpt = payoutRepository.findByIdForUpdate(payoutId);
    if (payoutOpt.isEmpty()) {
      return null;
    }

    B2bPayoutEntity payout = payoutOpt.get();
    if (!ownsClaim(payout, claimToken)
        || payout.getStatus() != B2bPayoutStatus.SWITCH_SUCCESS_LEDGER_PENDING) {
      log.warn("[PAYOUT-RESULT] Ignoring stale clearing result for payout [{}]", payoutId);
      return null;
    }
    payout.setStatus(B2bPayoutStatus.SUCCESS);
    payout.setClearingJournalId(clearingJournalId);
    payout.setClaimToken(null);
    payout.setClaimedAt(null);
    payout.setClaimExpiresAt(null);
    payout.setNextRetryAt(null);
    payout.setUpdatedAt(now);
    payoutRepository.save(payout);

    // Complete corresponding settlement leg
    Optional<SettlementLegEntity> legOpt = legRepository.findById(payout.getSettlementLegId());
    if (legOpt.isPresent()) {
      SettlementLegEntity leg = legOpt.get();
      leg.setStatus(SettlementLegStatus.COMPLETED);
      leg.setUpdatedAt(now);
      legRepository.save(leg);
      log.info("[PAYOUT-RESULT] Payout [{}] clearing SUCCESS (journalId={}), marked leg [{}] COMPLETED",
          payoutId, clearingJournalId, leg.getId());
      return leg.getSettlementId();
    }

    return null;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailed(UUID payoutId, UUID claimToken, String reason) {
    Instant now = Instant.now();
    Optional<B2bPayoutEntity> payoutOpt = payoutRepository.findByIdForUpdate(payoutId);
    if (payoutOpt.isEmpty()) {
      return false;
    }

    B2bPayoutEntity payout = payoutOpt.get();
    if (!ownsClaim(payout, claimToken)
        || (payout.getStatus() != B2bPayoutStatus.DISPATCHING
            && payout.getStatus() != B2bPayoutStatus.PENDING_RECON)) {
      log.warn("[PAYOUT-RESULT] Ignoring stale failure for payout [{}]", payoutId);
      return false;
    }
    payout.setStatus(B2bPayoutStatus.FAILED);
    payout.setLastError(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
    payout.setClaimToken(null);
    payout.setClaimedAt(null);
    payout.setClaimExpiresAt(null);
    payout.setUpdatedAt(now);
    payoutRepository.save(payout);

    Optional<SettlementLegEntity> legOpt = legRepository.findById(payout.getSettlementLegId());
    if (legOpt.isPresent()) {
      SettlementLegEntity leg = legOpt.get();
      leg.setStatus(SettlementLegStatus.FAILED);
      leg.setUpdatedAt(now);
      legRepository.save(leg);
    }
    log.error("[PAYOUT-RESULT] Payout [{}] marked FAILED: {}", payoutId, reason);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markPendingRecon(UUID payoutId, UUID claimToken, String error) {
    Instant now = Instant.now();
    Optional<B2bPayoutEntity> payoutOpt = payoutRepository.findByIdForUpdate(payoutId);
    if (payoutOpt.isEmpty()) {
      return false;
    }

    B2bPayoutEntity payout = payoutOpt.get();
    if (!ownsClaim(payout, claimToken)
        || (payout.getStatus() != B2bPayoutStatus.DISPATCHING
            && payout.getStatus() != B2bPayoutStatus.PENDING_RECON)) {
      log.warn("[PAYOUT-RESULT] Ignoring stale reconciliation result for payout [{}]", payoutId);
      return false;
    }
    int nextCount = payout.getRetryCount() + 1;
    payout.setRetryCount(nextCount);
    payout.setLastError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
    payout.setClaimToken(null);
    payout.setClaimedAt(null);
    payout.setClaimExpiresAt(null);
    payout.setUpdatedAt(now);

    if (nextCount >= 5) {
      payout.setStatus(B2bPayoutStatus.MANUAL_REVIEW);
      payout.setNextRetryAt(null);
      log.error("[PAYOUT-RESULT] Payout [{}] remains financially indeterminate after 5 inquiries; marked MANUAL_REVIEW: {}", payoutId, error);
    } else {
      payout.setStatus(B2bPayoutStatus.PENDING_RECON);
      long backoffSeconds = 30L * (1L << Math.min(nextCount, 6));
      payout.setNextRetryAt(now.plusSeconds(backoffSeconds));
      log.warn("[PAYOUT-RESULT] Payout [{}] marked PENDING_RECON (retry={}, nextRetryAt={}): {}",
          payoutId, nextCount, payout.getNextRetryAt(), error);
    }

    payoutRepository.save(payout);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean resetToReadyForRetry(UUID payoutId, UUID claimToken) {
    Instant now = Instant.now();
    Optional<B2bPayoutEntity> payoutOpt = payoutRepository.findByIdForUpdate(payoutId);
    if (payoutOpt.isEmpty()) {
      return false;
    }

    B2bPayoutEntity payout = payoutOpt.get();
    if (!ownsClaim(payout, claimToken) || payout.getStatus() != B2bPayoutStatus.PENDING_RECON) {
      log.warn("[PAYOUT-RESULT] Ignoring stale reset-to-ready for payout [{}]", payoutId);
      return false;
    }
    payout.setStatus(B2bPayoutStatus.READY);
    payout.setClaimToken(null);
    payout.setClaimedAt(null);
    payout.setClaimExpiresAt(null);
    payout.setNextRetryAt(null);
    payout.setUpdatedAt(now);
    payoutRepository.save(payout);
    log.info("[PAYOUT-RESULT] Payout [{}] confirmed not executed by inquiry, reset to READY with same clientRequestId [{}]",
        payoutId, payout.getClientRequestId());
    return true;
  }

  private boolean ownsClaim(B2bPayoutEntity payout, UUID claimToken) {
    return claimToken != null && Objects.equals(payout.getClaimToken(), claimToken);
  }
}
