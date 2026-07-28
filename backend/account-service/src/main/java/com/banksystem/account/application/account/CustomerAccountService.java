package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.account.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.account.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.account.AccountDtos.OpenAccountRequest;
import com.banksystem.account.api.dto.account.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.account.AccountDtos.TopUpResponse;
import com.banksystem.account.application.account.query.LedgerStatementQuery;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.entity.account.LedgerEntryEntity;
import com.banksystem.account.domain.enums.account.AccountStatus;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.account.domain.repository.account.LedgerEntryRepository;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-facing account use-cases (open, list, get, statement, top-up). */
@Service
public class CustomerAccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final AccountNumberGenerator accountNumbers;
  private final AccountMoneyService moneyService;
  private final int maxPerUser;
  private final BigDecimal initialBalance;
  private final BigDecimal maxTopUpAmount;

  public CustomerAccountService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountAccessService access,
      AccountMapper mapper,
      AccountNumberGenerator accountNumbers,
      AccountMoneyService moneyService,
      @Value("${bank.account.max-per-user:3}") int maxPerUser,
      @Value("${bank.account.initial-balance:1000000}") BigDecimal initialBalance,
      @Value("${bank.account.topup.max-amount:50000000}") BigDecimal maxTopUpAmount) {
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
    if (accountRepository.countByUserId(userId) >= maxPerUser) {
      throw new BusinessException("MAX_ACCOUNTS", "Maximum " + maxPerUser + " accounts per user",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    String accountType = (req == null || req.accountType() == null || req.accountType().isBlank())
        ? "PAYMENT"
        : req.accountType().trim();

    AccountEntity a = new AccountEntity();
    a.setId(UUID.randomUUID());
    a.setUserId(userId);
    a.setAccountNumber(accountNumbers.next());
    a.setAccountType(accountType);
    a.setCurrency("VND");
    a.setBalance(initialBalance);
    a.setStatus(AccountStatus.ACTIVE.name());
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return mapper.toResponse(accountRepository.save(a));
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listMine(UUID userId) {
    return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(mapper::toResponse)
        .toList();
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
