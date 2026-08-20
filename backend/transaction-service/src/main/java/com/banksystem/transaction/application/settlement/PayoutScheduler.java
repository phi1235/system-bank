package com.banksystem.transaction.application.settlement;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayoutScheduler {

  private static final Logger log = LoggerFactory.getLogger(PayoutScheduler.class);

  private final PayoutClaimService payoutClaimService;
  private final NapasPayoutExecutor napasPayoutExecutor;

  public PayoutScheduler(
      PayoutClaimService payoutClaimService,
      NapasPayoutExecutor napasPayoutExecutor) {
    this.payoutClaimService = payoutClaimService;
    this.napasPayoutExecutor = napasPayoutExecutor;
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 3000)
  public void dispatchReadyPayouts() {
    List<PayoutClaimContext> claimed = payoutClaimService.claimReadyPayoutsBatch(20, 60);
    if (claimed.isEmpty()) {
      return;
    }

    for (PayoutClaimContext ctx : claimed) {
      try {
        napasPayoutExecutor.executePayout(ctx);
      } catch (Exception ex) {
        log.error("[PAYOUT-SCHEDULER] Uncaught error executing payout {}: {}", ctx.payoutId(), ex.getMessage());
      }
    }
  }

  @Scheduled(fixedDelay = 15000, initialDelay = 8000)
  public void reconcilePendingPayouts() {
    // 1. Transition expired DISPATCHING leases to PENDING_RECON
    payoutClaimService.transitionExpiredDispatchingToPendingRecon(20);

    // 2. Claim batch of PENDING_RECON and SWITCH_SUCCESS_LEDGER_PENDING payouts
    List<PayoutClaimContext> claimedRecon = payoutClaimService.claimPendingReconBatch(20, 60);
    if (claimedRecon.isEmpty()) {
      return;
    }

    for (PayoutClaimContext ctx : claimedRecon) {
      try {
        napasPayoutExecutor.inquireAndReconcile(ctx);
      } catch (Exception ex) {
        log.error("[PAYOUT-SCHEDULER] Uncaught error reconciling payout {}: {}", ctx.payoutId(), ex.getMessage());
      }
    }
  }
}
