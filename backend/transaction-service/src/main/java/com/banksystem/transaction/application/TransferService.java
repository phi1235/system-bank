package com.banksystem.transaction.application;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.config.GatewayUser;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final TransferOrderRepository transferOrderRepository;
  private final AuditLogRepository auditLogRepository;
  private final AccountClient accountClient;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final String internalApiKey;

  public TransferService(
      TransferOrderRepository transferOrderRepository,
      AuditLogRepository auditLogRepository,
      AccountClient accountClient,
      TransferSagaOrchestrator sagaOrchestrator,
      @Value("${bank.internal.api-key}") String internalApiKey) {
    this.transferOrderRepository = transferOrderRepository;
    this.auditLogRepository = auditLogRepository;
    this.accountClient = accountClient;
    this.sagaOrchestrator = sagaOrchestrator;
    this.internalApiKey = internalApiKey;
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
    order.setCurrency(req.currency() == null || req.currency().isBlank() ? "VND" : req.currency());
    order.setDescription(req.description());
    order.setRequestFingerprint(fingerprint);
    order.setStatus(TransferStatus.PENDING);
    order.setCreatedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    transferOrderRepository.save(order);

    auditLogRepository.save(AuditLogEntity.of(
        user.userId(), "TRANSFER_CREATE", "TRANSFER", order.getId().toString(), ip,
        "amount=" + req.amount() + ",to=" + req.toAccountNumber()));

    TransferOrderEntity result = sagaOrchestrator.run(order);
    return toResponse(result);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> myHistory(UUID userId, int page, int size) {
    Page<TransferOrderEntity> p =
        transferOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    return mapPage(p);
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID id, GatewayUser user) {
    TransferOrderEntity e = transferOrderRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found",
            HttpStatus.NOT_FOUND));
    if (!user.hasPermission("transactions:list:view") && !e.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your transfer", HttpStatus.FORBIDDEN);
    }
    return toResponse(e);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> adminList(String status, int page, int size) {
    TransferStatus st = null;
    if (status != null && !status.isBlank()) {
      st = TransferStatus.valueOf(status);
    }
    Page<TransferOrderEntity> p = transferOrderRepository.adminSearch(st, PageRequest.of(page, size));
    return mapPage(p);
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
    return new TransferResponse(
        e.getId().toString(),
        e.getStatus().name(),
        e.getFromAccountId().toString(),
        e.getToAccountId() == null ? null : e.getToAccountId().toString(),
        e.getToAccountNumber(),
        e.getAmount(),
        e.getCurrency(),
        e.getDescription(),
        e.getFailureReason(),
        e.getCreatedAt()
    );
  }
}
