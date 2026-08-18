package com.banksystem.account.application.ledger;

import com.banksystem.account.application.account.AccountMoneyService;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.domain.ledger.AccountInboxRepository;
import com.banksystem.account.domain.ledger.AccountOutboxEntity;
import com.banksystem.account.domain.ledger.AccountOutboxRepository;
import com.banksystem.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountInboxService {
  private static final Logger log = LoggerFactory.getLogger(AccountInboxService.class);

  private final AccountInboxRepository inboxRepository;
  private final AccountOutboxRepository outboxRepository;
  private final AccountMoneyService moneyService;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public AccountInboxService(
      AccountInboxRepository inboxRepository,
      AccountOutboxRepository outboxRepository,
      AccountMoneyService moneyService,
      Clock clock,
      ObjectMapper objectMapper) {
    this.inboxRepository = inboxRepository;
    this.outboxRepository = outboxRepository;
    this.moneyService = moneyService;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public boolean processAdjustmentRequestedEvent(
      UUID eventId,
      UUID proposalId,
      UUID caseId,
      int cycle,
      UUID targetAccountId,
      String direction,
      BigDecimal amount,
      String currency,
      String referenceId,
      String reason) {
    Instant now = clock.instant();

    // 1. ATOMIC DB INBOX DEDUPLICATION (ON CONFLICT DO NOTHING)
    int inserted = inboxRepository.insertIfNotExistsNative(eventId, "ADJUSTMENT_REQUESTED", now);
    if (inserted == 0) {
      log.info("Duplicate inbox event {} received, acknowledging safely without duplicate posting", eventId);
      return false; // Safe ACK for duplicate delivery
    }

    // 2. EXECUTE MONEY SERVICE CREDIT / DEBIT (HANDLES SELECT FOR UPDATE, BALANCE & UNICITY)
    MoneyCommand command = new MoneyCommand(amount, referenceId, reason != null ? reason : "Remediation Posting", referenceId);
    MoneyResult result;
    if ("DEBIT".equalsIgnoreCase(direction)) {
      result = moneyService.debit(targetAccountId, command);
    } else {
      result = moneyService.credit(targetAccountId, command);
    }

    // 3. INSERT REMEDIATION_POSTED OUTBOX EVENT IN SAME LOCAL DB TRANSACTION
    enqueueRemediationPostedOutbox(proposalId, caseId, cycle, referenceId, targetAccountId, amount, now);

    log.info("Successfully posted adjustment entry {} (Ledger ID: {}) for proposal {}", referenceId, result.ledgerEntryId(), proposalId);
    return true;
  }

  private void enqueueRemediationPostedOutbox(
      UUID proposalId, UUID caseId, int cycle, String referenceId, UUID targetAccountId, BigDecimal amount, Instant now) {
    UUID outboxEventId = UUID.randomUUID();
    Map<String, Object> payloadMap = Map.of(
        "eventId", outboxEventId.toString(),
        "eventType", "REMEDIATION_POSTED",
        "schemaVersion", 1,
        "proposalId", proposalId.toString(),
        "caseId", caseId.toString(),
        "investigationCycle", cycle,
        "referenceId", referenceId,
        "targetAccountId", targetAccountId.toString(),
        "amount", amount.toPlainString(),
        "postedAt", now.toString()
    );

    try {
      String jsonPayload = objectMapper.writeValueAsString(payloadMap);
      AccountOutboxEntity outbox = AccountOutboxEntity.create(
          outboxEventId, proposalId, "REMEDIATION_POSTED", 1, jsonPayload, now);
      outboxRepository.save(outbox);
    } catch (JsonProcessingException e) {
      throw new BusinessException("OUTBOX_SERIALIZATION_FAILED", "Failed to serialize account outbox event", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
