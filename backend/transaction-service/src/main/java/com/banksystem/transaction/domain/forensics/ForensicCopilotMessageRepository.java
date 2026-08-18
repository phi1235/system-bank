package com.banksystem.transaction.domain.forensics;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForensicCopilotMessageRepository
    extends JpaRepository<ForensicCopilotMessageEntity, UUID> {
  List<ForensicCopilotMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
  long countBySessionId(UUID sessionId);
}
