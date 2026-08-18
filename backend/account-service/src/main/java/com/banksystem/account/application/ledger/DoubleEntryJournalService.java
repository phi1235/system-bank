package com.banksystem.account.application.ledger;

import com.banksystem.account.domain.ledger.FinancialEventEntity;
import com.banksystem.account.domain.ledger.FinancialEventRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerJournalEntity;
import com.banksystem.account.domain.ledger.LedgerJournalRepository;
import com.banksystem.account.domain.ledger.LedgerPostingEntity;
import com.banksystem.account.domain.ledger.LedgerPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.banksystem.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shadow double-entry writer used during expand/validate before ledger cutover. */
@Service
public class DoubleEntryJournalService {
  private static final Logger log = LoggerFactory.getLogger(DoubleEntryJournalService.class);
  private static final Pattern UUID_PATTERN = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

  private final LedgerJournalRepository journalRepository;
  private final LedgerPostingRepository postingRepository;
  private final FinancialEventRepository eventRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public DoubleEntryJournalService(
      LedgerJournalRepository journalRepository,
      LedgerPostingRepository postingRepository,
      FinancialEventRepository eventRepository,
      ObjectMapper objectMapper,
      Clock clock) {
    this.journalRepository = journalRepository;
    this.postingRepository = postingRepository;
    this.eventRepository = eventRepository;
    this.objectMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    this.clock = clock;
  }

  @Transactional
  public UUID recordLegacyEntry(LedgerEntryEntity entry, String currency) {
    String commandId = "LEGACY_ENTRY:" + entry.getId();
    LedgerJournalEntity existing = journalRepository.findByBusinessCommandId(commandId).orElse(null);
    if (existing != null) {
      return existing.getId();
    }
    Instant now = clock.instant();
    String businessReference = entry.getReferenceId() == null
        ? "LEGACY:" + entry.getId() : entry.getReferenceId();
    UUID transactionId = extractTransactionId(businessReference);
    LedgerJournalEntity journal = LedgerJournalEntity.draft(
        UUID.randomUUID(), commandId, businessReference, transactionId,
        "LEGACY_" + entry.getEntryType(), currency, entry.getDescription(), now);
    // Flush the journal first because postings deliberately keep only the journal id,
    // not a JPA association. This guarantees deterministic FK ordering in every provider.
    journalRepository.saveAndFlush(journal);

    String counterSide = "DEBIT".equals(entry.getEntryType()) ? "CREDIT" : "DEBIT";
    LedgerPostingEntity customer = LedgerPostingEntity.of(
        journal.getId(), entry.getAccountId(), "ACCOUNT:" + entry.getAccountId(),
        entry.getEntryType(), entry.getAmount(), currency, now);
    LedgerPostingEntity clearing = LedgerPostingEntity.of(
        journal.getId(), null, "LEGACY_CLEARING:" + currency,
        counterSide, entry.getAmount(), currency, now);
    postingRepository.saveAllAndFlush(List.of(customer, clearing));
    journal.post(now);
    journalRepository.saveAndFlush(journal);
    eventRepository.save(toEvent(journal, now));
    log.info("[LEDGER-POST] Journal=[{}] Type=[{}] TxId=[{}] Ref=[{}] Amount={} {} (Customer={}, Clearing={})",
        journal.getId(), journal.getJournalType(), transactionId, businessReference, entry.getAmount(), currency,
        entry.getEntryType(), counterSide);
    return journal.getId();
  }

  @Transactional
  public UUID reverse(UUID originalJournalId, String commandId, String reason) {
    LedgerJournalEntity duplicate = journalRepository.findByBusinessCommandId(commandId).orElse(null);
    if (duplicate != null) return duplicate.getId();

    LedgerJournalEntity original = journalRepository.findById(originalJournalId).orElseThrow(() ->
        new BusinessException("LEDGER_JOURNAL_NOT_FOUND", "Ledger journal not found"));
    if (!"POSTED".equals(original.getStatus()) || original.getReversalOfJournalId() != null) {
      throw new BusinessException(
          "JOURNAL_NOT_REVERSIBLE", "Only an original posted journal can be reversed");
    }
    List<LedgerPostingEntity> originalPostings =
        postingRepository.findByJournalIdOrderByCreatedAtAsc(originalJournalId);
    if (originalPostings.size() < 2) {
      throw new BusinessException("JOURNAL_EVIDENCE_INCOMPLETE", "Original postings are incomplete");
    }

    Instant now = clock.instant();
    int sequence = original.getTransactionId() == null
        ? 1 : journalRepository.maxSequence(original.getTransactionId(), "REVERSAL") + 1;
    LedgerJournalEntity reversal = LedgerJournalEntity.draft(
        UUID.randomUUID(), commandId, original.getBusinessReference(), original.getTransactionId(),
        "REVERSAL", original.getCurrency(), reason, sequence, original.getId(), now);
    journalRepository.saveAndFlush(reversal);
    List<LedgerPostingEntity> reversedPostings = originalPostings.stream()
        .map(posting -> LedgerPostingEntity.of(
            reversal.getId(), posting.getAccountId(), posting.getLedgerAccountCode(),
            opposite(posting.getSide()), posting.getAmount(), posting.getCurrency(), now))
        .toList();
    postingRepository.saveAllAndFlush(reversedPostings);
    reversal.post(now);
    journalRepository.saveAndFlush(reversal);
    eventRepository.save(toEvent(reversal, "JOURNAL_REVERSED", now));
    log.info("[LEDGER-REVERSAL] Reversed original journal [{}] with new journal [{}] Sequence={} Reason=[{}]",
        originalJournalId, reversal.getId(), sequence, reason);
    return reversal.getId();
  }

  private FinancialEventEntity toEvent(LedgerJournalEntity journal, Instant now) {
    return toEvent(journal, "JOURNAL_POSTED", now);
  }

  private FinancialEventEntity toEvent(
      LedgerJournalEntity journal, String eventType, Instant now) {
    Map<String, Object> payload = Map.of(
        "journalId", journal.getId().toString(),
        "businessReference", journal.getBusinessReference(),
        "journalType", journal.getJournalType(),
        "status", journal.getStatus(),
        "currency", journal.getCurrency());
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot serialize financial event", exception);
    }
    String eventKey = eventType + ":" + journal.getId();
    return FinancialEventEntity.of(
        UUID.nameUUIDFromBytes(eventKey.getBytes(StandardCharsets.UTF_8)), journal.getId(),
        eventType, journal.getTransactionId(), now, json, sha256(json));
  }

  private String opposite(String side) {
    return "DEBIT".equals(side) ? "CREDIT" : "DEBIT";
  }

  private UUID extractTransactionId(String reference) {
    if (reference == null) return null;
    Matcher matcher = UUID_PATTERN.matcher(reference);
    return matcher.find() ? UUID.fromString(matcher.group()) : null;
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
