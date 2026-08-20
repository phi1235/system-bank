package com.banksystem.transaction.application.settlement;

import com.banksystem.transaction.domain.settlement.B2bPayoutEntity;
import com.banksystem.transaction.domain.settlement.B2bPayoutRepository;
import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutClaimService {

  private static final Logger log = LoggerFactory.getLogger(PayoutClaimService.class);

  private final B2bPayoutRepository payoutRepository;

  public PayoutClaimService(B2bPayoutRepository payoutRepository) {
    this.payoutRepository = payoutRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<PayoutClaimContext> claimReadyPayoutsBatch(int limit, int leaseSeconds) {
    Instant now = Instant.now();
    List<B2bPayoutEntity> readyPayouts = payoutRepository.claimReadyPayouts(now, limit);
    if (readyPayouts.isEmpty()) {
      return List.of();
    }

    List<PayoutClaimContext> contexts = new ArrayList<>();
    for (B2bPayoutEntity payout : readyPayouts) {
      UUID claimToken = UUID.randomUUID();
      payout.setStatus(B2bPayoutStatus.DISPATCHING);
      payout.setClaimToken(claimToken);
      payout.setClaimedAt(now);
      payout.setClaimExpiresAt(now.plusSeconds(leaseSeconds));
      payout.setUpdatedAt(now);
      payoutRepository.save(payout);

      contexts.add(toContext(payout));
    }

    log.info("[PAYOUT-CLAIM] Claimed {} READY payouts for dispatching", contexts.size());
    return contexts;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void transitionExpiredDispatchingToPendingRecon(int limit) {
    Instant now = Instant.now();
    List<B2bPayoutEntity> expired = payoutRepository.claimExpiredDispatchingPayouts(now, limit);
    if (expired.isEmpty()) {
      return;
    }

    for (B2bPayoutEntity payout : expired) {
      log.warn("[PAYOUT-CLAIM] Payout [{}] lease expired in DISPATCHING, transitioning to PENDING_RECON", payout.getId());
      payout.setStatus(B2bPayoutStatus.PENDING_RECON);
      payout.setClaimToken(null);
      payout.setClaimedAt(null);
      payout.setClaimExpiresAt(null);
      payout.setLastError("Lease expired during DISPATCHING phase");
      payout.setUpdatedAt(now);
      payoutRepository.save(payout);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<PayoutClaimContext> claimPendingReconBatch(int limit, int leaseSeconds) {
    Instant now = Instant.now();
    List<B2bPayoutEntity> reconList = payoutRepository.claimPendingReconPayouts(now, limit);
    if (reconList.isEmpty()) {
      return List.of();
    }

    List<PayoutClaimContext> contexts = new ArrayList<>();
    for (B2bPayoutEntity payout : reconList) {
      UUID claimToken = UUID.randomUUID();
      payout.setClaimToken(claimToken);
      payout.setClaimedAt(now);
      payout.setClaimExpiresAt(now.plusSeconds(leaseSeconds));
      payout.setUpdatedAt(now);
      payoutRepository.save(payout);

      contexts.add(toContext(payout));
    }

    log.info("[PAYOUT-CLAIM] Claimed {} payouts for reconciliation (PENDING_RECON/SWITCH_SUCCESS_LEDGER_PENDING)", contexts.size());
    return contexts;
  }

  private PayoutClaimContext toContext(B2bPayoutEntity entity) {
    return new PayoutClaimContext(
        entity.getId(),
        entity.getClientRequestId(),
        entity.getOrganizationId(),
        entity.getSettlementLegId(),
        entity.getPayoutType(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getBeneficiaryAccountId(),
        entity.getBeneficiaryBankBin(),
        entity.getBeneficiaryAccountNumber(),
        entity.getBeneficiaryName(),
        entity.getStatus(),
        entity.getRetryCount(),
        entity.getClaimToken()
    );
  }
}
