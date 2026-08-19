package com.banksystem.transaction.application.transfer.impl;

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
import com.banksystem.transaction.application.risk.RiskEngine;
import com.banksystem.transaction.application.risk.RiskEngine.RiskResult;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService.VerifiedBinding;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferServiceImpl implements TransferService {

  private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

  private final TransferOrderRepository transferOrderRepository;
  private final AuditLogRepository auditLogRepository;
  private final AccountGateway accountGateway;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final TransferLimitPolicy transferLimitPolicy;
  private final TransferFeePolicy transferFeePolicy;
  private final TransferQueryService queryService;
  private final TransferMapper mapper;
  private final BeneficiaryInquiryService inquiryService;
  private final RiskEngine riskEngine;
  private final TransactionTemplate transactionTemplate;

  public TransferServiceImpl(
      TransferOrderRepository transferOrderRepository,
      AuditLogRepository auditLogRepository,
      AccountGateway accountGateway,
      TransferSagaOrchestrator sagaOrchestrator,
      TransferLimitPolicy transferLimitPolicy,
      TransferFeePolicy transferFeePolicy,
      TransferQueryService queryService,
      TransferMapper mapper,
      BeneficiaryInquiryService inquiryService,
      RiskEngine riskEngine,
      TransactionTemplate transactionTemplate) {
    this.transferOrderRepository = transferOrderRepository;
    this.auditLogRepository = auditLogRepository;
    this.accountGateway = accountGateway;
    this.sagaOrchestrator = sagaOrchestrator;
    this.transferLimitPolicy = transferLimitPolicy;
    this.transferFeePolicy = transferFeePolicy;
    this.queryService = queryService;
    this.mapper = mapper;
    this.inquiryService = inquiryService;
    this.riskEngine = riskEngine;
    this.transactionTemplate = transactionTemplate;
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
      if (!e.getUserId().equals(user.userId())) {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Idempotency-Key is already in use");
      }
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

    boolean interbank = "INTERBANK".equalsIgnoreCase(req.transferType());
    AccountView to = null;
    String targetBankCode = interbank ? normalizeBankCode(req.targetBankCode()) : "SYSTEM_BANK";
    String targetAccountName = req.targetAccountName();
    VerifiedBinding verifiedBinding = null;
    if (interbank) {
      verifiedBinding = inquiryService.validateForTransfer(
          user.userId(), req.inquiryId(), targetBankCode, req.toAccountNumber());
      targetBankCode = verifiedBinding.bankCode();
      targetAccountName = verifiedBinding.accountName();
    } else {
      to = loadByNumber(req.toAccountNumber());
      if (from.idUuid().equals(to.idUuid())) {
        throw new BusinessException("SAME_ACCOUNT", "Cannot transfer to the same account");
      }
      if (!"ACTIVE".equals(to.status())) {
        throw new BusinessException("ACCOUNT_FROZEN", "Destination account is not active");
      }
      targetAccountName = "TK KH (" + lastFour(to.accountNumber()) + ")";
    }

    TransferOrderEntity order = new TransferOrderEntity();
    order.setId(UUID.randomUUID());
    order.setIdempotencyKey(idempotencyKey);
    order.setUserId(user.userId());
    order.setFromAccountId(from.idUuid());
    order.setToAccountId(to == null ? null : to.idUuid());
    order.setToAccountNumber(req.toAccountNumber().trim());
    order.setTransferType(interbank ? "INTERBANK" : "INTERNAL");
    order.setTargetBankCode(targetBankCode);
    order.setTargetAccountName(targetAccountName);
    order.setBeneficiaryInquiryId(verifiedBinding == null ? null : verifiedBinding.inquiryId());
    order.setAmount(req.amount());
    order.setFeeAmount(feeAmount);
    order.setTotalDebit(req.amount().add(feeAmount));
    order.setBankBin(interbank && verifiedBinding != null ? verifiedBinding.bankBin() : "970499");
    order.setRecipientName(targetAccountName);
    order.setCurrency(req.currency() == null || req.currency().isBlank() ? "VND" : req.currency());
    order.setDescription(req.description());
    order.setRequestFingerprint(fingerprint);
    order.setStatus(TransferStatus.PENDING);
    order.setCreatedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    TransferOrderEntity pendingOrder = order;
    VerifiedBinding pendingBinding = verifiedBinding;
    try {
      order = transactionTemplate.execute(status -> {
        if (pendingBinding != null) {
          inquiryService.consumeForTransfer(user.userId(), pendingBinding.inquiryId());
        }
        TransferOrderEntity saved = transferOrderRepository.saveAndFlush(pendingOrder);
        auditLogRepository.save(AuditLogEntity.of(
            user.userId(), "TRANSFER_CREATE", "TRANSFER", saved.getId().toString(), ip,
            "amount=" + req.amount() + ",fee=" + feeAmount.toPlainString()
                + ",beneficiaryInquiryId=" + String.valueOf(saved.getBeneficiaryInquiryId())));
        return saved;
      });
    } catch (RuntimeException createFailure) {
      // A concurrent retry can lose either the inquiry atomic-consume race or the
      // unique idempotency-key insert race. Once the winner commits, return that
      // exact order instead of exposing a false "inquiry consumed" failure.
      var concurrentWinner = transferOrderRepository.findByIdempotencyKey(idempotencyKey);
      if (concurrentWinner.isPresent()) {
        TransferOrderEntity winner = concurrentWinner.get();
        if (winner.getUserId().equals(user.userId())
            && winner.getRequestFingerprint().equals(fingerprint)) {
          return mapper.toResponse(winner);
        }
      }
      throw createFailure;
    }
    if (order == null) {
      throw new BusinessException("TRANSFER_CREATE_FAILED", "Could not create transfer order");
    }

    log.info("[TRANSFER-CREATE] Created transfer order [{}] User=[{}] Type=[{}] Amount={} {} From=[{}] To=[{}] (Bank: {})",
        order.getId(), user.userId(), order.getTransferType(), order.getAmount(), order.getCurrency(),
        maskAccount(from.accountNumber()), maskAccount(req.toAccountNumber()), targetBankCode);

    RiskResult risk = riskEngine.assess(order);
    order = transferOrderRepository.findById(order.getId())
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    log.info("[TRANSFER-RISK] Risk assessed for order [{}]: Decision=[{}] Score=[{}] Reason=[{}]",
        order.getId(), risk.decision(), risk.score(), risk.reason());

    if ("BLOCK".equals(risk.decision())) {
      order.setStatus(TransferStatus.FAILED);
      order.setFailureReason("RISK_BLOCKED: " + risk.reason());
      order.setUpdatedAt(Instant.now());
      order = transferOrderRepository.saveAndFlush(order);
      log.warn("[TRANSFER-BLOCKED] Transfer order [{}] was blocked by RiskEngine: Reason=[{}]", order.getId(), risk.reason());
      return mapper.toResponse(order);
    }
    if ("REVIEW".equals(risk.decision())) {
      order.setStatus(TransferStatus.RISK_REVIEW);
      order.setFailureReason("RISK_REVIEW_REQUIRED: " + risk.reason());
      order.setUpdatedAt(Instant.now());
      order = transferOrderRepository.saveAndFlush(order);
      log.warn("[TRANSFER-REVIEW] Transfer order [{}] requires manual risk review: Reason=[{}]", order.getId(), risk.reason());
      return mapper.toResponse(order);
    }

    log.info("[TRANSFER-SAGA] Starting saga execution for transfer order [{}]", order.getId());
    TransferOrderEntity result = sagaOrchestrator.run(order);
    log.info("[TRANSFER-COMPLETED] Transfer order [{}] completed with Status=[{}]", result.getId(), result.getStatus());
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
    return req.fromAccountId() + "|" + req.toAccountNumber().trim() + "|"
        + req.amount().toPlainString() + "|" + String.valueOf(req.transferType()) + "|"
        + String.valueOf(req.targetBankCode()) + "|" + String.valueOf(req.inquiryId());
  }

  private String normalizeBankCode(String bankCode) {
    if (bankCode == null || bankCode.isBlank()) {
      throw new BusinessException("TARGET_BANK_REQUIRED", "targetBankCode is required for interbank transfer");
    }
    String code = bankCode.trim().toUpperCase();
    if ("SYSTEM_BANK".equals(code) || "970499".equals(code)) {
      throw new BusinessException("INVALID_TARGET_BANK", "External bank must be selected");
    }
    return code;
  }

  private String maskAccount(String accountNumber) {
    return "******" + lastFour(accountNumber);
  }

  private String lastFour(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.substring(Math.max(0, value.length() - 4));
  }
}
