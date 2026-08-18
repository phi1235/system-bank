package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.application.ledger.DoubleEntryJournalService;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal idempotent money movements and internal account lookup. */
@Service
public class AccountMoneyService {
  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final DoubleEntryJournalService journalService;
  private final Clock clock;

  public AccountMoneyService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountAccessService access,
      AccountMapper mapper,
      DoubleEntryJournalService journalService,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.access = access;
    this.mapper = mapper;
    this.journalService = journalService;
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

  @Transactional
  public MoneyResult debit(UUID id, MoneyCommand command) {
    validateAmount(command.amount());
    AccountEntity account = access.requireForUpdate(id);
    requireActive(account, "Account is not active");
    Optional<LedgerEntryEntity> existing = existing(id, command.referenceId(), "DEBIT");
    if (existing.isPresent()) {
      return result(existing.get(), account);
    }
    if (accountRepository.debitIfSufficient(id, command.amount()) == 0) {
      AccountEntity current = access.require(id);
      requireActive(current, "Account is not active");
      throw new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient");
    }
    return persist(id, "DEBIT", command, account);
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
