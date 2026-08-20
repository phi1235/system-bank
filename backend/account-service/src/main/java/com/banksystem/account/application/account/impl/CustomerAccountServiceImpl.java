package com.banksystem.account.application.account.impl;

import java.time.Instant;
import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.account.application.account.AccountAccessService;
import com.banksystem.account.application.account.AccountMapper;
import com.banksystem.account.application.account.AccountMoneyService;
import com.banksystem.account.application.account.AccountNumberGenerator;
import com.banksystem.account.application.account.CustomerAccountService;
import com.banksystem.account.application.account.StatementCsvWriter;
import com.banksystem.account.application.ledger.LedgerStatementQuery;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.account.AccountStatus;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-facing account use-cases (open, list, get, statement, top-up). */
@Service
public class CustomerAccountServiceImpl implements CustomerAccountService {

  private static final Logger log = LoggerFactory.getLogger(CustomerAccountServiceImpl.class);

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final AccountNumberGenerator accountNumbers;
  private final AccountMoneyService moneyService;
  private final int maxPerUser;
  private final BigDecimal initialBalance;
  private final BigDecimal maxTopUpAmount;

  public CustomerAccountServiceImpl(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountAccessService access,
      AccountMapper mapper,
      AccountNumberGenerator accountNumbers,
      AccountMoneyService moneyService,
      @Value("${bank.account.max-per-user}") int maxPerUser,
      @Value("${bank.account.initial-balance}") BigDecimal initialBalance,
      @Value("${bank.account.topup.max-amount}") BigDecimal maxTopUpAmount) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.access = access;
    this.mapper = mapper;
    this.accountNumbers = accountNumbers;
    this.moneyService = moneyService;
    this.maxPerUser = maxPerUser;
    this.initialBalance = initialBalance;
    this.maxTopUpAmount = maxTopUpAmount;
  }

  @Transactional
  public AccountResponse open(UUID userId, OpenAccountRequest req) {
    if (accountRepository.countByUserIdAndOwnerType(userId, "INDIVIDUAL") >= maxPerUser) {
      throw new BusinessException("MAX_ACCOUNTS", "Maximum " + maxPerUser + " accounts per user");
    }
    String accountType = (req == null || req.accountType() == null || req.accountType().isBlank())
        ? "PAYMENT"
        : req.accountType().trim();

    AccountEntity a = new AccountEntity();
    a.setId(UUID.randomUUID());
    a.setUserId(userId);
    a.setOwnerType("INDIVIDUAL");
    a.setOwnerId(userId);
    a.setAccountNumber(accountNumbers.next());
    a.setAccountType(accountType);
    a.setCurrency("VND");
    a.setBalance(initialBalance);
    a.setStatus(AccountStatus.ACTIVE.name());
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());

    AccountEntity saved = accountRepository.save(a);
    log.info("[ACCOUNT-OPEN] Opened new account [{}] (#{}) Type=[{}] User=[{}] InitialBalance={} {}",
        saved.getId(), saved.getAccountNumber(), saved.getAccountType(), userId, saved.getBalance(), saved.getCurrency());
    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listMine(UUID userId) {
    List<AccountResponse> accounts = accountRepository.findByUserIdAndOwnerTypeOrderByCreatedAtDesc(userId, "INDIVIDUAL").stream()
        .map(mapper::toResponse)
        .toList();
    log.info("[ACCOUNT-LIST] Found {} personal accounts for userId=[{}]", accounts.size(), userId);
    return accounts;
  }

  @Transactional(readOnly = true)
  public AccountResponse get(UUID id, GatewayUser user) {
    return mapper.toResponse(access.requireOwnedOrStaff(id, user));
  }

  /**
   * Customer/staff account ledger statement (money movements), newest first.
   * Distinct from transfer-order history in transaction-service.
   */
  @Transactional(readOnly = true)
  public PageResponse<LedgerEntryResponse> statement(LedgerStatementQuery query, GatewayUser user) {
    access.requireOwnedOrStaff(query.accountId(), user);
    PageRequest pageable = PageRequest.of(
        query.page(),
        query.size(),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<LedgerEntryEntity> page = ledgerEntryRepository.search(
        query.accountId(),
        query.hasEntryType(),
        query.entryTypeName(),
        query.from(),
        query.to(),
        pageable);
    List<LedgerEntryResponse> items = page.getContent().stream().map(mapper::toLedgerResponse).toList();
    log.info("[ACCOUNT-STATEMENT] Account=[{}] Page={} Size={} Results={}",
        query.accountId(), query.page(), query.size(), page.getTotalElements());
    return new PageResponse<>(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /**
   * Export ledger lines as CSV (UTF-8 BOM for Excel). Same filters as statement, newest first,
   * hard-capped at {@link LedgerStatementQuery#MAX_EXPORT_ROWS}.
   */
  @Transactional(readOnly = true)
  public byte[] exportStatementCsv(LedgerStatementQuery query, GatewayUser user) {
    access.requireOwnedOrStaff(query.accountId(), user);
    int limit = Math.min(Math.max(query.size(), 1), LedgerStatementQuery.MAX_EXPORT_ROWS);
    PageRequest pageable = PageRequest.of(
        0,
        limit,
        Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<LedgerEntryEntity> page = ledgerEntryRepository.search(
        query.accountId(),
        query.hasEntryType(),
        query.entryTypeName(),
        query.from(),
        query.to(),
        pageable);
    return StatementCsvWriter.write(page.getContent());
  }
}
