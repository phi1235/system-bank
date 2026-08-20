package com.banksystem.account.application.ledger;

import com.banksystem.account.api.dto.AccountHoldDtos.CreateBatchHoldRequest;
import com.banksystem.account.api.dto.AccountHoldDtos.CreateHoldRequest;
import com.banksystem.account.api.dto.AccountHoldDtos.HoldActionRequest;
import com.banksystem.account.api.dto.AccountHoldDtos.HoldResponse;
import com.banksystem.account.api.dto.AccountHoldDtos.PartialCaptureHoldRequest;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.AccountHoldEntity;
import com.banksystem.account.domain.ledger.AccountHoldRepository;
import com.banksystem.account.domain.ledger.LedgerJournalEntity;
import com.banksystem.account.domain.ledger.LedgerJournalRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountHoldService {
  private static final Logger log = LoggerFactory.getLogger(AccountHoldService.class);
  private final AccountHoldRepository holdRepository;
  private final AccountRepository accountRepository;
  private final LedgerJournalRepository journalRepository;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final int expiryBatchSize;

  public AccountHoldService(
      AccountHoldRepository holdRepository,
      AccountRepository accountRepository,
      LedgerJournalRepository journalRepository,
      JdbcTemplate jdbcTemplate,
      Clock clock,
      @Value("${bank.ledger.hold-expiry.batch-size:100}") int expiryBatchSize) {
    this.holdRepository = holdRepository;
    this.accountRepository = accountRepository;
    this.journalRepository = journalRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
    this.expiryBatchSize = expiryBatchSize;
  }

  @Transactional
  public HoldResponse create(UUID accountId, CreateHoldRequest request) {
    AccountHoldEntity duplicate = holdRepository
        .findByAccountIdAndCommandId(accountId, request.commandId()).orElse(null);
    if (duplicate != null) return toResponse(duplicate);

    AccountEntity account = accountRepository.findById(accountId).orElseThrow(() ->
        new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    if (!account.getCurrency().equalsIgnoreCase(request.currency())) {
      throw new BusinessException("HOLD_CURRENCY_MISMATCH", "Hold currency does not match account");
    }
    BigDecimal available = jdbcTemplate.queryForObject(
        "SELECT available_balance FROM accounts WHERE id = ? FOR UPDATE",
        BigDecimal.class,
        accountId);
    if (available == null || available.compareTo(request.amount()) < 0) {
      throw new BusinessException("INSUFFICIENT_AVAILABLE_BALANCE", "Available balance is insufficient");
    }

    Instant now = clock.instant();
    AccountHoldEntity hold = AccountHoldEntity.active(
        UUID.randomUUID(), accountId, request.transactionId(), request.commandId(), request.amount(),
        request.currency().toUpperCase(), request.expiresAt(), now);
    holdRepository.saveAndFlush(hold);
    recordCommand(request.commandId(), hold.getId(), "CREATE");
    log.info("[ACCOUNT-HOLD] Created ACTIVE hold [{}] Account=[{}] TxId=[{}] Amount={} {}",
        hold.getId(), accountId, request.transactionId(), request.amount(), request.currency());
    return toResponse(hold);
  }

  @Transactional
  public HoldResponse createBatchHold(UUID accountId, CreateBatchHoldRequest request) {
    AccountHoldEntity duplicate = holdRepository
        .findByAccountIdAndCommandId(accountId, request.commandId()).orElse(null);
    if (duplicate != null) return toResponse(duplicate);

    AccountEntity account = accountRepository.findById(accountId).orElseThrow(() ->
        new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    if (!account.getCurrency().equalsIgnoreCase(request.currency())) {
      throw new BusinessException("HOLD_CURRENCY_MISMATCH", "Hold currency does not match account");
    }
    BigDecimal available = jdbcTemplate.queryForObject(
        "SELECT available_balance FROM accounts WHERE id = ? FOR UPDATE",
        BigDecimal.class,
        accountId);
    if (available == null || available.compareTo(request.amount()) < 0) {
      throw new BusinessException("INSUFFICIENT_AVAILABLE_BALANCE", "Available balance is insufficient");
    }

    Instant now = clock.instant();
    AccountHoldEntity hold = AccountHoldEntity.activeForBatch(
        UUID.randomUUID(), accountId, request.batchId(), request.commandId(), request.amount(),
        request.currency().toUpperCase(), request.expiresAt(), now);
    holdRepository.saveAndFlush(hold);
    recordCommand(request.commandId(), hold.getId(), "CREATE_BATCH");
    log.info("[ACCOUNT-HOLD] Created ACTIVE batch hold [{}] Account=[{}] BatchId=[{}] Amount={} {}",
        hold.getId(), accountId, request.batchId(), request.amount(), request.currency());
    return toResponse(hold);
  }

  @Transactional
  public HoldResponse capture(UUID holdId, HoldActionRequest request) {
    if (request.journalId() == null) {
      throw new BusinessException("JOURNAL_ID_REQUIRED", "journalId is required for capture");
    }
    AccountHoldEntity hold = require(holdId);
    if (!recordCommand(request.commandId(), holdId, "CAPTURE")) return toResponse(hold);
    LedgerJournalEntity journal = journalRepository.findById(request.journalId()).orElseThrow(() ->
        new BusinessException("LEDGER_JOURNAL_NOT_FOUND", "Ledger journal not found"));
    if (!"POSTED".equals(journal.getStatus())
        || !hold.getTransactionId().equals(journal.getTransactionId())) {
      throw new BusinessException("HOLD_JOURNAL_MISMATCH", "Posted journal does not match hold transaction");
    }
    transition(() -> hold.capture(journal.getId(), clock.instant()));
    AccountHoldEntity saved = holdRepository.saveAndFlush(hold);
    log.info("[ACCOUNT-HOLD] Captured hold [{}] with Journal=[{}] TxId=[{}]",
        holdId, journal.getId(), hold.getTransactionId());
    return toResponse(saved);
  }

  @Transactional
  public HoldResponse partialCapture(UUID holdId, PartialCaptureHoldRequest request) {
    AccountHoldEntity hold = require(holdId);
    if (!recordCommand(request.commandId(), holdId, "PARTIAL_CAPTURE")) return toResponse(hold);
    transition(() -> hold.partialCapture(request.amount(), clock.instant()));
    AccountHoldEntity saved = holdRepository.saveAndFlush(hold);
    log.info("[ACCOUNT-HOLD] Partial captured hold [{}] Amount={} TotalCaptured={}",
        holdId, request.amount(), saved.getCapturedAmount());
    return toResponse(saved);
  }

  @Transactional
  public HoldResponse releaseRemaining(UUID holdId, HoldActionRequest request) {
    AccountHoldEntity hold = require(holdId);
    if (!recordCommand(request.commandId(), holdId, "RELEASE_REMAINING")) return toResponse(hold);
    transition(() -> hold.releaseRemaining(clock.instant()));
    AccountHoldEntity saved = holdRepository.saveAndFlush(hold);
    log.info("[ACCOUNT-HOLD] Released remaining hold [{}] ReleasedAmount={}",
        holdId, saved.getReleasedAmount());
    return toResponse(saved);
  }

  @Transactional
  public HoldResponse release(UUID holdId, HoldActionRequest request) {
    AccountHoldEntity hold = require(holdId);
    if (!recordCommand(request.commandId(), holdId, "RELEASE")) return toResponse(hold);
    transition(() -> hold.release(clock.instant()));
    AccountHoldEntity saved = holdRepository.saveAndFlush(hold);
    log.info("[ACCOUNT-HOLD] Released hold [{}] TxId=[{}]", holdId, hold.getTransactionId());
    return toResponse(saved);
  }

  @Transactional
  public int expireDueHolds() {
    Instant now = clock.instant();
    List<UUID> ids = jdbcTemplate.queryForList("""
        SELECT id FROM account_holds
        WHERE status = 'ACTIVE' AND expires_at <= ?
        ORDER BY expires_at
        FOR UPDATE SKIP LOCKED
        LIMIT ?
        """, UUID.class, now, expiryBatchSize);
    int expired = 0;
    for (UUID id : ids) {
      AccountHoldEntity hold = require(id);
      if (hold.expire(now)) {
        holdRepository.save(hold);
        recordCommand("EXPIRE:" + id, id, "EXPIRE");
        expired++;
      }
    }
    holdRepository.flush();
    if (expired > 0) {
      log.info("[ACCOUNT-HOLD] Expired {} due holds at timestamp {}", expired, now);
    }
    return expired;
  }

  private AccountHoldEntity require(UUID id) {
    return holdRepository.findById(id).orElseThrow(() ->
        new BusinessException("ACCOUNT_HOLD_NOT_FOUND", "Account hold not found"));
  }

  private boolean recordCommand(String commandId, UUID holdId, String type) {
    return jdbcTemplate.update("""
        INSERT INTO account_hold_commands (command_id, hold_id, command_type)
        VALUES (?, ?, ?)
        ON CONFLICT (command_id) DO NOTHING
        """, commandId, holdId, type) == 1;
  }

  private void transition(Runnable transition) {
    try {
      transition.run();
    } catch (IllegalStateException exception) {
      throw new BusinessException("INVALID_HOLD_STATE", exception.getMessage());
    }
  }

  private HoldResponse toResponse(AccountHoldEntity hold) {
    return new HoldResponse(
        hold.getId(),
        hold.getAccountId(),
        hold.getTransactionId(),
        hold.getBatchId(),
        hold.getAmount(),
        hold.getOriginalAmount(),
        hold.getCapturedAmount(),
        hold.getReleasedAmount(),
        hold.getCurrency(),
        hold.getStatus(),
        hold.getExpiresAt(),
        hold.getCapturedJournalId(),
        hold.getCreatedAt(),
        hold.getUpdatedAt(),
        hold.getVersion());
  }
}
