package com.banksystem.account.application.account.impl;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.AdminAccountFilterRequest;
import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.account.application.gateway.AuditGateway;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-facing account use-cases (search, detail, freeze, unfreeze, top-up). */
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

  private final AccountRepository accountRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final OpsAlertPublisher opsAlertPublisher;
  private final AccountMoneyService moneyService;
  private final AuditGateway auditGateway;
  private final BigDecimal maxTopUpAmount;

  public AdminAccountServiceImpl(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper,
      OpsAlertPublisher opsAlertPublisher,
      AccountMoneyService moneyService,
      BigDecimal maxTopUpAmount) {
    this(accountRepository, access, mapper, opsAlertPublisher, moneyService, null, maxTopUpAmount);
  }

  @Autowired
  public AdminAccountServiceImpl(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper,
      OpsAlertPublisher opsAlertPublisher,
      AccountMoneyService moneyService,
      AuditGateway auditGateway,
      @Value("${bank.account.topup.max-amount:50000000}") BigDecimal maxTopUpAmount) {
    this.accountRepository = accountRepository;
    this.access = access;
    this.mapper = mapper;
    this.opsAlertPublisher = opsAlertPublisher;
    this.moneyService = moneyService;
    this.auditGateway = auditGateway;
    this.maxTopUpAmount = maxTopUpAmount;
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
  public Object adminList(AdminAccountFilterRequest req) {
    AdminAccountSearchQuery query = AdminAccountSearchQuery.of(req.q(), req.status(), req.accountType(), req.page(), req.size());
    if (Boolean.TRUE.equals(req.noCount())) {
      return adminListSlice(query);
    }
    return adminList(query);
  }

  @Transactional(readOnly = true)
  public Object adminList(AdminAccountSearchQuery query, boolean noCount) {
    if (noCount) {
      return adminListSlice(query);
    }
    return adminList(query);
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> adminListSlice(AdminAccountSearchQuery query) {
    String status = query.status();
    String accountType = query.accountType();
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

    UUID boundUserId = hasUserId ? userId : new UUID(0L, 0L);
    UUID boundAccountId = hasAccountId ? accountId : new UUID(0L, 0L);
    String boundStatus = hasStatus ? status : "";
    String boundType = hasType ? accountType : "";

    Slice<AccountEntity> slice = accountRepository.adminSearchSlice(
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
    return slice.getContent().stream().map(mapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public AccountResponse get(UUID id) {
    return mapper.toResponse(access.require(id));
  }

  @Transactional
  public AccountResponse freeze(UUID id) {
    return freeze(id, null);
  }

  @Transactional
  public AccountResponse freeze(UUID id, GatewayUser actor) {
    AccountEntity a = access.require(id);
    AccountStatus current = access.currentStatus(a);
    if (current.isClosed()) {
      throw new BusinessException("ACCOUNT_CLOSED", "Closed account cannot be frozen");
    }
    if (current.isFrozen()) {
      return mapper.toResponse(a);
    }
    a.setStatus(AccountStatus.FROZEN.name());
    a.setUpdatedAt(Instant.now());
    AccountEntity saved = accountRepository.save(a);
    opsAlertPublisher.accountFrozen(saved);
    UUID actorId = actor != null ? actor.userId() : null;
    recordAuditLog(actorId, "ACCOUNT_FREEZE", "ACCOUNT", saved.getId().toString(), "status=FROZEN");
    return mapper.toResponse(saved);
  }

  @Transactional
  public AccountResponse unfreeze(UUID id) {
    return unfreeze(id, null);
  }

  @Transactional
  public AccountResponse unfreeze(UUID id, GatewayUser actor) {
    AccountEntity a = access.require(id);
    AccountStatus current = access.currentStatus(a);
    if (current.isClosed()) {
      throw new BusinessException("ACCOUNT_CLOSED", "Closed account cannot be unfrozen");
    }
    if (current.isActive()) {
      return mapper.toResponse(a);
    }
    a.setStatus(AccountStatus.ACTIVE.name());
    a.setUpdatedAt(Instant.now());
    AccountEntity saved = accountRepository.save(a);
    opsAlertPublisher.accountUnfrozen(saved);
    UUID actorId = actor != null ? actor.userId() : null;
    recordAuditLog(actorId, "ACCOUNT_UNFREEZE", "ACCOUNT", saved.getId().toString(), "status=ACTIVE");
    return mapper.toResponse(saved);
  }

  @Transactional
  public TopUpResponse topUp(UUID id, TopUpRequest req, GatewayUser actor) {
    if (req == null || req.amount() == null || req.amount().compareTo(new BigDecimal("0.01")) < 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be at least 0.01");
    }
    if (req.amount().compareTo(maxTopUpAmount) > 0) {
      throw new BusinessException("TOPUP_MAX_EXCEEDED", "Top-up amount exceeds maximum allowed limit");
    }

    AccountEntity account = access.require(id);
    if (actor != null && actor.userId() != null && account.getUserId() != null && account.getUserId().equals(actor.userId())) {
      throw new BusinessException("SELF_TOPUP_FORBIDDEN", "Staff cannot credit their own account");
    }
    String referenceId = "ADMIN-TOPUP-" + UUID.randomUUID();
    String note = req.description() != null ? req.description().trim() : "";
    String description = note.isEmpty() ? "Admin top-up" : "Admin top-up: " + note;

    MoneyCommand cmd = new MoneyCommand(req.amount(), referenceId, description, null);
    MoneyResult result = moneyService.credit(id, cmd);

    UUID actorId = actor != null ? actor.userId() : null;
    recordAuditLog(actorId, "ACCOUNT_TOPUP", "ACCOUNT", account.getId().toString(), "amount=" + req.amount() + ",ref=" + referenceId);

    return new TopUpResponse(
        account.getId().toString(),
        account.getAccountNumber(),
        result.ledgerEntryId(),
        referenceId,
        req.amount(),
        result.balanceAfter(),
        Instant.now());
  }

  private void recordAuditLog(UUID actorId, String action, String resourceType, String resourceId, String metadata) {
    if (auditGateway != null) {
      auditGateway.recordAuditLog(actorId, action, resourceType, resourceId, metadata);
    }
  }

  private UUID tryParseUuid(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
