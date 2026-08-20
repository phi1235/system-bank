package com.banksystem.transaction.infrastructure.forensics;

import com.banksystem.transaction.application.forensics.ForensicCaseEventPublisher;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxForensicCaseEventPublisher implements ForensicCaseEventPublisher {
  private final OutboxService outboxService;
  private final Clock clock;

  public OutboxForensicCaseEventPublisher(OutboxService outboxService, Clock clock) {
    this.outboxService = outboxService;
    this.clock = clock;
  }

  @Override
  public void publish(String eventType, ForensicCaseEntity forensicCase) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("caseId", forensicCase.getId().toString());
    data.put("caseNumber", forensicCase.getCaseNumber());
    data.put("transactionId", string(forensicCase.getTransactionId()));
    data.put("status", forensicCase.getStatus().name());
    data.put("priority", forensicCase.getPriority().name());
    data.put("assignedTo", string(forensicCase.getAssignedTo()));
    data.put("actionPath", "/admin/forensics?caseId=" + forensicCase.getId());

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", UUID.nameUUIDFromBytes(
        (eventType + ":" + forensicCase.getId() + ":" + forensicCase.getVersion())
            .getBytes(StandardCharsets.UTF_8)).toString());
    envelope.put("eventType", eventType);
    envelope.put("occurredAt", clock.instant().toString());
    envelope.put("data", data);
    outboxService.enqueue(
        "FORENSIC_CASE", eventType, forensicCase.getId(),
        eventType + ":" + forensicCase.getVersion(), envelope);
  }

  private String string(Object value) { return value == null ? null : value.toString(); }
}
