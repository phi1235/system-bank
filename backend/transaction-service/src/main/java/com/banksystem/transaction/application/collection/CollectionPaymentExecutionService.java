package com.banksystem.transaction.application.collection;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.merchant.MerchantWebhookDispatcher;
import com.banksystem.transaction.domain.collection.CollectionOrderEntity;
import com.banksystem.transaction.domain.collection.CollectionOrderRepository;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import com.banksystem.transaction.domain.collection.InboundPaymentEventEntity;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import com.banksystem.transaction.domain.collection.PaymentAllocationEntity;
import com.banksystem.transaction.domain.collection.PaymentAllocationRepository;
import com.banksystem.transaction.domain.merchant.MerchantAccountEntity;
import com.banksystem.transaction.domain.merchant.MerchantAccountRepository;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountEntity;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountRepository;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountStatus;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionPaymentExecutionService {

  private static final Logger log = LoggerFactory.getLogger(CollectionPaymentExecutionService.class);

  private final InboundPaymentEventRepository eventRepository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final CollectionOrderRepository collectionOrderRepository;
  private final PaymentAllocationRepository paymentAllocationRepository;
  private final MerchantAccountRepository merchantAccountRepository;
  private final MerchantWebhookDispatcher webhookDispatcher;
  private final OutboxService outboxService;

  public CollectionPaymentExecutionService(
      InboundPaymentEventRepository eventRepository,
      VirtualAccountRepository virtualAccountRepository,
      CollectionOrderRepository collectionOrderRepository,
      PaymentAllocationRepository paymentAllocationRepository,
      MerchantAccountRepository merchantAccountRepository,
      MerchantWebhookDispatcher webhookDispatcher,
      OutboxService outboxService) {
    this.eventRepository = eventRepository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.collectionOrderRepository = collectionOrderRepository;
    this.paymentAllocationRepository = paymentAllocationRepository;
    this.merchantAccountRepository = merchantAccountRepository;
    this.webhookDispatcher = webhookDispatcher;
    this.outboxService = outboxService;
  }

  /**
   * Phase A (TX A): Lock order, validate VA/event, transition to PAYMENT_PROCESSING and commit.
   * Remote calls to account-service must happen OUTSIDE this transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PaymentPreparedContext preparePaymentProcessing(InboundPaymentEventEntity event) {
    Instant now = Instant.now();

    // 1. Locate matching Virtual Account by account number and bank bin
    Optional<VirtualAccountEntity> vaOpt = virtualAccountRepository.findByBankBinAndAccountNumber(
        event.getBankBin(), event.getVirtualAccountNumber()
    );
    if (vaOpt.isEmpty()) {
      vaOpt = virtualAccountRepository.findByAccountNumber(event.getVirtualAccountNumber());
    }

    if (vaOpt.isEmpty()) {
      event.setStatus(InboundPaymentStatus.UNMATCHED);
      event.setErrorMessage("No virtual account found matching " + event.getVirtualAccountNumber());
      releaseClaim(event);
      eventRepository.save(event);
      log.warn("[COLLECTION-EXEC] Unmatched VA number: {}", event.getVirtualAccountNumber());
      return null;
    }

    VirtualAccountEntity va = vaOpt.get();
    if (va.getStatus() != VirtualAccountStatus.ACTIVE) {
      event.setStatus(InboundPaymentStatus.FAILED);
      event.setErrorMessage("Virtual account is inactive or closed: " + va.getStatus());
      releaseClaim(event);
      eventRepository.save(event);
      return null;
    }

    if (va.getExpiresAt() != null && va.getExpiresAt().isBefore(now)) {
      event.setStatus(InboundPaymentStatus.FAILED);
      event.setErrorMessage("Virtual account has expired");
      releaseClaim(event);
      eventRepository.save(event);
      return null;
    }

    // 2. Locate matching Collection Order
    CollectionOrderEntity order = null;
    if ("DYNAMIC".equalsIgnoreCase(va.getMode().name())) {
      List<CollectionOrderEntity> orders = collectionOrderRepository.findByVirtualAccountId(va.getId());
      order = orders.stream()
          .filter(o -> o.getStatus() == CollectionOrderStatus.PENDING || o.getStatus() == CollectionOrderStatus.PARTIAL || o.getStatus() == CollectionOrderStatus.PAYMENT_PROCESSING)
          .findFirst()
          .orElse(null);
    } else {
      // FIXED VA mode: match by payment reference/content or FIFO pending order
      List<CollectionOrderEntity> orders = collectionOrderRepository.findByVirtualAccountId(va.getId());
      String ref = event.getReferenceContent() != null ? event.getReferenceContent().toUpperCase() : "";

      order = orders.stream()
          .filter(o -> o.getStatus() == CollectionOrderStatus.PENDING || o.getStatus() == CollectionOrderStatus.PARTIAL)
          .filter(o -> (o.getCustomerReference() != null && ref.contains(o.getCustomerReference().toUpperCase()))
              || ref.contains(o.getMerchantOrderId().toUpperCase()))
          .findFirst()
          .orElse(null);

      if (order == null) {
        // FIFO order fallback
        order = orders.stream()
            .filter(o -> o.getStatus() == CollectionOrderStatus.PENDING || o.getStatus() == CollectionOrderStatus.PARTIAL || o.getStatus() == CollectionOrderStatus.PAYMENT_PROCESSING)
            .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
            .orElse(null);
      }
    }

    if (order == null) {
      event.setStatus(InboundPaymentStatus.UNMATCHED);
      event.setErrorMessage("No pending order found for VA " + va.getAccountNumber());
      releaseClaim(event);
      eventRepository.save(event);
      return null;
    }

    // Lock order for update
    order = collectionOrderRepository.findByIdForUpdate(order.getId()).orElse(order);

    if (order.getStatus() == CollectionOrderStatus.CANCELLED || order.getStatus() == CollectionOrderStatus.EXPIRED) {
      event.setStatus(InboundPaymentStatus.MISMATCH);
      event.setErrorMessage("Order is in terminal invalid state: " + order.getStatus());
      releaseClaim(event);
      eventRepository.save(event);
      return null;
    }

    if (!order.getCurrency().equalsIgnoreCase(event.getCurrency())) {
      event.setStatus(InboundPaymentStatus.MISMATCH);
      event.setErrorMessage("Currency mismatch: order=" + order.getCurrency() + ", webhook=" + event.getCurrency());
      releaseClaim(event);
      eventRepository.save(event);
      return null;
    }

    // 3. Resolve Merchant Escrow Account
    UUID orgId = order.getOrganizationId();
    MerchantAccountEntity merchantAccount = merchantAccountRepository.findByOrganizationId(orgId)
        .orElseThrow(() -> new BusinessException("MERCHANT_ACCOUNT_NOT_CONFIGURED",
            "Merchant account configuration not found for organization " + orgId));

    UUID escrowAccountId = merchantAccount.getEscrowAccountId();
    if (escrowAccountId == null) {
      throw new BusinessException("ESCROW_ACCOUNT_NOT_CONFIGURED", "Escrow account not configured for merchant");
    }

    // Transition order state to PAYMENT_PROCESSING
    order.setStatus(CollectionOrderStatus.PAYMENT_PROCESSING);
    order.setUpdatedAt(now);
    collectionOrderRepository.save(order);

    event.setStatus(InboundPaymentStatus.LEDGER_PENDING);
    eventRepository.save(event);

    return new PaymentPreparedContext(
        order.getId(),
        order.getOrganizationId(),
        order.getMerchantOrderId(),
        escrowAccountId,
        event.getId(),
        event.getProvider(),
        event.getProviderTransactionId(),
        event.getAmount(),
        event.getCurrency(),
        event.getClaimToken()
    );
  }

  /**
   * Phase B (TX B): Allocate funds, update order paidAmount and status (PAID/PARTIAL/OVERPAID), and write outbox.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CollectionOrderStatus finalizePaymentAllocation(PaymentPreparedContext ctx, UUID ledgerJournalId) {
    Instant now = Instant.now();
    if (ledgerJournalId == null) {
      throw new BusinessException("LEDGER_INDETERMINATE", "Cannot finalize payment without a posted ledger journal");
    }

    CollectionOrderEntity order = collectionOrderRepository.findByIdForUpdate(ctx.orderId())
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found: " + ctx.orderId()));

    InboundPaymentEventEntity event = eventRepository.findByIdForUpdate(ctx.eventId())
        .orElseThrow(() -> new BusinessException("EVENT_NOT_FOUND", "Inbound event not found: " + ctx.eventId()));

    if (ctx.claimToken() != null && !Objects.equals(ctx.claimToken(), event.getClaimToken())) {
      throw new BusinessException("STALE_WORKER_CLAIM", "Inbound event lease is no longer owned by this worker");
    }
    if (event.getStatus() == InboundPaymentStatus.PROCESSED) {
      return order.getStatus();
    }

    // Record Payment Allocation if not already recorded
    List<PaymentAllocationEntity> existingAllocs = paymentAllocationRepository.findByInboundPaymentEventId(ctx.eventId());
    if (existingAllocs.isEmpty()) {
      PaymentAllocationEntity alloc = PaymentAllocationEntity.create(
          ctx.eventId(), ctx.orderId(), ctx.amount(), now
      );
      paymentAllocationRepository.save(alloc);
    }

    // Calculate sum of all payment allocations for this order
    List<PaymentAllocationEntity> allAllocations = paymentAllocationRepository.findByCollectionOrderId(ctx.orderId());
    BigDecimal totalPaid = allAllocations.stream()
        .map(PaymentAllocationEntity::getAllocatedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    order.setPaidAmount(totalPaid);
    order.setUpdatedAt(now);

    if (totalPaid.compareTo(order.getExpectedAmount()) >= 0) {
      if (totalPaid.compareTo(order.getExpectedAmount()) == 0) {
        order.setStatus(CollectionOrderStatus.PAID);
      } else {
        order.setStatus(CollectionOrderStatus.OVERPAID);
      }
      order.setPaidAt(now);
    } else {
      order.setStatus(CollectionOrderStatus.PARTIAL);
    }

    collectionOrderRepository.save(order);

    event.setStatus(InboundPaymentStatus.PROCESSED);
    event.setLedgerJournalId(ledgerJournalId);
    event.setProcessedAt(now);
    event.setNextRetryAt(null);
    releaseClaim(event);
    eventRepository.save(event);

    String eventType = switch (order.getStatus()) {
      case PAID -> "collection.order.paid.v1";
      case PARTIAL -> "collection.order.partial.v1";
      case OVERPAID -> "collection.order.overpaid.v1";
      default -> "collection.order.updated.v1";
    };

    Map<String, Object> payload = Map.of(
        "organizationId", order.getOrganizationId().toString(),
        "orderId", order.getId().toString(),
        "merchantOrderId", order.getMerchantOrderId(),
        "status", order.getStatus().name(),
        "expectedAmount", order.getExpectedAmount(),
        "paidAmount", order.getPaidAmount(),
        "currency", order.getCurrency(),
        "provider", ctx.provider(),
        "providerTransactionId", ctx.providerTxId(),
        "paidAt", now.toString()
    );

    UUID transitionEventId = UUID.nameUUIDFromBytes(
        (eventType + ":" + event.getId()).getBytes(StandardCharsets.UTF_8));

    // Enqueue reliable Outbox Event for Merchant Webhook delivery
    outboxService.enqueue(
        "COLLECTION", eventType, transitionEventId, order.getStatus().name().toLowerCase(), payload
    );

    // Dispatch directly to merchant webhook delivery queue with unique transition event ID
    webhookDispatcher.dispatchEvent(order.getOrganizationId(), transitionEventId, eventType, payload);

    log.info("[COLLECTION-FINALIZED] Order [{}] updated to {} (paid={}, expected={}) [transitionEventId={}]",
        order.getId(), order.getStatus(), totalPaid, order.getExpectedAmount(), transitionEventId);
    return order.getStatus();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markEventPendingRecovery(UUID eventId, String reason) {
    eventRepository.findById(eventId).ifPresent(e -> {
      int nextCount = e.getRetryCount() + 1;
      e.setRetryCount(nextCount);
      e.setErrorMessage(reason);
      Instant now = Instant.now();
      if (nextCount >= 5) {
        e.setStatus(InboundPaymentStatus.DEAD_LETTER);
        log.error("[COLLECTION-RECOVERY] Event [{}] exceeded max retries (5), moved to DEAD_LETTER: {}", eventId, reason);
      } else {
        e.setStatus(InboundPaymentStatus.PENDING_RECOVERY);
        e.setNextRetryAt(now.plusSeconds(30L * (1L << Math.min(nextCount, 6))));
        log.warn("[COLLECTION-RECOVERY] Event [{}] marked PENDING_RECOVERY attempt #{}: {}", eventId, nextCount, reason);
      }
      eventRepository.save(e);
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markEventFailed(UUID eventId, String reason) {
    eventRepository.findById(eventId).ifPresent(e -> {
      e.setStatus(InboundPaymentStatus.FAILED);
      e.setErrorMessage(reason);
      e.setProcessedAt(Instant.now());
      eventRepository.save(e);
    });
  }

  public record PaymentPreparedContext(
      UUID orderId,
      UUID organizationId,
      String merchantOrderId,
      UUID escrowAccountId,
      UUID eventId,
      String provider,
      String providerTxId,
      BigDecimal amount,
      String currency,
      UUID claimToken
  ) {}

  private void releaseClaim(InboundPaymentEventEntity event) {
    event.setClaimToken(null);
    event.setClaimedAt(null);
    event.setClaimExpiresAt(null);
  }
}
