package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.AccountDtos.AccountOwnershipResponse;
import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.DebitAgainstHoldCommand;
import com.banksystem.account.api.dto.AccountDtos.CompensateCreditAgainstHoldCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.application.ledger.DoubleEntryJournalService;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.AccountHoldEntity;
import com.banksystem.account.domain.ledger.AccountHoldRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal idempotent money movements and internal account lookup. */
@Service
public class AccountMoneyService {
  private final AccountRepository accountRepository;
  private final AccountHoldRepository holdRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final DoubleEntryJournalService journalService;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public AccountMoneyService(
      AccountRepository accountRepository,
      AccountHoldRepository holdRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountAccessService access,
      AccountMapper mapper,
      DoubleEntryJournalService journalService,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.holdRepository = holdRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.access = access;
    this.mapper = mapper;
    this.journalService = journalService;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AccountResponse getInternal(UUID id) {
    return mapper.toResponse(access.require(id));
  }

  @Transactional(readOnly = true)
  public AccountResponse getByNumber(String accountNumber) {
    return mapper.toResponse(accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found")));
  }

  @Transactional(readOnly = true)
  public AccountOwnershipResponse getOwnership(UUID id) {
    AccountEntity account = access.require(id);
    return new AccountOwnershipResponse(
        account.getId(),
        account.getAccountNumber(),
        account.getOwnerType(),
        account.getOwnerId(),
        account.getStatus(),
        account.getCurrency()
    );
  }

  @Transactional
  public MoneyResult debit(UUID id, MoneyCommand command) {
    validateAmount(command.amount());
    AccountEntity account = access.requireForUpdate(id);
    requireActive(account, "Account is not active");
    Optional<LedgerEntryEntity> existing = existing(id, command.referenceId(), "DEBIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }
    // Regular transfer: check available_balance to respect existing holds
    if (accountRepository.debitIfAvailableSufficient(id, command.amount()) == 0) {
      AccountEntity current = access.require(id);
      requireActive(current, "Account is not active");
      throw new BusinessException("INSUFFICIENT_AVAILABLE_BALANCE", "Account available balance is insufficient (held funds protected)");
    }
    return persist(id, "DEBIT", command, account);
  }

  @Transactional
  public MoneyResult debitAgainstHold(UUID accountId, DebitAgainstHoldCommand cmd) {
    MoneyCommand moneyCmd = cmd.command();
    validateAmount(moneyCmd.amount());

    // 1. Lock account for update
    AccountEntity account = access.requireForUpdate(accountId);
    requireActive(account, "Account is not active");

    // Check idempotency
    Optional<LedgerEntryEntity> existing = existing(accountId, moneyCmd.referenceId(), "DEBIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }

    // 2. Lock hold for update
    AccountHoldEntity hold = holdRepository.findByIdForUpdate(cmd.holdId()).orElseThrow(() ->
        new BusinessException("HOLD_NOT_FOUND", "Batch hold not found for ID: " + cmd.holdId()));

    if (!hold.getAccountId().equals(accountId)) {
      throw new BusinessException("HOLD_ACCOUNT_MISMATCH", "Hold does not belong to specified account");
    }

    // Validate hold belongs to the correct batch
    if (hold.getBatchId() == null || !hold.getBatchId().equals(cmd.batchId())) {
      throw new BusinessException("HOLD_BATCH_MISMATCH", "Hold does not belong to specified batch");
    }

    if (!"ACTIVE".equals(hold.getStatus())) {
      throw new BusinessException("HOLD_NOT_ACTIVE", "Hold status is not ACTIVE (current: " + hold.getStatus() + ")");
    }

    if (!hold.getExpiresAt().isAfter(clock.instant())) {
      throw new BusinessException("HOLD_EXPIRED", "Hold has expired at " + hold.getExpiresAt());
    }

    BigDecimal remaining = hold.getOriginalAmount()
        .subtract(hold.getCapturedAmount())
        .subtract(hold.getReleasedAmount());

    if (remaining.compareTo(moneyCmd.amount()) < 0) {
      throw new BusinessException("INSUFFICIENT_HOLD_REMAINING", "Hold remaining amount " + remaining + " is less than debit " + moneyCmd.amount());
    }

    // 3. Perform debit on balance
    if (accountRepository.debitIfSufficient(accountId, moneyCmd.amount()) == 0) {
      throw new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient");
    }

    // 4. Capture portion of hold atomically
    hold.partialCapture(moneyCmd.amount(), clock.instant());
    holdRepository.saveAndFlush(hold);

    // 5. Record command for audit
    String cmdId = moneyCmd.commandId() != null ? moneyCmd.commandId() : "DEBIT-HOLD:" + cmd.holdId() + ":" + moneyCmd.referenceId();
    jdbcTemplate.update("""
        INSERT INTO account_hold_commands (command_id, hold_id, command_type)
        VALUES (?, ?, 'DEBIT_AGAINST_HOLD')
        ON CONFLICT (command_id) DO NOTHING
        """, cmdId, hold.getId());

    // 6. Persist ledger entry
    return persist(accountId, "DEBIT", moneyCmd, account);
  }

  @Transactional
  public MoneyResult compensateCreditAgainstHold(
      UUID accountId, CompensateCreditAgainstHoldCommand cmd) {
    MoneyCommand moneyCmd = cmd.command();
    validateAmount(moneyCmd.amount());

    AccountEntity account = access.requireForUpdate(accountId);
    Optional<LedgerEntryEntity> existing = existing(accountId, moneyCmd.referenceId(), "CREDIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }

    AccountHoldEntity hold = holdRepository.findByIdForUpdate(cmd.holdId()).orElseThrow(() ->
        new BusinessException("HOLD_NOT_FOUND", "Batch hold not found for ID: " + cmd.holdId()));
    if (!hold.getAccountId().equals(accountId)) {
      throw new BusinessException("HOLD_ACCOUNT_MISMATCH", "Hold does not belong to specified account");
    }
    if (hold.getBatchId() == null || !hold.getBatchId().equals(cmd.batchId())) {
      throw new BusinessException("HOLD_BATCH_MISMATCH", "Hold does not belong to specified batch");
    }
    if (hold.getCapturedAmount().compareTo(moneyCmd.amount()) < 0) {
      throw new BusinessException(
          "INSUFFICIENT_CAPTURED_AMOUNT", "Compensation exceeds captured hold amount");
    }

    if (accountRepository.creditForCompensation(accountId, moneyCmd.amount()) == 0) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found for compensation");
    }
    hold.reverseCapture(moneyCmd.amount(), clock.instant());
    holdRepository.saveAndFlush(hold);

    String commandId = moneyCmd.commandId() != null
        ? moneyCmd.commandId()
        : "COMPENSATE-HOLD:" + cmd.holdId() + ":" + moneyCmd.referenceId();
    jdbcTemplate.update("""
        INSERT INTO account_hold_commands (command_id, hold_id, command_type)
        VALUES (?, ?, 'COMPENSATE_CREDIT_AGAINST_HOLD')
        ON CONFLICT (command_id) DO NOTHING
        """, commandId, hold.getId());
    return persist(accountId, "CREDIT", moneyCmd, account);
  }

  @Transactional
  public MoneyResult credit(UUID id, MoneyCommand command) {
    validateAmount(command.amount());
    AccountEntity account = access.requireForUpdate(id);
    requireActive(account, "Account is not active for credit");
    Optional<LedgerEntryEntity> existing = existing(id, command.referenceId(), "CREDIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }
    if (accountRepository.creditIfActive(id, command.amount()) == 0) {
      AccountEntity current = access.require(id);
      requireActive(current, "Account is not active for credit");
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found");
    }
    return persist(id, "CREDIT", command, account);
  }

  @Transactional
  public MoneyResult compensateCredit(UUID id, MoneyCommand command) {
    validateAmount(command.amount());
    AccountEntity account = access.requireForUpdate(id);
    Optional<LedgerEntryEntity> existing = existing(id, command.referenceId(), "CREDIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }
    if (accountRepository.creditForCompensation(id, command.amount()) == 0) {
      throw new BusinessException(
          "COMPENSATION_REVIEW_REQUIRED",
          "Closed accounts require a controlled manual compensation workflow");
    }
    return persist(id, "CREDIT", command, account);
  }

  private MoneyResult persist(
      UUID accountId, String entryType, MoneyCommand command, AccountEntity lockedAccount) {
    LedgerEntryEntity entry = newLedger(accountId, entryType, command);
    ledgerEntryRepository.saveAndFlush(entry);
    journalService.recordLegacyEntry(entry, lockedAccount.getCurrency());
    return new MoneyResult(entry.getId().toString(), access.require(accountId).getBalance());
  }

  private Optional<LedgerEntryEntity> existing(
      UUID accountId, String referenceId, String entryType) {
    return ledgerEntryRepository.findByAccountIdAndReferenceIdAndEntryType(
        accountId, referenceId, entryType);
  }

  private MoneyResult result(LedgerEntryEntity entry, AccountEntity account) {
    return new MoneyResult(entry.getId().toString(), account.getBalance());
  }

  private LedgerEntryEntity newLedger(UUID accountId, String type, MoneyCommand command) {
    LedgerEntryEntity entry = new LedgerEntryEntity();
    entry.setId(UUID.randomUUID());
    entry.setAccountId(accountId);
    entry.setEntryType(type);
    entry.setAmount(command.amount());
    entry.setReferenceId(command.referenceId());
    entry.setDescription(command.description());
    entry.setCreatedAt(Instant.now(clock));
    return entry;
  }

  private void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive");
    }
  }

  private void requireActive(AccountEntity account, String message) {
    if (!access.currentStatus(account).isActive()) {
      throw new BusinessException("ACCOUNT_FROZEN", message);
    }
  }
}
