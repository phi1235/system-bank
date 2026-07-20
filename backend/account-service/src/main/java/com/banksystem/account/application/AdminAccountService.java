package com.banksystem.account.application;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.AccountStatus;
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

/** Staff-facing account use-cases (search, freeze, unfreeze). */
@Service
public class AdminAccountService {

  private final AccountRepository accountRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;

  public AdminAccountService(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper) {
    this.accountRepository = accountRepository;
    this.access = access;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PageResponse<AccountResponse> adminList(AdminAccountSearchQuery query) {
    String status = query.status() == null
        ? null
        : AccountStatus.parseRequired(query.status()).name();

    // Staff may paste account number, account UUID, or owner user UUID in the same `q` box.
    UUID userId = null;
    UUID accountId = null;
    if (query.q() != null) {
      UUID asUuid = tryParseUuid(query.q());
      if (asUuid != null) {
        userId = asUuid;
        accountId = asUuid;
      }
    }

    Page<AccountEntity> result = accountRepository.adminSearch(
        query.q(),
        status,
        userId,
        accountId,
        PageRequest.of(query.page(), query.size()));
    List<AccountResponse> items = result.getContent().stream().map(mapper::toResponse).toList();
    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
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
    return mapper.toResponse(accountRepository.save(a));
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
    return mapper.toResponse(accountRepository.save(a));
  }

  private UUID tryParseUuid(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
