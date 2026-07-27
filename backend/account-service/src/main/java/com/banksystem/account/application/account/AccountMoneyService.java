package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.account.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyResult;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.entity.account.LedgerEntryEntity;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.account.domain.repository.account.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal money movements (debit/credit) and internal account lookup.
 * Idempotent on (accountId, referenceId, entryType).
 */
@Service
public class AccountMoneyService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;

  public AccountMoneyService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountAccessService access,
      AccountMapper mapper) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.access = access;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public AccountResponse getInternal(UUID id) {
    return mapper.toResponse(access.require(id));
  }

  @Transactional(readOnly = true)
  public AccountResponse getByNumber(String accountNumber) {
    return mapper.toResponse(accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found",
            HttpStatus.NOT_FOUND)));
  }

  @Transactional
  public MoneyResult debit(UUID id, MoneyCommand cmd) {
    validateAmount(cmd.amount());
    AccountEntity account = access.require(id);
    if (!access.currentStatus(account).isActive()) {
      throw new BusinessException("ACCOUNT_FROZEN", "Account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    var existing = ledgerEntryRepository.findByAccountIdAndReferenceIdAndEntryType(
        id, cmd.referenceId(), "DEBIT");
    if (existing.isPresent()) {
      AccountEntity refreshed = access.require(id);
      return new MoneyResult(existing.get().getId().toString(), refreshed.getBalance());
    }

    int updated = accountRepository.debitIfSufficient(id, cmd.amount());
    if (updated == 0) {
      AccountEntity current = access.require(id);
      if (!access.currentStatus(current).isActive()) {
        throw new BusinessException("ACCOUNT_FROZEN", "Account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
      }
      throw new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    LedgerEntryEntity entry = newLedger(id, "DEBIT", cmd);
    try {
      ledgerEntryRepository.save(entry);
    } catch (DataIntegrityViolationException e) {
      LedgerEntryEntity again = ledgerEntryRepository
          .findByAccountIdAndReferenceIdAndEntryType(id, cmd.referenceId(), "DEBIT")
          .orElseThrow();
      return new MoneyResult(again.getId().toString(), access.require(id).getBalance());
    }
    return new MoneyResult(entry.getId().toString(), access.require(id).getBalance());
  }

  @Transactional
  public MoneyResult credit(UUID id, MoneyCommand cmd) {
    validateAmount(cmd.amount());
    access.require(id);

    var existing = ledgerEntryRepository.findByAccountIdAndReferenceIdAndEntryType(
        id, cmd.referenceId(), "CREDIT");
    if (existing.isPresent()) {
      return new MoneyResult(existing.get().getId().toString(), access.require(id).getBalance());
    }

    int updated = accountRepository.creditIfActive(id, cmd.amount());
    if (updated == 0) {
      AccountEntity current = access.require(id);
      if (!access.currentStatus(current).isActive()) {
        // Compensation after debit usually targets ACTIVE accounts.
        // Frozen/closed credit remains blocked for now.
        throw new BusinessException("ACCOUNT_FROZEN", "Account is not active for credit",
            HttpStatus.UNPROCESSABLE_ENTITY);
      }
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found", HttpStatus.NOT_FOUND);
    }

    LedgerEntryEntity entry = newLedger(id, "CREDIT", cmd);
    try {
      ledgerEntryRepository.save(entry);
    } catch (DataIntegrityViolationException e) {
      LedgerEntryEntity again = ledgerEntryRepository
          .findByAccountIdAndReferenceIdAndEntryType(id, cmd.referenceId(), "CREDIT")
          .orElseThrow();
      return new MoneyResult(again.getId().toString(), access.require(id).getBalance());
    }
    return new MoneyResult(entry.getId().toString(), access.require(id).getBalance());
  }

  private LedgerEntryEntity newLedger(UUID accountId, String type, MoneyCommand cmd) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.setId(UUID.randomUUID());
    e.setAccountId(accountId);
    e.setEntryType(type);
    e.setAmount(cmd.amount());
    e.setReferenceId(cmd.referenceId());
    e.setDescription(cmd.description());
    e.setCreatedAt(Instant.now());
    return e;
  }

  private void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);
    }
  }
}
