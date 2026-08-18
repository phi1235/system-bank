package com.banksystem.account.application.ledger;

import com.banksystem.account.api.dto.LedgerEvidenceDtos.AccountStateEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.FinancialEventEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.FinancialEvidenceSearchResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.HoldEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.JournalEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.LegacyEntryEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.PostingEvidenceResponse;
import com.banksystem.account.api.dto.LedgerEvidenceDtos.TransactionLedgerEvidenceResponse;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.AccountHoldEntity;
import com.banksystem.account.domain.ledger.AccountHoldRepository;
import com.banksystem.account.domain.ledger.FinancialEventEntity;
import com.banksystem.account.domain.ledger.FinancialEventRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.account.domain.ledger.LedgerJournalEntity;
import com.banksystem.account.domain.ledger.LedgerJournalRepository;
import com.banksystem.account.domain.ledger.LedgerPostingEntity;
import com.banksystem.account.domain.ledger.LedgerPostingRepository;
import com.banksystem.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEvidenceQueryService {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final LedgerJournalRepository journalRepository;
  private final LedgerPostingRepository postingRepository;
  private final LedgerEntryRepository entryRepository;
  private final AccountHoldRepository holdRepository;
  private final FinancialEventRepository eventRepository;
  private final AccountRepository accountRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final AccountTemporalSnapshotService temporalSnapshotService;

  public LedgerEvidenceQueryService(
      LedgerJournalRepository journalRepository,
      LedgerPostingRepository postingRepository,
      LedgerEntryRepository entryRepository,
      AccountHoldRepository holdRepository,
      FinancialEventRepository eventRepository,
      AccountRepository accountRepository,
      ObjectMapper objectMapper,
      Clock clock,
      AccountTemporalSnapshotService temporalSnapshotService) {
    this.journalRepository = journalRepository;
    this.postingRepository = postingRepository;
    this.entryRepository = entryRepository;
    this.holdRepository = holdRepository;
    this.eventRepository = eventRepository;
    this.accountRepository = accountRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.temporalSnapshotService = temporalSnapshotService;
  }

  @Transactional(readOnly = true)
  public FinancialEvidenceSearchResponse search(Collection<String> references) {
    List<String> normalized = references.stream().map(String::trim).distinct().limit(100).toList();
    List<LedgerJournalEntity> journals =
        journalRepository.findByBusinessReferenceInOrderByCreatedAtAsc(normalized);
    List<LegacyEntryEvidenceResponse> compatibility = entryRepository.findByReferenceIdIn(normalized)
        .stream().map(this::toLegacy).toList();
    String completeness = journals.isEmpty() ? compatibility.isEmpty() ? "EMPTY" : "PARTIAL" : "COMPLETE";
    return new FinancialEvidenceSearchResponse(
        journals.stream().map(this::toJournal).toList(), compatibility, completeness);
  }

  @Transactional(readOnly = true)
  public JournalEvidenceResponse journal(UUID id) {
    return toJournal(journalRepository.findById(id).orElseThrow(() ->
        new BusinessException("LEDGER_JOURNAL_NOT_FOUND", "Ledger journal not found")));
  }

  @Transactional(readOnly = true)
  public AccountStateEvidenceResponse accountState(UUID accountId, Instant requestedAt) {
    AccountEntity account = accountRepository.findById(accountId).orElseThrow(() ->
        new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    Instant now = clock.instant();
    Instant at = requestedAt == null ? now : requestedAt;
    if (at.isAfter(now)) {
      throw new BusinessException("INVALID_TEMPORAL_POINT", "Temporal point cannot be in the future");
    }
    var snapshot = requestedAt == null
        ? java.util.Optional.<AccountTemporalSnapshotService.SnapshotState>empty()
        : temporalSnapshotService.nearest(accountId, at);
    BigDecimal balance = requestedAt == null
        ? account.getBalance()
        : snapshot.map(value -> value.balance().add(
            temporalSnapshotService.replayDelta(accountId, value.at(), at)))
            .orElseGet(() -> entryRepository.balanceAt(accountId, at));
    BigDecimal held = holdRepository.activeAmountAt(accountId, at);
    return new AccountStateEvidenceResponse(
        accountId.toString(), account.getCurrency(), balance, held, balance.subtract(held), at,
        requestedAt == null || snapshot.isPresent() ? "COMPLETE" : "PARTIAL");
  }

  @Transactional(readOnly = true)
  public TransactionLedgerEvidenceResponse transaction(UUID transactionId) {
    List<JournalEvidenceResponse> journals = journalRepository
        .findByTransactionIdOrderByCreatedAtAsc(transactionId).stream().map(this::toJournal).toList();
    List<HoldEvidenceResponse> holds = holdRepository
        .findByTransactionIdOrderByCreatedAtAsc(transactionId).stream().map(this::toHold).toList();
    List<FinancialEventEvidenceResponse> events = eventRepository
        .findByTransactionIdOrderByOccurredAtAsc(transactionId).stream().map(this::toEvent).toList();
    String completeness = journals.isEmpty() ? "EMPTY" : events.isEmpty() ? "PARTIAL" : "COMPLETE";
    return new TransactionLedgerEvidenceResponse(
        transactionId.toString(), journals, holds, events, completeness);
  }

  private JournalEvidenceResponse toJournal(LedgerJournalEntity entity) {
    List<PostingEvidenceResponse> postings = postingRepository
        .findByJournalIdOrderByCreatedAtAsc(entity.getId()).stream().map(this::toPosting).toList();
    return new JournalEvidenceResponse(
        entity.getId().toString(), entity.getBusinessCommandId(), entity.getBusinessReference(),
        string(entity.getTransactionId()), entity.getJournalType(), entity.getStatus(),
        entity.getCurrency(), entity.getDescription(), string(entity.getReversalOfJournalId()),
        entity.getSequenceNo(), entity.getCreatedAt(), entity.getPostedAt(), postings);
  }

  private PostingEvidenceResponse toPosting(LedgerPostingEntity entity) {
    return new PostingEvidenceResponse(
        entity.getId().toString(), string(entity.getAccountId()), entity.getLedgerAccountCode(),
        entity.getSide(), entity.getAmount(), entity.getCurrency(), entity.getCreatedAt());
  }

  private LegacyEntryEvidenceResponse toLegacy(LedgerEntryEntity entity) {
    return new LegacyEntryEvidenceResponse(
        entity.getId().toString(), entity.getAccountId().toString(), entity.getEntryType(),
        entity.getAmount(), entity.getReferenceId(), entity.getDescription(), entity.getCreatedAt());
  }

  private HoldEvidenceResponse toHold(AccountHoldEntity entity) {
    return new HoldEvidenceResponse(
        entity.getId().toString(), entity.getAccountId().toString(), entity.getTransactionId().toString(),
        entity.getAmount(), entity.getCurrency(), entity.getStatus(), entity.getExpiresAt(),
        string(entity.getCapturedJournalId()), entity.getCreatedAt(), entity.getUpdatedAt());
  }

  private FinancialEventEvidenceResponse toEvent(FinancialEventEntity entity) {
    return new FinancialEventEvidenceResponse(
        entity.getEventId().toString(), entity.getAggregateType(), entity.getAggregateId().toString(),
        entity.getSequenceNo(), entity.getEventType(), entity.getSchemaVersion(),
        string(entity.getTransactionId()), entity.getOccurredAt(), parse(entity.getPayloadJson()),
        entity.getPayloadSha256());
  }

  private Map<String, Object> parse(String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (JsonProcessingException exception) {
      return Map.of("status", "CORRUPTED");
    }
  }

  private String string(Object value) { return value == null ? null : value.toString(); }
}
