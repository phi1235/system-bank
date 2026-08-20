package com.banksystem.account.application.ledger;

import com.banksystem.account.api.dto.AtomicLedgerDtos.AtomicPostingResponse;
import com.banksystem.account.api.dto.AtomicLedgerDtos.CollectionReceiptCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.PayoutClearingCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.SettlementLegCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.SettlementPostingCommand;
import com.banksystem.account.api.dto.AtomicLedgerDtos.SettlementReversalCommand;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtomicLedgerPostingService {

  private static final Logger log = LoggerFactory.getLogger(AtomicLedgerPostingService.class);

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final LedgerJournalRepository journalRepository;
  private final LedgerPostingRepository postingRepository;
  private final FinancialEventRepository eventRepository;
  private final ObjectMapper objectMapper;

  public AtomicLedgerPostingService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      LedgerJournalRepository journalRepository,
      LedgerPostingRepository postingRepository,
      FinancialEventRepository eventRepository,
      ObjectMapper objectMapper) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.journalRepository = journalRepository;
    this.postingRepository = postingRepository;
    this.eventRepository = eventRepository;
    this.objectMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  @Transactional
  public AtomicPostingResponse recordCollectionReceipt(CollectionReceiptCommand command) {
    String payloadHash = calculateCommandHash(command);
    Optional<LedgerJournalEntity> existing = journalRepository.findByBusinessCommandId(command.businessCommandId());
    if (existing.isPresent()) {
      LedgerJournalEntity journal = existing.get();
      // Verify idempotency payload fingerprint
      if (journal.getRequestHash() != null && !journal.getRequestHash().equalsIgnoreCase(payloadHash)) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Command ID " + command.businessCommandId() + " was previously used with different parameters");
      }
      log.info("[COLLECTION-RECEIPT] Idempotent hit for commandId={}, journalId={}", command.businessCommandId(), journal.getId());
      return new AtomicPostingResponse(
          journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
          journal.getCurrency(), command.amount(), journal.getPostedAt() != null ? journal.getPostedAt() : journal.getCreatedAt()
      );
    }

    AccountEntity account = accountRepository.findByIdForUpdate(command.collectionAccountId())
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Collection account not found"));

    if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
      throw new BusinessException("ACCOUNT_INACTIVE", "Collection account is not active");
    }
    if (!account.getCurrency().equalsIgnoreCase(command.currency())) {
      throw new BusinessException("CURRENCY_MISMATCH", "Collection account currency mismatch");
    }

    Instant now = Instant.now();

    int updated = accountRepository.creditIfActive(account.getId(), command.amount());
    if (updated == 0) {
      throw new BusinessException("ACCOUNT_CREDIT_FAILED", "Failed to credit collection account");
    }

    LedgerEntryEntity entry = new LedgerEntryEntity();
    entry.setId(UUID.randomUUID());
    entry.setAccountId(account.getId());
    entry.setEntryType("CREDIT");
    entry.setAmount(command.amount());
    entry.setReferenceId(command.businessReference());
    entry.setDescription(command.description() != null ? command.description() : "Collection receipt");
    entry.setCreatedAt(now);
    ledgerEntryRepository.save(entry);

    LedgerJournalEntity journal = LedgerJournalEntity.draft(
        UUID.randomUUID(), command.businessCommandId(), command.businessReference(), command.transactionId(),
        "COLLECTION_RECEIPT", command.currency().toUpperCase(), command.description(), now
    );
    journal.setRequestHash(payloadHash);
    journalRepository.saveAndFlush(journal);

    LedgerPostingEntity clearingPosting = LedgerPostingEntity.of(
        journal.getId(), null, command.clearingAccountCode(), "DEBIT", command.amount(), command.currency().toUpperCase(), now
    );
    LedgerPostingEntity accountPosting = LedgerPostingEntity.of(
        journal.getId(), account.getId(), "ACCOUNT:" + account.getId(), "CREDIT", command.amount(), command.currency().toUpperCase(), now
    );
    postingRepository.saveAllAndFlush(List.of(clearingPosting, accountPosting));

    journal.post(now);
    journalRepository.saveAndFlush(journal);
    eventRepository.save(toEvent(journal, "COLLECTION_RECEIPT_POSTED", now));

    log.info("[COLLECTION-RECEIPT] Posted journal=[{}] cmd=[{}] account=[{}] amount={} {}",
        journal.getId(), command.businessCommandId(), account.getId(), command.amount(), command.currency());

    return new AtomicPostingResponse(
        journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
        journal.getCurrency(), command.amount(), journal.getPostedAt()
    );
  }

  @Transactional
  public AtomicPostingResponse recordSettlement(SettlementPostingCommand command) {
    String payloadHash = calculateCommandHash(command);
    Optional<LedgerJournalEntity> existing = journalRepository.findByBusinessCommandId(command.businessCommandId());
    if (existing.isPresent()) {
      LedgerJournalEntity journal = existing.get();
      if (journal.getRequestHash() != null && !journal.getRequestHash().equalsIgnoreCase(payloadHash)) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Command ID " + command.businessCommandId() + " was previously used with different parameters");
      }
      log.info("[SETTLEMENT-POST] Idempotent hit for commandId={}, journalId={}", command.businessCommandId(), journal.getId());
      return new AtomicPostingResponse(
          journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
          journal.getCurrency(), command.grossAmount(), journal.getPostedAt() != null ? journal.getPostedAt() : journal.getCreatedAt()
      );
    }

    BigDecimal legsTotal = command.legs().stream()
        .map(SettlementLegCommand::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (legsTotal.compareTo(command.grossAmount()) != 0) {
      throw new BusinessException("SETTLEMENT_SUM_MISMATCH",
          "Sum of settlement legs (" + legsTotal + ") does not equal gross amount (" + command.grossAmount() + ")");
    }

    List<UUID> accountIdsToLock = new ArrayList<>();
    accountIdsToLock.add(command.sourceAccountId());
    for (SettlementLegCommand leg : command.legs()) {
      if (leg.accountId() != null) {
        accountIdsToLock.add(leg.accountId());
      }
    }
    // Sort account UUIDs to avoid database deadlocks
    List<UUID> sortedAccountIds = accountIdsToLock.stream()
        .distinct()
        .sorted(Comparator.comparing(UUID::toString))
        .toList();

    Map<UUID, AccountEntity> lockedAccounts = new HashMap<>();
    for (UUID accId : sortedAccountIds) {
      AccountEntity acc = accountRepository.findByIdForUpdate(accId).orElseThrow(() ->
          new BusinessException("ACCOUNT_NOT_FOUND", "Account " + accId + " not found"));
      if (!"ACTIVE".equalsIgnoreCase(acc.getStatus())) {
        throw new BusinessException("ACCOUNT_INACTIVE", "Account " + accId + " is not active");
      }
      lockedAccounts.put(accId, acc);
    }

    AccountEntity sourceAccount = lockedAccounts.get(command.sourceAccountId());
    int debited = accountRepository.debitIfSufficient(sourceAccount.getId(), command.grossAmount());
    if (debited == 0) {
      throw new BusinessException("INSUFFICIENT_FUNDS", "Insufficient funds in source account for settlement");
    }

    Instant now = Instant.now();

    LedgerEntryEntity debitEntry = new LedgerEntryEntity();
    debitEntry.setId(UUID.randomUUID());
    debitEntry.setAccountId(sourceAccount.getId());
    debitEntry.setEntryType("DEBIT");
    debitEntry.setAmount(command.grossAmount());
    debitEntry.setReferenceId(command.businessReference());
    debitEntry.setDescription("Settlement debit: " + command.description());
    debitEntry.setCreatedAt(now);
    ledgerEntryRepository.save(debitEntry);

    for (SettlementLegCommand leg : command.legs()) {
      if (leg.accountId() != null) {
        AccountEntity destAccount = lockedAccounts.get(leg.accountId());
        accountRepository.creditIfActive(destAccount.getId(), leg.amount());
        LedgerEntryEntity creditEntry = new LedgerEntryEntity();
        creditEntry.setId(UUID.randomUUID());
        creditEntry.setAccountId(destAccount.getId());
        creditEntry.setEntryType("CREDIT");
        creditEntry.setAmount(leg.amount());
        creditEntry.setReferenceId(command.businessReference());
        creditEntry.setDescription(leg.description() != null ? leg.description() : "Settlement split credit");
        creditEntry.setCreatedAt(now);
        ledgerEntryRepository.save(creditEntry);
      }
    }

    LedgerJournalEntity journal = LedgerJournalEntity.draft(
        UUID.randomUUID(), command.businessCommandId(), command.businessReference(), command.transactionId(),
        "SETTLEMENT", command.currency().toUpperCase(), command.description(), now
    );
    journal.setRequestHash(payloadHash);
    journalRepository.saveAndFlush(journal);

    List<LedgerPostingEntity> postings = new ArrayList<>();
    postings.add(LedgerPostingEntity.of(
        journal.getId(), sourceAccount.getId(), "ACCOUNT:" + sourceAccount.getId(),
        "DEBIT", command.grossAmount(), command.currency().toUpperCase(), now
    ));

    for (SettlementLegCommand leg : command.legs()) {
      postings.add(LedgerPostingEntity.of(
          journal.getId(), leg.accountId(), leg.ledgerAccountCode(),
          "CREDIT", leg.amount(), command.currency().toUpperCase(), now
      ));
    }
    postingRepository.saveAllAndFlush(postings);

    journal.post(now);
    journalRepository.saveAndFlush(journal);
    eventRepository.save(toEvent(journal, "SETTLEMENT_POSTED", now));

    log.info("[SETTLEMENT-POST] Posted journal=[{}] cmd=[{}] gross={} legs={}",
        journal.getId(), command.businessCommandId(), command.grossAmount(), command.legs().size());

    return new AtomicPostingResponse(
        journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
        journal.getCurrency(), command.grossAmount(), journal.getPostedAt()
    );
  }

  @Transactional
  public AtomicPostingResponse recordPayoutClearing(PayoutClearingCommand command) {
    String payloadHash = calculateCommandHash(command);
    Optional<LedgerJournalEntity> existing = journalRepository.findByBusinessCommandId(command.businessCommandId());
    if (existing.isPresent()) {
      LedgerJournalEntity journal = existing.get();
      if (journal.getRequestHash() != null && !journal.getRequestHash().equalsIgnoreCase(payloadHash)) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Command ID " + command.businessCommandId() + " was previously used with different parameters");
      }
      log.info("[PAYOUT-CLEARING] Idempotent hit for commandId={}, journalId={}", command.businessCommandId(), journal.getId());
      return new AtomicPostingResponse(
          journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
          journal.getCurrency(), command.amount(), journal.getPostedAt() != null ? journal.getPostedAt() : journal.getCreatedAt()
      );
    }

    Instant now = Instant.now();

    LedgerJournalEntity journal = LedgerJournalEntity.draft(
        UUID.randomUUID(), command.businessCommandId(), command.businessReference(), command.payoutId(),
        "PAYOUT_CLEARING", command.currency().toUpperCase(), command.description(), now
    );
    journal.setRequestHash(payloadHash);
    journalRepository.saveAndFlush(journal);

    LedgerPostingEntity debitPayablePosting = LedgerPostingEntity.of(
        journal.getId(), null, command.payableAccountCode(), "DEBIT", command.amount(), command.currency().toUpperCase(), now
    );
    LedgerPostingEntity creditClearingPosting = LedgerPostingEntity.of(
        journal.getId(), null, command.clearingAccountCode(), "CREDIT", command.amount(), command.currency().toUpperCase(), now
    );
    postingRepository.saveAllAndFlush(List.of(debitPayablePosting, creditClearingPosting));

    journal.post(now);
    journalRepository.saveAndFlush(journal);
    eventRepository.save(toEvent(journal, "PAYOUT_CLEARING_POSTED", now));

    log.info("[PAYOUT-CLEARING] Posted journal=[{}] cmd=[{}] amount={} {}",
        journal.getId(), command.businessCommandId(), command.amount(), command.currency());

    return new AtomicPostingResponse(
        journal.getId(), journal.getBusinessCommandId(), journal.getStatus(), journal.getJournalType(),
        journal.getCurrency(), command.amount(), journal.getPostedAt()
    );
  }

  @Transactional
  public AtomicPostingResponse reverseSettlement(UUID journalId, SettlementReversalCommand command) {
    Optional<LedgerJournalEntity> duplicate = journalRepository.findByBusinessCommandId(command.businessCommandId());
    if (duplicate.isPresent()) {
      LedgerJournalEntity rev = duplicate.get();
      return new AtomicPostingResponse(
          rev.getId(), rev.getBusinessCommandId(), rev.getStatus(), rev.getJournalType(),
          rev.getCurrency(), BigDecimal.ZERO, rev.getPostedAt()
      );
    }

    LedgerJournalEntity original = journalRepository.findById(journalId).orElseThrow(() ->
        new BusinessException("LEDGER_JOURNAL_NOT_FOUND", "Original settlement journal not found"));

    if (!"POSTED".equals(original.getStatus()) || original.getReversalOfJournalId() != null) {
      throw new BusinessException("JOURNAL_NOT_REVERSIBLE", "Only an active posted settlement journal can be reversed");
    }

    List<LedgerPostingEntity> originalPostings = postingRepository.findByJournalIdOrderByCreatedAtAsc(journalId);
    Instant now = Instant.now();

    List<UUID> accountIds = originalPostings.stream()
        .map(LedgerPostingEntity::getAccountId)
        .filter(Objects::nonNull)
        .distinct()
        .sorted(Comparator.comparing(UUID::toString))
        .toList();

    for (UUID accId : accountIds) {
      accountRepository.findByIdForUpdate(accId);
    }

    BigDecimal totalAmount = BigDecimal.ZERO;
    for (LedgerPostingEntity posting : originalPostings) {
      if ("DEBIT".equals(posting.getSide())) {
        totalAmount = totalAmount.add(posting.getAmount());
        if (posting.getAccountId() != null) {
          accountRepository.creditIfActive(posting.getAccountId(), posting.getAmount());
        }
      } else {
        if (posting.getAccountId() != null) {
          int debited = accountRepository.debitIfSufficient(posting.getAccountId(), posting.getAmount());
          if (debited == 0) {
            throw new BusinessException("REVERSAL_INSUFFICIENT_FUNDS",
                "Cannot reverse: beneficiary account " + posting.getAccountId() + " has insufficient funds");
          }
        }
      }
    }

    // Mark original journal as REVERSED
    original.markReversed();
    journalRepository.saveAndFlush(original);

    LedgerJournalEntity reversal = LedgerJournalEntity.draft(
        UUID.randomUUID(), command.businessCommandId(), "REVERSAL:" + original.getBusinessReference(),
        original.getTransactionId(), "SETTLEMENT_REVERSAL", original.getCurrency(), command.reason(),
        original.getSequenceNo() + 1, original.getId(), now
    );
    journalRepository.saveAndFlush(reversal);

    List<LedgerPostingEntity> reversedPostings = originalPostings.stream()
        .map(p -> LedgerPostingEntity.of(
            reversal.getId(), p.getAccountId(), p.getLedgerAccountCode(),
            "DEBIT".equals(p.getSide()) ? "CREDIT" : "DEBIT",
            p.getAmount(), p.getCurrency(), now
        ))
        .toList();
    postingRepository.saveAllAndFlush(reversedPostings);

    reversal.post(now);
    journalRepository.saveAndFlush(reversal);
    eventRepository.save(toEvent(reversal, "SETTLEMENT_REVERSED", now));

    log.info("[SETTLEMENT-REVERSAL] Reversed original journal [{}] with reversal [{}]", original.getId(), reversal.getId());
    return new AtomicPostingResponse(
        reversal.getId(), reversal.getBusinessCommandId(), reversal.getStatus(), reversal.getJournalType(),
        reversal.getCurrency(), totalAmount, reversal.getPostedAt()
    );
  }

  private String calculateCommandHash(Object command) {
    try {
      String json = objectMapper.writeValueAsString(command);
      return sha256(json);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot calculate deterministic ledger command hash", e);
    }
  }

  private FinancialEventEntity toEvent(LedgerJournalEntity journal, String eventType, Instant now) {
    Map<String, Object> payload = Map.of(
        "journalId", journal.getId().toString(),
        "businessReference", journal.getBusinessReference(),
        "journalType", journal.getJournalType(),
        "status", journal.getStatus(),
        "currency", journal.getCurrency()
    );
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize financial event", ex);
    }
    String eventKey = eventType + ":" + journal.getId();
    return FinancialEventEntity.of(
        UUID.nameUUIDFromBytes(eventKey.getBytes(StandardCharsets.UTF_8)), journal.getId(),
        eventType, journal.getTransactionId(), now, json, sha256(json)
    );
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
