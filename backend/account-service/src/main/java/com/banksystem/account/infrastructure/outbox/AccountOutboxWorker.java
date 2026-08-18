package com.banksystem.account.infrastructure.outbox;

import com.banksystem.account.domain.ledger.AccountOutboxEntity;
import com.banksystem.account.domain.ledger.AccountOutboxRepository;
import com.banksystem.account.infrastructure.feign.TransactionRemediationClient;
import com.banksystem.account.infrastructure.feign.TransactionRemediationClient.RemediationPostedEventRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountOutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(AccountOutboxWorker.class);
  private static final String WORKER_ID = "account-service-outbox-worker-1";

  private final AccountOutboxRepository outboxRepository;
  private final TransactionRemediationClient remediationClient;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  @Value("${bank.internal-api-key:internal-secret-key}")
  private String internalApiKey;

  public AccountOutboxWorker(
      AccountOutboxRepository outboxRepository,
      TransactionRemediationClient remediationClient,
      Clock clock,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.remediationClient = remediationClient;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelay = 5000)
  public void processOutboxEvents() {
    Instant now = clock.instant();

    // 1. Atomic Claim using FOR UPDATE SKIP LOCKED & Lease Expiration Recovery
    List<AccountOutboxEntity> claimedEvents = claimEventsInTx(now);
    if (claimedEvents.isEmpty()) {
      return;
    }

    log.info("Account outbox worker claimed {} pending REMEDIATION_POSTED events", claimedEvents.size());

    // 2. Deliver event to transaction-service Result Inbox (OUTSIDE DB TRANSACTION)
    for (AccountOutboxEntity event : claimedEvents) {
      try {
        deliverResultEvent(event);
        markProcessedInTx(event.getId(), clock.instant());
      } catch (Exception ex) {
        log.error("Account outbox delivery failed for event {}", event.getId(), ex);
        markFailedInTx(event.getId(), ex.getMessage(), clock.instant());
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<AccountOutboxEntity> claimEventsInTx(Instant now) {
    List<AccountOutboxEntity> pending = outboxRepository.claimPendingEventsNative(now, 10);
    List<AccountOutboxEntity> claimed = new ArrayList<>();
    Instant leaseExpiration = now.plusSeconds(60); // 60-second lease

    for (AccountOutboxEntity entity : pending) {
      try {
        entity.markSending(WORKER_ID, now, leaseExpiration);
        outboxRepository.save(entity);
        claimed.add(entity);
      } catch (Exception e) {
        log.warn("Failed claiming account outbox event {}", entity.getId(), e);
      }
    }
    return claimed;
  }

  private void deliverResultEvent(AccountOutboxEntity event) throws Exception {
    Map<String, Object> payloadMap = objectMapper.readValue(
        event.getPayload(), new TypeReference<Map<String, Object>>() {});

    UUID eventId = UUID.fromString((String) payloadMap.get("eventId"));
    UUID proposalId = UUID.fromString((String) payloadMap.get("proposalId"));
    UUID caseId = UUID.fromString((String) payloadMap.get("caseId"));
    int cycle = (Integer) payloadMap.get("investigationCycle");
    String referenceId = (String) payloadMap.get("referenceId");
    String targetAccountId = (String) payloadMap.get("targetAccountId");

    RemediationPostedEventRequest request = new RemediationPostedEventRequest(
        eventId, proposalId, caseId, cycle, referenceId, targetAccountId);

    remediationClient.processResultInbox(request, internalApiKey);
    log.info("Successfully delivered REMEDIATION_POSTED event {} to transaction-service", eventId);
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
      long backoffSeconds = (long) Math.pow(2, Math.min(nextRetry, 6)) * 5;
      Instant nextAttempt = now.plusSeconds(backoffSeconds);
      boolean deadLetter = nextRetry >= 5;

      event.markFailed(errorMessage, nextAttempt, deadLetter);
      outboxRepository.save(event);
      log.warn("Account outbox event {} delivery failed (Attempt {}/5). Next attempt: {}. DeadLetter: {}",
          eventId, nextRetry, nextAttempt, deadLetter);
    });
  }
}
