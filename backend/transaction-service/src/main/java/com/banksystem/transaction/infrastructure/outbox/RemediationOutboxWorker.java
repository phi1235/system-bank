package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.forensics.RemediationOutboxEntity;
import com.banksystem.transaction.domain.forensics.RemediationOutboxRepository;
import com.banksystem.transaction.domain.forensics.RemediationProposalRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AdjustmentRequestedEventRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RemediationOutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(RemediationOutboxWorker.class);
  private static final String WORKER_ID = "transaction-service-outbox-worker-1";

  private final RemediationOutboxRepository outboxRepository;
  private final RemediationProposalRepository proposalRepository;
  private final AccountGateway accountGateway;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public RemediationOutboxWorker(
      RemediationOutboxRepository outboxRepository,
      RemediationProposalRepository proposalRepository,
      AccountGateway accountGateway,
      Clock clock,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.proposalRepository = proposalRepository;
    this.accountGateway = accountGateway;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelay = 5000)
  public void processOutboxEvents() {
    Instant now = clock.instant();

    // 1. DB TX #1: Atomic Claim using FOR UPDATE SKIP LOCKED & Lease Expiration Recovery
    List<RemediationOutboxEntity> claimedEvents = claimEventsInTx(now);
    if (claimedEvents.isEmpty()) {
      return;
    }

    log.info("Outbox worker claimed {} pending/stale-lease remediation events", claimedEvents.size());

    // 2. Network Event Delivery to AccountInboxService (OUTSIDE DB TRANSACTION)
    for (RemediationOutboxEntity event : claimedEvents) {
      try {
        deliverEventToAccountInbox(event);
        markProcessedInTx(event.getId(), clock.instant());
      } catch (Exception ex) {
        log.error("Outbox delivery failed for event {}", event.getId(), ex);
        markFailedInTx(event.getId(), ex.getMessage(), clock.instant());
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<RemediationOutboxEntity> claimEventsInTx(Instant now) {
    List<RemediationOutboxEntity> pending = outboxRepository.claimPendingEventsNative(now, 10);
    List<RemediationOutboxEntity> claimed = new ArrayList<>();
    Instant leaseExpiration = now.plusSeconds(60); // 60-second lease

    for (RemediationOutboxEntity entity : pending) {
      try {
        entity.markSending(WORKER_ID, now, leaseExpiration);
        outboxRepository.save(entity);
        claimed.add(entity);
      } catch (Exception e) {
        log.warn("Failed claiming event {}", entity.getId(), e);
      }
    }
    return claimed;
  }

  private void deliverEventToAccountInbox(RemediationOutboxEntity event) throws Exception {
    Map<String, Object> payloadMap = objectMapper.readValue(
        event.getPayload(), new TypeReference<Map<String, Object>>() {});

    UUID eventId = UUID.fromString((String) payloadMap.get("eventId"));
    UUID proposalId = UUID.fromString((String) payloadMap.get("proposalId"));
    UUID caseId = UUID.fromString((String) payloadMap.get("caseId"));
    int cycle = (Integer) payloadMap.get("investigationCycle");
    UUID targetAccountId = UUID.fromString((String) payloadMap.get("targetAccountId"));
    String direction = (String) payloadMap.get("direction");
    BigDecimal amount = new BigDecimal((String) payloadMap.get("amount"));
    String currency = (String) payloadMap.get("currency");
    String referenceId = (String) payloadMap.get("referenceId");
    String reason = (String) payloadMap.get("reason");

    AdjustmentRequestedEventRequest request = new AdjustmentRequestedEventRequest(
        eventId, proposalId, caseId, cycle, targetAccountId, direction, amount, currency, referenceId, reason);

    // Call AccountInboxService via Feign Gateway (Guarantees AccountInboxService execution path!)
    boolean result = accountGateway.processRemediationInbox(request);
    log.info("Delivered event {} to AccountInboxService. Result: {}", eventId, result);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markProcessedInTx(UUID eventId, Instant now) {
    outboxRepository.findById(eventId).ifPresent(event -> {
      event.markProcessed(now);
      outboxRepository.save(event);
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailedInTx(UUID eventId, String errorMessage, Instant now) {
    outboxRepository.findById(eventId).ifPresent(event -> {
      int nextRetry = event.getRetryCount() + 1;
      long backoffSeconds = (long) Math.pow(2, Math.min(nextRetry, 6)) * 5; // Exponential backoff
      Instant nextAttempt = now.plusSeconds(backoffSeconds);
      boolean deadLetter = nextRetry >= 5;

      event.markFailed(errorMessage, nextAttempt, deadLetter);
      outboxRepository.save(event);

      if (deadLetter) {
        proposalRepository.findById(event.getAggregateId()).ifPresent(proposal -> {
          proposal.markExecutionFailed("Dead-Letter: Max retries (5) exceeded delivering to account-service: " + errorMessage, now);
          proposalRepository.save(proposal);
          log.error("CRITICAL ALERT: Proposal {} execution DEAD_LETTER. Transitioned to EXECUTION_FAILED for manual intervention", proposal.getId());
        });
      }

      log.warn("Event {} delivery failed (Attempt {}/5). Next attempt: {}. DeadLetter: {}",
          eventId, nextRetry, nextAttempt, deadLetter);
    });
  }
}
