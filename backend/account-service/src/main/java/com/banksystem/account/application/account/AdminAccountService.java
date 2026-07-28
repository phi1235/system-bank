package com.banksystem.account.application.account;

import com.banksystem.account.api.dto.account.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.account.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.account.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.account.AccountDtos.TopUpResponse;
import com.banksystem.account.application.account.query.AdminAccountSearchQuery;
import com.banksystem.account.application.common.OpsAlertPublisher;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.enums.account.AccountStatus;
import com.banksystem.account.domain.enums.account.AccountType;
import com.banksystem.account.domain.repository.account.AccountRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-facing account use-cases (search, detail, freeze, unfreeze, top-up). */
@Service
public class AdminAccountService {

  private final AccountRepository accountRepository;
  private final AccountAccessService access;
  private final AccountMapper mapper;
  private final OpsAlertPublisher opsAlertPublisher;
  private final AccountMoneyService moneyService;
  private final com.banksystem.account.infrastructure.feign.AuditClient auditClient;
  private final String internalApiKey;
  private final BigDecimal maxTopUpAmount;

  public AdminAccountService(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper,
      OpsAlertPublisher opsAlertPublisher,
      AccountMoneyService moneyService,
      BigDecimal maxTopUpAmount) {
    this(accountRepository, access, mapper, opsAlertPublisher, moneyService, null, "", maxTopUpAmount);
  }

  @Autowired
  public AdminAccountService(
      AccountRepository accountRepository,
      AccountAccessService access,
      AccountMapper mapper,
      OpsAlertPublisher opsAlertPublisher,
      AccountMoneyService moneyService,
      com.banksystem.account.infrastructure.feign.AuditClient auditClient,
      @Value("${bank.internal.transaction-api-key}") String internalApiKey,
      @Value("${bank.account.topup.max-amount:50000000}") BigDecimal maxTopUpAmount) {
    this.accountRepository = accountRepository;
    this.access = access;
    this.mapper = mapper;
    this.opsAlertPublisher = opsAlertPublisher;
    this.moneyService = moneyService;
    this.auditClient = auditClient;
    this.internalApiKey = internalApiKey;
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
    UUID actorId = actor != null ? actor.userId() : null;
    recordAuditLog(actorId, "ACCOUNT_UNFREEZE", "ACCOUNT", saved.getId().toString(), "status=ACTIVE");
    return mapper.toResponse(saved);
  }

  @Transactional
  public TopUpResponse topUp(UUID id, TopUpRequest req, GatewayUser actor) {
    if (req == null || req.amount() == null || req.amount().compareTo(new BigDecimal("0.01")) < 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be at least 0.01", HttpStatus.BAD_REQUEST);
    }
    if (req.amount().compareTo(maxTopUpAmount) > 0) {
      throw new BusinessException("TOPUP_MAX_EXCEEDED", "Top-up amount exceeds maximum allowed limit", HttpStatus.BAD_REQUEST);
    }

    AccountEntity account = access.require(id);
    if (actor != null && actor.userId() != null && account.getUserId() != null && account.getUserId().equals(actor.userId())) {
      throw new BusinessException("SELF_TOPUP_FORBIDDEN", "Staff cannot credit their own account", HttpStatus.FORBIDDEN);
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
        "ADMIN");
  }

  private void recordAuditLog(UUID actorId, String action, String resourceType, String resourceId, String metadata) {
    if (auditClient == null) {
      return;
    }
    java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        auditClient.createAuditLog(
            new com.banksystem.account.infrastructure.feign.AuditClient.CreateAuditLogRequest(
                actorId, action, resourceType, resourceId, "127.0.0.1", metadata),
            internalApiKey);
      } catch (Exception ex) {
        // Non-blocking best-effort audit logging
      }
    });
  }

  private UUID tryParseUuid(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
