package com.banksystem.transaction.domain.forensics;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForensicVerificationRunRepository
    extends JpaRepository<ForensicVerificationRunEntity, UUID> {
  Optional<ForensicVerificationRunEntity> findByRequestedByAndIdempotencyKey(
      UUID requestedBy, String idempotencyKey);
}
