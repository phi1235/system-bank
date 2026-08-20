package com.banksystem.corporate.application.outbox;

import com.banksystem.corporate.domain.outbox.CorporateOutboxEventEntity;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporateOutboxStateService {

  private final CorporateOutboxEventRepository repository;

  public CorporateOutboxStateService(CorporateOutboxEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claim(
      Instant now, int limit, String worker, Instant leaseUntil) {
    List<UUID> ids = repository.claimEvents(now, limit);
    if (!ids.isEmpty()) {
      repository.markEventsSending(ids, worker, leaseUntil);
    }
    return ids;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processed(UUID eventId) {
    CorporateOutboxEventEntity event = repository.findById(eventId).orElseThrow();
    event.setStatus("PROCESSED");
    event.setProcessedAt(Instant.now());
    event.setClaimedBy(null);
    event.setLeaseUntil(null);
    event.setLastError(null);
    repository.saveAndFlush(event);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(UUID eventId, String error, int maxRetries) {
    CorporateOutboxEventEntity event = repository.findById(eventId).orElseThrow();
    int retryCount = event.getRetryCount() + 1;
    event.setRetryCount(retryCount);
    event.setStatus(retryCount >= maxRetries ? "DEAD_LETTER" : "PENDING");
    event.setNextAttemptAt(
        retryCount >= maxRetries ? event.getNextAttemptAt() : Instant.now().plusSeconds(retryCount * 5L));
    event.setLastError(sanitize(error));
    event.setClaimedBy(null);
    event.setLeaseUntil(null);
    repository.saveAndFlush(event);
  }

  private String sanitize(String error) {
    String value = error == null || error.isBlank() ? "Unknown outbox dispatch error" : error;
    value = value.replace('\n', ' ').replace('\r', ' ');
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }
}
