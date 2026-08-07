package com.banksystem.transaction.application.transfer.impl;

import com.banksystem.transaction.application.transfer.TransferService;
import com.banksystem.transaction.application.transfer.TransferQueryService;
import com.banksystem.transaction.application.transfer.TransferFeeGlService;
import com.banksystem.transaction.application.transfer.policy.TransferLimitPolicy;
import com.banksystem.transaction.application.transfer.policy.TransferFeePolicy;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.transaction.api.dto.TransferDtos.AdminTransferFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.MyTransferFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.transfer.AdminTransferListQuery;
import com.banksystem.transaction.application.transfer.TransferService;
import com.banksystem.transaction.application.transfer.TransferQueryService;
import com.banksystem.transaction.application.transfer.policy.TransferFeePolicy;
import com.banksystem.transaction.application.transfer.policy.TransferLimitPolicy;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferServiceImpl implements TransferService {

  private final TransferOrderRepository transferOrderRepository;
  private final AuditLogRepository auditLogRepository;
  private final AccountGateway accountGateway;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final TransferLimitPolicy transferLimitPolicy;
  private final TransferFeePolicy transferFeePolicy;
  private final TransferQueryService queryService;
  private final TransferMapper mapper;

  public TransferServiceImpl(
      TransferOrderRepository transferOrderRepository,
      AuditLogRepository auditLogRepository,
      AccountGateway accountGateway,
      TransferSagaOrchestrator sagaOrchestrator,
      TransferLimitPolicy transferLimitPolicy,
      TransferFeePolicy transferFeePolicy,
      TransferQueryService queryService,
      TransferMapper mapper) {
    this.transferOrderRepository = transferOrderRepository;
    this.auditLogRepository = auditLogRepository;
    this.accountGateway = accountGateway;
    this.sagaOrchestrator = sagaOrchestrator;
    this.transferLimitPolicy = transferLimitPolicy;
    this.transferFeePolicy = transferFeePolicy;
    this.queryService = queryService;
    this.mapper = mapper;
  }

  public TransferResponse transfer(GatewayUser user, String idempotencyKey, TransferRequest req, String ip) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException("IDEMPOTENCY_REQUIRED", "Idempotency-Key header is required");
    }
    if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive");
    }

    String fingerprint = fingerprint(req);
    var existing = transferOrderRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      TransferOrderEntity e = existing.get();
      if (!e.getRequestFingerprint().equals(fingerprint)) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Idempotency-Key reused with different payload");
      }
      return mapper.toResponse(e);
    }

    transferLimitPolicy.validate(user.userId(), req.amount());
    BigDecimal feeAmount = transferFeePolicy.calculate(req.amount());

    AccountView from = loadAccount(req.fromAccountId());
    if (!from.userIdUuid().equals(user.userId()) && !user.hasPermission("transactions:list:view")) {
      throw new BusinessException("FORBIDDEN", "Source account is not yours");
    }
    if (!"ACTIVE".equals(from.status())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Source account is not active");
    }

    AccountView to = loadByNumber(req.toAccountNumber());
    if (from.idUuid().equals(to.idUuid())) {
      throw new BusinessException("SAME_ACCOUNT", "Cannot transfer to the same account");
    }
    if (!"ACTIVE".equals(to.status())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Destination account is not active");
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
    return mapper.toResponse(result);
  }

  public TransferQuoteResponse quote(UUID userId, BigDecimal amount) {
    return queryService.quote(userId, amount);
  }

  public PageResponse<TransferResponse> myHistory(UUID userId, MyTransferFilterRequest req) {
    return queryService.myHistory(userId, req.page(), req.size(), req.status(), req.from(), req.to());
  }

  public PageResponse<TransferResponse> myHistory(UUID userId, int page, int size, String status, Instant from, Instant to) {
    return queryService.myHistory(userId, page, size, status, from, to);
  }

  public Object adminTransfers(AdminTransferFilterRequest req) {
    return queryService.adminTransfers(req);
  }

  public TransferResponse get(UUID id, GatewayUser user) {
    return queryService.get(id, user);
  }

  public TransferDetailResponse getDetail(UUID id, GatewayUser user) {
    return queryService.getDetail(id, user);
  }

  private AccountView loadAccount(UUID id) {
    AccountView view = accountGateway.getAccount(id);
    if (view == null) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Source account not found");
    }
    return view;
  }

  private AccountView loadByNumber(String number) {
    AccountView view = accountGateway.getAccountByNumber(number);
    if (view == null) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Destination account not found");
    }
    return view;
  }

  private String fingerprint(TransferRequest req) {
    return req.fromAccountId() + "|" + req.toAccountNumber() + "|" + req.amount().toPlainString();
  }
}
