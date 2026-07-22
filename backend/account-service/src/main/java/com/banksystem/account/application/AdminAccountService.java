package com.banksystem.account.application;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.AccountStatus;
import com.banksystem.account.domain.AccountType;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-facing account use-cases (search, detail, freeze, unfreeze). */
@Service
public class AdminAccountService {

  private final AccountRepository accountRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final OpsAlertPublisher opsAlertPublisher;

  public AdminAccountService(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper,
      OpsAlertPublisher opsAlertPublisher) {
    this.accountRepository = accountRepository;
    this.access = access;
    this.mapper = mapper;
    this.opsAlertPublisher = opsAlertPublisher;
  }

  @Transactional(readOnly = true)
  public PageResponse<AccountResponse> adminList(AdminAccountSearchQuery query) {
    String status = query.status() == null
        ? null
        : AccountStatus.parseRequired(query.status()).name();
    String accountType = query.accountType() == null
        ? null
        : AccountType.parseRequired(query.accountType()).name();

    boolean hasQ = query.q() != null;
    String q = hasQ ? query.q() : "";
    UUID userId = null;
    UUID accountId = null;
    if (hasQ) {
      UUID asUuid = tryParseUuid(query.q());
      if (asUuid != null) {
        userId = asUuid;
        accountId = asUuid;
      }
    }

    boolean hasStatus = status != null;
    boolean hasType = accountType != null;
    boolean hasUserId = userId != null;
    boolean hasAccountId = accountId != null;

    // Concrete UUID binds when flags are false (Postgres-safe).
    UUID boundUserId = hasUserId ? userId : new UUID(0L, 0L);
    UUID boundAccountId = hasAccountId ? accountId : new UUID(0L, 0L);
    String boundStatus = hasStatus ? status : "";
    String boundType = hasType ? accountType : "";

    Page<AccountEntity> result = accountRepository.adminSearch(
        hasQ,
        q,
        hasStatus,
        boundStatus,
        hasType,
        boundType,
        hasUserId,
        boundUserId,
        hasAccountId,
        boundAccountId,
        PageRequest.of(query.page(), query.size()));
    List<AccountResponse> items = result.getContent().stream().map(mapper::toResponse).toList();
    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AccountResponse get(UUID id) {
    return mapper.toResponse(access.require(id));
  }

  @Transactional
  public AccountResponse freeze(UUID id) {
    AccountEntity a = access.require(id);
    AccountStatus current = access.currentStatus(a);
    if (current.isClosed()) {
      throw new BusinessException("ACCOUNT_CLOSED", "Closed account cannot be frozen",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (current.isFrozen()) {
      return mapper.toResponse(a);
    }
    a.setStatus(AccountStatus.FROZEN.name());
    a.setUpdatedAt(Instant.now());
    AccountEntity saved = accountRepository.save(a);
    opsAlertPublisher.accountFrozen(saved);
    return mapper.toResponse(saved);
  }

  @Transactional
  public AccountResponse unfreeze(UUID id) {
    AccountEntity a = access.require(id);
    AccountStatus current = access.currentStatus(a);
    if (current.isClosed()) {
      throw new BusinessException("ACCOUNT_CLOSED", "Closed account cannot be unfrozen",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (current.isActive()) {
      return mapper.toResponse(a);
    }
    a.setStatus(AccountStatus.ACTIVE.name());
    a.setUpdatedAt(Instant.now());
    AccountEntity saved = accountRepository.save(a);
    opsAlertPublisher.accountUnfrozen(saved);
    return mapper.toResponse(saved);
  }

  private UUID tryParseUuid(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
