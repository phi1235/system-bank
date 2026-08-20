package com.banksystem.corporate.application.receipt;

import com.banksystem.corporate.domain.receipt.ReceiptArtifactEntity;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptEmailStateService {

  private final ReceiptArtifactRepository repository;

  public ReceiptEmailStateService(ReceiptArtifactRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claim(Instant now, int limit, String worker, Instant leaseUntil) {
    List<UUID> ids = repository.claimPendingEmailIds(now, limit);
    if (!ids.isEmpty()) {
      repository.markEmailSending(ids, worker, leaseUntil);
    }
    return ids;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markQueued(UUID artifactId) {
    repository.findById(artifactId).ifPresent(artifact -> {
      artifact.setEmailStatus("QUEUED");
      artifact.setEmailSent(true);
      artifact.setEmailSentAt(Instant.now());
      artifact.setEmailClaimedBy(null);
      artifact.setEmailLeaseUntil(null);
      artifact.setEmailLastError(null);
      repository.saveAndFlush(artifact);
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID artifactId, String error, int maxRetries) {
    repository.findById(artifactId).ifPresent(artifact -> {
      int retryCount = artifact.getEmailRetryCount() + 1;
      artifact.setEmailRetryCount(retryCount);
      artifact.setEmailStatus(retryCount >= maxRetries ? "DEAD_LETTER" : "PENDING");
      artifact.setEmailNextAttemptAt(
          retryCount >= maxRetries ? null : Instant.now().plusSeconds(Math.min(900, retryCount * 30L)));
      artifact.setEmailClaimedBy(null);
      artifact.setEmailLeaseUntil(null);
      artifact.setEmailLastError(sanitize(error));
      repository.saveAndFlush(artifact);
    });
  }

  private String sanitize(String error) {
    String value = error == null || error.isBlank() ? "Unknown email dispatch error" : error;
    value = value.replace('\n', ' ').replace('\r', ' ');
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
