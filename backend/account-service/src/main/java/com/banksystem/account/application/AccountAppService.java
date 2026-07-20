package com.banksystem.account.application;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.application.query.LedgerStatementQuery;
import com.banksystem.account.config.GatewayUser;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.LedgerEntryEntity;
import com.banksystem.account.domain.LedgerEntryRepository;
import com.banksystem.account.domain.LedgerEntryType;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAppService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final int maxPerUser;
  private final BigDecimal initialBalance;
  private final SecureRandom random = new SecureRandom();

  public AccountAppService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      @Value("${bank.account.max-per-user:3}") int maxPerUser,
      @Value("${bank.account.initial-balance:1000000}") BigDecimal initialBalance) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.maxPerUser = maxPerUser;
    this.initialBalance = initialBalance;
  }

  @Transactional
  public AccountResponse open(UUID userId, OpenAccountRequest req) {
    if (accountRepository.countByUserId(userId) >= maxPerUser) {
      throw new BusinessException("MAX_ACCOUNTS", "Maximum " + maxPerUser + " accounts per user",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    AccountEntity a = new AccountEntity();
    a.setId(UUID.randomUUID());
    a.setUserId(userId);
    a.setAccountNumber(generateAccountNumber());
    a.setAccountType(req.accountType() == null || req.accountType().isBlank() ? "PAYMENT" : req.accountType());
    a.setCurrency("VND");
    a.setBalance(initialBalance);
    a.setStatus("ACTIVE");
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return toResponse(accountRepository.save(a));
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listMine(UUID userId) {
    return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public AccountResponse get(UUID id, GatewayUser user) {
    AccountEntity a = requireOwnedOrStaff(id, user);
    return toResponse(a);
  }

  /**
   * Customer/staff account ledger statement (money movements), newest first.
   * Distinct from transfer-order history in transaction-service.
   */
  @Transactional(readOnly = true)
  public PageResponse<LedgerEntryResponse> statement(LedgerStatementQuery query, GatewayUser user) {
    requireOwnedOrStaff(query.accountId(), user);
    String type = query.entryType() == null ? null : query.entryType().name();
    PageRequest pageable = PageRequest.of(
        query.page(),
        query.size(),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<LedgerEntryEntity> page = ledgerEntryRepository.search(
        query.accountId(),
        type,
        query.from(),
        query.to(),
        pageable);
    List<LedgerEntryResponse> items = page.getContent().stream().map(this::toLedgerResponse).toList();
    return new PageResponse<>(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional
  public AccountResponse freeze(UUID id) {
    AccountEntity a = require(id);
    a.setStatus("FROZEN");
    a.setUpdatedAt(Instant.now());
    return toResponse(accountRepository.save(a));
  }

  @Transactional
  public AccountResponse unfreeze(UUID id) {
    AccountEntity a = require(id);
    a.setStatus("ACTIVE");
    a.setUpdatedAt(Instant.now());
    return toResponse(accountRepository.save(a));
  }

  @Transactional(readOnly = true)
  public AccountResponse getInternal(UUID id) {
    return toResponse(require(id));
  }

  @Transactional(readOnly = true)
  public AccountResponse getByNumber(String accountNumber) {
    return toResponse(accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found",
            HttpStatus.NOT_FOUND)));
  }

  @Transactional
  public MoneyResult debit(UUID id, MoneyCommand cmd) {
    validateAmount(cmd.amount());
    AccountEntity account = require(id);
    if ("FROZEN".equals(account.getStatus()) || "CLOSED".equals(account.getStatus())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    var existing = ledgerEntryRepository.findByAccountIdAndReferenceIdAndEntryType(
        id, cmd.referenceId(), "DEBIT");
    if (existing.isPresent()) {
      AccountEntity refreshed = require(id);
      return new MoneyResult(existing.get().getId().toString(), refreshed.getBalance());
    }

    int updated = accountRepository.debitIfSufficient(id, cmd.amount());
    if (updated == 0) {
      // distinguish frozen vs insufficient
      AccountEntity current = require(id);
      if (!"ACTIVE".equals(current.getStatus())) {
        throw new BusinessException("ACCOUNT_FROZEN", "Account is not active", HttpStatus.UNPROCESSABLE_ENTITY);
      }
      throw new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    LedgerEntryEntity entry = newLedger(id, "DEBIT", cmd);
    try {
      ledgerEntryRepository.save(entry);
    } catch (DataIntegrityViolationException e) {
      // concurrent idempotent retry
      LedgerEntryEntity again = ledgerEntryRepository
          .findByAccountIdAndReferenceIdAndEntryType(id, cmd.referenceId(), "DEBIT")
          .orElseThrow();
      return new MoneyResult(again.getId().toString(), require(id).getBalance());
    }
    return new MoneyResult(entry.getId().toString(), require(id).getBalance());
  }

  @Transactional
  public MoneyResult credit(UUID id, MoneyCommand cmd) {
    validateAmount(cmd.amount());
    require(id);

    var existing = ledgerEntryRepository.findByAccountIdAndReferenceIdAndEntryType(
        id, cmd.referenceId(), "CREDIT");
    if (existing.isPresent()) {
      return new MoneyResult(existing.get().getId().toString(), require(id).getBalance());
    }

    int updated = accountRepository.creditIfActive(id, cmd.amount());
    if (updated == 0) {
      AccountEntity current = require(id);
      if (!"ACTIVE".equals(current.getStatus())) {
        // allow credit to frozen for compensation? ADR debit/credit - compensate reverse.
        // For compensation after debit, account is still ACTIVE typically.
        // If frozen, still try direct update for credit compensation:
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
      return new MoneyResult(again.getId().toString(), require(id).getBalance());
    }
    return new MoneyResult(entry.getId().toString(), require(id).getBalance());
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

  private AccountEntity require(UUID id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found",
            HttpStatus.NOT_FOUND));
  }

  private AccountEntity requireOwnedOrStaff(UUID id, GatewayUser user) {
    AccountEntity a = require(id);
    boolean staffLookup = user.hasPermission("accounts:lookup:view")
        || user.hasPermission("accounts:freeze:execute");
    if (!staffLookup && !a.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your account", HttpStatus.FORBIDDEN);
    }
    return a;
  }

  private LedgerEntryResponse toLedgerResponse(LedgerEntryEntity e) {
    BigDecimal signed = e.getAmount();
    if (LedgerEntryType.DEBIT.name().equalsIgnoreCase(e.getEntryType())) {
      signed = e.getAmount().negate();
    }
    return new LedgerEntryResponse(
        e.getId().toString(),
        e.getAccountId().toString(),
        e.getEntryType(),
        e.getAmount(),
        signed,
        e.getReferenceId(),
        e.getDescription(),
        e.getCreatedAt());
  }

  private String generateAccountNumber() {
    for (int i = 0; i < 20; i++) {
      int suffix = random.nextInt(100_000_000);
      String num = String.format("10%08d", suffix);
      if (!accountRepository.existsByAccountNumber(num)) {
        return num;
      }
    }
    throw new BusinessException("ACCOUNT_NUMBER_GEN_FAILED", "Could not generate unique account number",
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private AccountResponse toResponse(AccountEntity a) {
    return new AccountResponse(
        a.getId().toString(),
        a.getUserId().toString(),
        a.getAccountNumber(),
        a.getAccountType(),
        a.getCurrency(),
        a.getBalance(),
        a.getStatus()
    );
  }
}
