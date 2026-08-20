package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.domain.collection.InboundPaymentEventEntity;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundRecoveryClaimService {

  private static final Logger log = LoggerFactory.getLogger(InboundRecoveryClaimService.class);

  private final InboundPaymentEventRepository eventRepository;

  public InboundRecoveryClaimService(
      InboundPaymentEventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<InboundEventClaimContext> claimPendingEventsBatch(int limit, int leaseSeconds) {
    Instant now = Instant.now();
    List<InboundPaymentEventEntity> claimedEntities = eventRepository.claimPendingEvents(now, limit);
    if (claimedEntities.isEmpty()) {
      return List.of();
    }

    List<InboundEventClaimContext> contexts = new ArrayList<>();
    for (InboundPaymentEventEntity entity : claimedEntities) {
      UUID claimToken = UUID.randomUUID();
      entity.setClaimToken(claimToken);
      entity.setClaimedAt(now);
      entity.setClaimExpiresAt(now.plusSeconds(leaseSeconds));
      eventRepository.save(entity);

      contexts.add(toContext(entity));
    }

    log.info("[INBOUND-CLAIM] Claimed {} pending inbound events for recovery", contexts.size());
    return contexts;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean recordLedgerSuccess(UUID eventId, UUID claimToken, UUID journalId) {
    Optional<InboundPaymentEventEntity> opt = eventRepository.findByIdForUpdate(eventId);
    if (opt.isEmpty()) {
      return false;
    }

    InboundPaymentEventEntity entity = opt.get();
    if (!ownsClaim(entity, claimToken)) {
      log.warn("[INBOUND-CLAIM] Ignoring stale ledger result for event [{}]", eventId);
      return false;
    }
    entity.setLedgerJournalId(journalId);
    entity.setStatus(InboundPaymentStatus.FINALIZE_PENDING);
    eventRepository.save(entity);
    log.info("[INBOUND-CLAIM] Event [{}] recorded ledgerJournalId={}, set FINALIZE_PENDING", eventId, journalId);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailedOrRetry(UUID eventId, UUID claimToken, String reason) {
    Instant now = Instant.now();
    Optional<InboundPaymentEventEntity> opt = eventRepository.findByIdForUpdate(eventId);
    if (opt.isEmpty()) {
      return false;
    }

    InboundPaymentEventEntity entity = opt.get();
    if (!ownsClaim(entity, claimToken) || entity.getStatus() == InboundPaymentStatus.PROCESSED) {
      log.warn("[INBOUND-CLAIM] Ignoring stale recovery failure for event [{}]", eventId);
      return false;
    }
    int nextCount = entity.getRetryCount() + 1;
    entity.setRetryCount(nextCount);
    entity.setErrorMessage(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
    entity.setClaimToken(null);
    entity.setClaimedAt(null);
    entity.setClaimExpiresAt(null);

    if (nextCount >= 5) {
      entity.setStatus(InboundPaymentStatus.DEAD_LETTER);
      log.error("[INBOUND-CLAIM] Event [{}] exceeded max retries (5), marked DEAD_LETTER: {}", eventId, reason);
    } else {
      entity.setStatus(InboundPaymentStatus.PENDING_RECOVERY);
      long backoffSeconds = 30L * (1L << Math.min(nextCount, 6));
      entity.setNextRetryAt(now.plusSeconds(backoffSeconds));
      log.warn("[INBOUND-CLAIM] Event [{}] marked PENDING_RECOVERY (retry={}, nextRetryAt={}): {}",
          eventId, nextCount, entity.getNextRetryAt(), reason);
    }

    eventRepository.save(entity);
    return true;
  }

  private boolean ownsClaim(InboundPaymentEventEntity entity, UUID claimToken) {
    return claimToken != null && Objects.equals(entity.getClaimToken(), claimToken);
  }

  private InboundEventClaimContext toContext(InboundPaymentEventEntity entity) {
    return new InboundEventClaimContext(
        entity.getId(),
        entity.getProvider(),
        entity.getProviderTransactionId(),
        entity.getVirtualAccountNumber(),
        entity.getBankBin(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getStatus(),
        entity.getLedgerJournalId(),
        entity.getRetryCount(),
        entity.getClaimToken()
    );
  }
}
