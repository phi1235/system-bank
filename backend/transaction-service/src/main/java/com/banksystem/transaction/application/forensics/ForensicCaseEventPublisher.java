package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;

public interface ForensicCaseEventPublisher {
  void publish(String eventType, ForensicCaseEntity forensicCase);
}
