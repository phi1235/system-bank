package com.banksystem.transaction.application;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.AdminTransferFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.SagaStepResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import com.banksystem.transaction.domain.SagaStepLogEntity;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import com.banksystem.transaction.application.query.AdminTransferListQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final TransferOrderRepository transferOrderRepository;
  private final AuditLogRepository auditLogRepository;
  private final SagaStepLogRepository sagaStepLogRepository;
  private final AccountClient accountClient;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final TransferLimitPolicy transferLimitPolicy;
  private final TransferFeePolicy transferFeePolicy;
  private final String internalApiKey;

  public TransferService(
      TransferOrderRepository transferOrderRepository,
      AuditLogRepository auditLogRepository,
      SagaStepLogRepository sagaStepLogRepository,
      AccountClient accountClient,
      TransferSagaOrchestrator sagaOrchestrator,
      TransferLimitPolicy transferLimitPolicy,
      TransferFeePolicy transferFeePolicy,
      @Value("${bank.internal.account-api-key}") String internalApiKey) {
    this.transferOrderRepository = transferOrderRepository;
    this.auditLogRepository = auditLogRepository;
    this.sagaStepLogRepository = sagaStepLogRepository;
    this.accountClient = accountClient;
    this.sagaOrchestrator = sagaOrchestrator;
    this.transferLimitPolicy = transferLimitPolicy;
    this.transferFeePolicy = transferFeePolicy;
    this.internalApiKey = internalApiKey;
  }

  /**
   * Read-only quote: fee for amount (if provided) + daily spent/remaining for the user.
   * Does not validate account ownership or create an order.
   */
  @Transactional(readOnly = true)
  public TransferQuoteResponse quote(UUID userId, BigDecimal amount) {
    BigDecimal principal = amount == null ? BigDecimal.ZERO : amount;
    if (principal.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be non-negative", HttpStatus.BAD_REQUEST);
    }
    BigDecimal fee = BigDecimal.ZERO.setScale(2);
    BigDecimal total = principal.setScale(2);
    BigDecimal feeFlat = transferFeePolicy.flat().setScale(2);
    BigDecimal feePercent = transferFeePolicy.percent();
    BigDecimal feePercentAmount = BigDecimal.ZERO.setScale(2);
    BigDecimal feeMin = transferFeePolicy.minFee().setScale(2);
    BigDecimal feeMax = transferFeePolicy.maxFee().setScale(2);
    BigDecimal feeRaw = BigDecimal.ZERO.setScale(2);
    boolean cappedByMin = false;
    boolean cappedByMax = false;
    if (principal.compareTo(BigDecimal.ZERO) > 0) {
      TransferFeePolicy.FeeBreakdown b = transferFeePolicy.breakdown(principal);
      fee = b.feeAmount();
      total = principal.add(fee).setScale(2);
      feeFlat = b.flat();
      feePercent = b.percent();
      feePercentAmount = b.percentAmount();
      feeMin = b.minFee();
      feeMax = b.maxFee();
      feeRaw = b.rawBeforeClamp();
      cappedByMin = b.cappedByMin();
      cappedByMax = b.cappedByMax();
    }
    BigDecimal spent = transferLimitPolicy.spentToday(userId);
    BigDecimal remaining = transferLimitPolicy.remainingToday(userId);
    return new TransferQuoteResponse(
        principal,
        fee,
        total,
        transferLimitPolicy.maxPerTransaction(),
        transferLimitPolicy.dailyLimit(),
        spent,
        remaining,
        "VND",
        transferLimitPolicy.dailyLimitZone().getId(),
        transferFeePolicy.enabled(),
        feeFlat,
        feePercent,
        feePercentAmount,
        feeMin,
        feeMax,
        feeRaw,
        cappedByMin,
        cappedByMax);
  }

  public TransferResponse transfer(GatewayUser user, String idempotencyKey, TransferRequest req, String ip) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException("IDEMPOTENCY_REQUIRED", "Idempotency-Key header is required",
          HttpStatus.BAD_REQUEST);
    }
    if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);
    }

    String fingerprint = fingerprint(req);
    var existing = transferOrderRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      TransferOrderEntity e = existing.get();
      if (!e.getRequestFingerprint().equals(fingerprint)) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT",
            "Idempotency-Key reused with different payload", HttpStatus.CONFLICT);
      }
      return toResponse(e);
    }

    // Enforce limits before any external side effects / order creation.
    // Daily / per-tx limits apply to principal only (fee is separate product rule).
    transferLimitPolicy.validate(user.userId(), req.amount());
    BigDecimal feeAmount = transferFeePolicy.calculate(req.amount());

    AccountView from = loadAccount(req.fromAccountId());
    if (!from.userIdUuid().equals(user.userId()) && !user.hasPermission("transactions:list:view")) {
      throw new BusinessException("FORBIDDEN", "Source account is not yours", HttpStatus.FORBIDDEN);
    }
    if (!"ACTIVE".equals(from.status())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Source account is not active",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    AccountView to = loadByNumber(req.toAccountNumber());
    if (from.idUuid().equals(to.idUuid())) {
      throw new BusinessException("SAME_ACCOUNT", "Cannot transfer to the same account",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (!"ACTIVE".equals(to.status())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Destination account is not active",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    TransferOrderEntity order = new TransferOrderEntity();
    order.setId(UUID.randomUUID());
    order.setIdempotencyKey(idempotencyKey);
    order.setUserId(user.userId());
    order.setFromAccountId(from.idUuid());
    order.setToAccountId(to.idUuid());
    order.setToAccountNumber(to.accountNumber());
    order.setAmount(req.amount());
    order.setFeeAmount(feeAmount);
    order.setCurrency(req.currency() == null || req.currency().isBlank() ? "VND" : req.currency());
    order.setDescription(req.description());
    order.setRequestFingerprint(fingerprint);
    order.setStatus(TransferStatus.PENDING);
    order.setCreatedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);

    auditLogRepository.save(AuditLogEntity.of(
        user.userId(), "TRANSFER_CREATE", "TRANSFER", order.getId().toString(), ip,
        "amount=" + req.amount() + ",fee=" + feeAmount.toPlainString() + ",to=" + req.toAccountNumber()));

    TransferOrderEntity result = sagaOrchestrator.run(order);
    return toResponse(result);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> myHistory(UUID userId, int page, int size) {
    return myHistory(userId, page, size, null, null, null);
  }

  /**
   * Customer transfer history with optional status and createdAt range filters.
   * Invalid status strings yield an empty page (same as no matches), not 500.
   */
  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> myHistory(
      UUID userId,
      int page,
      int size,
      String status,
      Instant from,
      Instant to) {
    int cappedSize = Math.min(Math.max(size, 1), 100);
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessException(
          "INVALID_DATE_RANGE", "from must be before or equal to to", HttpStatus.BAD_REQUEST);
    }
    TransferStatus st = parseStatusOrNull(status);
    if (status != null && !status.isBlank() && st == null) {
      return new PageResponse<>(List.of(), page, cappedSize, 0, 0);
    }
    // Postgres cannot infer types for NULL Instant/enum binds in "(:p IS NULL OR ...)".
    // Always pass concrete bounds + a boolean status flag; dummy enum when not filtering.
    Instant fromTs = from != null ? from : Instant.EPOCH;
    Instant toTs = to != null ? to : Instant.parse("9999-12-31T23:59:59.999999999Z");
    boolean hasStatus = st != null;
    TransferStatus statusParam = hasStatus ? st : TransferStatus.PENDING;
    Page<TransferOrderEntity> p = transferOrderRepository.searchMine(
        userId, hasStatus, statusParam, fromTs, toTs, PageRequest.of(page, cappedSize));
    return mapPage(p);
  }

  @Transactional(readOnly = true)
  public Object adminTransfers(AdminTransferFilterRequest req) {
    var query = AdminTransferListQuery.of(
        req.status(),
        req.transferId(),
        req.q(),
        req.from(),
        req.to(),
        req.page(),
        req.size(),
        req.lastCreatedAt());
    if (req.noCount()) {
      return adminListSlice(query);
    }
    return adminList(query);
  }

  private static TransferStatus parseStatusOrNull(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return TransferStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID id, GatewayUser user) {
    return toResponse(requireReadable(id, user));
  }

  /**
   * Transfer order plus ordered saga step logs (owner or staff with transactions:list:view).
   */
  @Transactional(readOnly = true)
  public TransferDetailResponse getDetail(UUID id, GatewayUser user) {
    TransferOrderEntity e = requireReadable(id, user);
    List<SagaStepResponse> steps = sagaStepLogRepository
        .findByTransferIdOrderByCreatedAtAsc(e.getId())
        .stream()
        .map(this::toStep)
        .toList();
    return new TransferDetailResponse(toResponse(e), steps);
  }

  private TransferOrderEntity requireReadable(UUID id, GatewayUser user) {
    TransferOrderEntity e = transferOrderRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found",
            HttpStatus.NOT_FOUND));
    if (!user.hasPermission("transactions:list:view") && !e.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your transfer", HttpStatus.FORBIDDEN);
    }
    return e;
  }

  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> adminList(AdminTransferListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Page<TransferOrderEntity> p =
        transferOrderRepository.adminSearch(
            query.hasStatus(),
            query.hasStatus() ? query.status() : TransferStatus.PENDING,
            query.hasTransferId(),
            query.hasTransferId() ? query.transferId() : new UUID(0L, 0L),
            query.hasQ(),
            query.hasQ() ? query.q() : "",
            query.from(),
            query.to(),
            pageable);
    return mapPage(p);
  }

  @Transactional(readOnly = true)
  public List<TransferResponse> adminListSlice(AdminTransferListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Slice<TransferOrderEntity> slice =
        transferOrderRepository.adminSearchSlice(
            query.hasStatus(),
            query.hasStatus() ? query.status() : TransferStatus.PENDING,
            query.hasTransferId(),
            query.hasTransferId() ? query.transferId() : new UUID(0L, 0L),
            query.hasQ(),
            query.hasQ() ? query.q() : "",
            query.from(),
            query.to(),
            query.hasLastCreatedAt(),
            query.hasLastCreatedAt() ? query.lastCreatedAt() : Instant.EPOCH,
            pageable);
    return slice.getContent().stream().map(this::toResponse).toList();
  }

  private PageResponse<TransferResponse> mapPage(Page<TransferOrderEntity> p) {
    List<TransferResponse> items = p.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  private AccountView loadAccount(UUID id) {
    try {
      ApiResponse<AccountView> res = accountClient.getById(id, internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found", HttpStatus.NOT_FOUND);
      }
      return res.data();
    } catch (FeignException.NotFound e) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found", HttpStatus.NOT_FOUND);
    } catch (FeignException e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service unavailable",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private AccountView loadByNumber(String number) {
    try {
      ApiResponse<AccountView> res = accountClient.getByNumber(number, internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        throw new BusinessException("ACCOUNT_NOT_FOUND", "Destination account not found",
            HttpStatus.NOT_FOUND);
      }
      return res.data();
    } catch (FeignException.NotFound e) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Destination account not found",
          HttpStatus.NOT_FOUND);
    } catch (FeignException e) {
      throw new BusinessException("ACCOUNT_SERVICE_ERROR", "Account service unavailable",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private String fingerprint(TransferRequest req) {
    return req.fromAccountId() + "|" + req.toAccountNumber() + "|" + req.amount().toPlainString();
  }

  private TransferResponse toResponse(TransferOrderEntity e) {
    BigDecimal fee = e.getFeeAmount() == null ? BigDecimal.ZERO : e.getFeeAmount();
    return new TransferResponse(
        e.getId().toString(),
        e.getStatus().name(),
        e.getFromAccountId().toString(),
        e.getToAccountId() == null ? null : e.getToAccountId().toString(),
        e.getToAccountNumber(),
        e.getAmount(),
        fee,
        e.getCurrency(),
        e.getDescription(),
        e.getFailureReason(),
        e.getCreatedAt(),
        "INTERNAL",
        "SYSTEM_BANK",
        null
    );
  }

  private SagaStepResponse toStep(SagaStepLogEntity s) {
    return new SagaStepResponse(
        s.getId().toString(),
        s.getStep(),
        s.getStatus(),
        s.getDetail(),
        s.getCreatedAt()
    );
  }
}
