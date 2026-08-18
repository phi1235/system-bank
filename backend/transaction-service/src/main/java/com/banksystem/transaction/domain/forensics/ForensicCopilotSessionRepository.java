package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForensicCopilotSessionRepository
    extends JpaRepository<ForensicCopilotSessionEntity, UUID> {
  List<ForensicCopilotSessionEntity> findTop100ByExpiresAtBefore(Instant now);
}
