package com.banksystem.transaction.application.collection;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.application.collection.CollectionPaymentExecutionService.PaymentPreparedContext;
import com.banksystem.transaction.domain.collection.InboundPaymentEventEntity;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.AtomicPostingView;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.CollectionReceiptCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectionPaymentRecoveryWorker {

  private static final Logger log = LoggerFactory.getLogger(CollectionPaymentRecoveryWorker.class);

  private final InboundRecoveryClaimService recoveryClaimService;
  private final InboundPaymentEventRepository eventRepository;
  private final CollectionPaymentExecutionService paymentExecutionService;
  private final AccountAtomicLedgerClient accountAtomicLedgerClient;

  public CollectionPaymentRecoveryWorker(
      InboundRecoveryClaimService recoveryClaimService,
      InboundPaymentEventRepository eventRepository,
      CollectionPaymentExecutionService paymentExecutionService,
      AccountAtomicLedgerClient accountAtomicLedgerClient) {
    this.recoveryClaimService = recoveryClaimService;
    this.eventRepository = eventRepository;
    this.paymentExecutionService = paymentExecutionService;
    this.accountAtomicLedgerClient = accountAtomicLedgerClient;
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 15000)
  public void recoverStuckInboundPayments() {
    List<InboundEventClaimContext> claimedEvents = recoveryClaimService.claimPendingEventsBatch(20, 60);

    if (claimedEvents.isEmpty()) {
      return;
    }

    log.info("[COLLECTION-RECOVERY] Claimed {} pending/stuck inbound events to recover", claimedEvents.size());

    for (InboundEventClaimContext ctx : claimedEvents) {
      try {
        Optional<InboundPaymentEventEntity> eventOpt = eventRepository.findById(ctx.eventId());
        if (eventOpt.isEmpty()) {
          continue;
        }
        InboundPaymentEventEntity event = eventOpt.get();

        PaymentPreparedContext prepCtx = paymentExecutionService.preparePaymentProcessing(event);
        if (prepCtx == null) {
          continue;
        }

        UUID journalId = ctx.ledgerJournalId();
        if (journalId == null) {
          String commandId = "COLLECTION:" + prepCtx.provider() + ":" + prepCtx.providerTxId();
          String businessRef = "ORDER:" + prepCtx.merchantOrderId() + ":" + prepCtx.providerTxId();
          String clearingCode = "CLEARING:" + prepCtx.provider();
          String desc = "Collection inbound receipt recovery for order " + prepCtx.merchantOrderId();

          CollectionReceiptCommand command = new CollectionReceiptCommand(
              commandId,
              businessRef,
              prepCtx.orderId(),
              prepCtx.escrowAccountId(),
              clearingCode,
              prepCtx.amount(),
              prepCtx.currency(),
              desc
          );

          ApiResponse<AtomicPostingView> resp = accountAtomicLedgerClient.recordCollectionReceipt(command);
          if (resp == null || resp.data() == null || resp.data().journalId() == null) {
            throw new IllegalStateException("Account ledger returned an indeterminate collection receipt result");
          }
          AtomicPostingView posting = resp.data();
          if (!commandId.equals(posting.businessCommandId())
              || prepCtx.amount().compareTo(posting.amount()) != 0) {
            throw new IllegalStateException("Account ledger response does not match the recovery command");
          }
          journalId = posting.journalId();
          if (!recoveryClaimService.recordLedgerSuccess(ctx.eventId(), ctx.claimToken(), journalId)) {
            continue;
          }
        }

        if (journalId == null) {
          throw new IllegalStateException("Inbound event cannot be finalized without a ledger journal");
        }

        paymentExecutionService.finalizePaymentAllocation(prepCtx, journalId);
        log.info("[COLLECTION-RECOVERY] Successfully recovered event [{}] for order [{}]", ctx.eventId(), prepCtx.orderId());
      } catch (Exception ex) {
        log.error("[COLLECTION-RECOVERY] Failed recovering event [{}]: {}", ctx.eventId(), ex.getMessage());
        recoveryClaimService.markFailedOrRetry(
            ctx.eventId(), ctx.claimToken(), "Recovery attempt failed: " + ex.getMessage());
      }
    }
  }
}
