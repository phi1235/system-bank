package com.banksystem.transaction.application.collection;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.CollectionDtos.WebhookProcessingResult;
import com.banksystem.transaction.application.collection.CollectionPaymentExecutionService.PaymentPreparedContext;
import com.banksystem.transaction.domain.collection.InboundPaymentEventEntity;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.AtomicPostingView;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.CollectionReceiptCommand;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider.VerifiedInboundPayment;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProviderRegistry;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class InboundWebhookService {

  private static final Logger log = LoggerFactory.getLogger(InboundWebhookService.class);

  private final VirtualAccountProviderRegistry providerRegistry;
  private final InboundWebhookInboxWriter inboxWriter;
  private final CollectionPaymentExecutionService paymentExecutionService;
  private final AccountAtomicLedgerClient accountAtomicLedgerClient;

  public InboundWebhookService(
      VirtualAccountProviderRegistry providerRegistry,
      InboundWebhookInboxWriter inboxWriter,
      CollectionPaymentExecutionService paymentExecutionService,
      AccountAtomicLedgerClient accountAtomicLedgerClient) {
    this.providerRegistry = providerRegistry;
    this.inboxWriter = inboxWriter;
    this.paymentExecutionService = paymentExecutionService;
    this.accountAtomicLedgerClient = accountAtomicLedgerClient;
  }

  public WebhookProcessingResult processInboundWebhook(String providerCode, String rawPayload, Map<String, String> headers) {
    VirtualAccountProvider provider = providerRegistry.getProvider(providerCode);
    VerifiedInboundPayment verified = provider.verifyWebhook(rawPayload, headers);
    if (!verified.valid()) {
      log.warn("[INBOUND-WEBHOOK] Invalid signature or unparseable payload from {}", providerCode);
      return new WebhookProcessingResult(false, verified.errorMessage(), "INVALID_PAYLOAD", null, null);
    }

    // Step 1 (Inbox TX): Persist webhook into inbox in isolated REQUIRES_NEW transaction before any processing
    InboundPaymentEventEntity event;
    try {
      Optional<InboundPaymentEventEntity> existing = inboxWriter.findByProviderAndTxId(verified.provider(), verified.providerTransactionId());
      if (existing.isPresent()) {
        log.info("[INBOUND-WEBHOOK] Known duplicate provider tx: {}/{}", verified.provider(), verified.providerTransactionId());
        return new WebhookProcessingResult(true, "Duplicate webhook skipped", "DUPLICATE", null, null);
      }
      event = inboxWriter.insertReceived(verified);
    } catch (Exception ex) {
      if (isPostgresDuplicateConstraint(ex)) {
        log.warn("[INBOUND-WEBHOOK] Concurrency duplicate on unique constraint uq_inbound_provider_tx: {}", ex.getMessage());
        return new WebhookProcessingResult(true, "Duplicate webhook skipped", "DUPLICATE", null, null);
      }
      log.error("[INBOUND-WEBHOOK] Database error writing inbox: {}", ex.getMessage(), ex);
      throw new BusinessException(
          "INTERNAL_ERROR", "Failed to persist inbound webhook", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Step 2: Phase A (TX A) - Lock order, validate match, set PAYMENT_PROCESSING, commit TX A
    PaymentPreparedContext ctx;
    try {
      ctx = paymentExecutionService.preparePaymentProcessing(event);
      if (ctx == null) {
        return new WebhookProcessingResult(true, "Inbound event unmatchable or not actionable", "UNMATCHED", null, null);
      }
    } catch (Exception ex) {
      log.error("[INBOUND-WEBHOOK] Error in Phase A (prepare): {}", ex.getMessage(), ex);
      paymentExecutionService.markEventPendingRecovery(event.getId(), "Phase A failed: " + ex.getMessage());
      return new WebhookProcessingResult(false, ex.getMessage(), "PREPARE_FAILED", null, null);
    }

    // Step 3: Remote Ledgers Posting (OUTSIDE any local DB transaction / locks)
    UUID journalId = null;
    try {
      String commandId = "COLLECTION:" + ctx.provider() + ":" + ctx.providerTxId();
      String businessRef = "ORDER:" + ctx.merchantOrderId() + ":" + ctx.providerTxId();
      String clearingCode = "CLEARING:" + ctx.provider();
      String desc = "Collection inbound receipt for order " + ctx.merchantOrderId();

      CollectionReceiptCommand command = new CollectionReceiptCommand(
          commandId,
          businessRef,
          ctx.orderId(),
          ctx.escrowAccountId(),
          clearingCode,
          ctx.amount(),
          ctx.currency(),
          desc
      );

      ApiResponse<AtomicPostingView> resp = accountAtomicLedgerClient.recordCollectionReceipt(command);
      if (resp == null || resp.data() == null || resp.data().journalId() == null) {
        throw new IllegalStateException("Account ledger returned an indeterminate collection receipt result");
      }
      AtomicPostingView posting = resp.data();
      if (!commandId.equals(posting.businessCommandId())
          || ctx.amount().compareTo(posting.amount()) != 0) {
        throw new IllegalStateException("Account ledger response does not match the collection command");
      }
      journalId = posting.journalId();
    } catch (Exception ex) {
      log.error("[INBOUND-WEBHOOK] Remote atomic ledger posting failed for cmdId={}: {}", ctx.providerTxId(), ex.getMessage(), ex);
      // Leave order in PAYMENT_PROCESSING and mark event PENDING_RECOVERY for background recovery worker
      paymentExecutionService.markEventPendingRecovery(event.getId(), "Ledger posting failed: " + ex.getMessage());
      return new WebhookProcessingResult(false, "Remote ledger call failed: " + ex.getMessage(), "LEDGER_FAILED", ctx.orderId(), "PAYMENT_PROCESSING");
    }

    // Step 4: Phase B (TX B) - Finalize payment allocation and order status, commit TX B
    try {
      var finalizedStatus = paymentExecutionService.finalizePaymentAllocation(ctx, journalId);
      return new WebhookProcessingResult(true, "Successfully processed inbound payment", "PROCESSED", ctx.orderId(), finalizedStatus.name());
    } catch (Exception ex) {
      log.error("[INBOUND-WEBHOOK] Error in Phase B (finalize): {}", ex.getMessage(), ex);
      paymentExecutionService.markEventPendingRecovery(event.getId(), "Phase B failed: " + ex.getMessage());
      return new WebhookProcessingResult(false, ex.getMessage(), "FINALIZE_FAILED", ctx.orderId(), "PAYMENT_PROCESSING");
    }
  }

  private boolean isPostgresDuplicateConstraint(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof SQLException sqlEx) {
        if ("23505".equals(sqlEx.getSQLState())) {
          String msg = sqlEx.getMessage();
          if (msg != null && (msg.contains("uq_inbound_provider_tx") || msg.contains("inbound_payment_events_provider_provider_transaction_id_key"))) {
            return true;
          }
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
