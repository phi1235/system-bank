package com.banksystem.transaction.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.transaction.api.dto.TransferDtos.AdminTransferFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.SagaStepResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.query.AdminTransferListQuery;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferQueryService {

  private final TransferOrderRepository transferOrderRepository;
  private final SagaStepLogRepository sagaStepLogRepository;
  private final TransferLimitPolicy transferLimitPolicy;
  private final TransferFeePolicy transferFeePolicy;
  private final TransferMapper mapper;
  private final JdbcTemplate jdbcTemplate;

  public TransferQueryService(
      TransferOrderRepository transferOrderRepository,
      SagaStepLogRepository sagaStepLogRepository,
      TransferLimitPolicy transferLimitPolicy,
      TransferFeePolicy transferFeePolicy,
      TransferMapper mapper,
      JdbcTemplate jdbcTemplate) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaStepLogRepository = sagaStepLogRepository;
    this.transferLimitPolicy = transferLimitPolicy;
    this.transferFeePolicy = transferFeePolicy;
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public TransferQuoteResponse quote(UUID userId, BigDecimal amount) {
    BigDecimal principal = amount == null ? BigDecimal.ZERO : amount;
    if (principal.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be non-negative");
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
      throw new BusinessException("INVALID_DATE_RANGE", "from must be before or equal to to");
    }
    TransferStatus st = parseStatusOrNull(status);
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
    if (Boolean.TRUE.equals(req.noCount())) {
      return adminListSlice(query);
    }
    return adminList(query);
  }

  @Transactional(readOnly = true)
  public PageResponse<TransferResponse> adminList(AdminTransferListQuery query) {
    Instant fromTs = query.from() != null ? query.from() : Instant.EPOCH;
    Instant toTs = query.to() != null ? query.to() : Instant.parse("9999-12-31T23:59:59.999999999Z");
    TransferStatus statusParam = query.hasStatus() ? query.status() : TransferStatus.PENDING;
    // Use Slice (no COUNT) + estimated total to avoid slow COUNT(*) on 1M+ rows
    Slice<TransferOrderEntity> slice = transferOrderRepository.adminSearchSlice(
        query.hasStatus(),
        statusParam,
        query.hasTransferId(),
        query.transferId(),
        query.hasQ(),
        query.q(),
        fromTs,
        toTs,
        false,
        Instant.now(),
        PageRequest.of(query.page(), query.size()));
    List<TransferResponse> items = slice.getContent().stream().map(mapper::toResponse).toList();
    long estimatedTotal = estimatedRowCount("transfer_orders");
    int totalPages = (int) Math.ceil((double) estimatedTotal / query.size());
    return new PageResponse<>(items, query.page(), query.size(), estimatedTotal, totalPages);
  }

  @Transactional(readOnly = true)
  public List<TransferResponse> adminListSlice(AdminTransferListQuery query) {
    Instant fromTs = query.from() != null ? query.from() : Instant.EPOCH;
    Instant toTs = query.to() != null ? query.to() : Instant.parse("9999-12-31T23:59:59.999999999Z");
    TransferStatus statusParam = query.hasStatus() ? query.status() : TransferStatus.PENDING;
    Slice<TransferOrderEntity> slice = transferOrderRepository.adminSearchSlice(
        query.hasStatus(),
        statusParam,
        query.hasTransferId(),
        query.transferId(),
        query.hasQ(),
        query.q(),
        fromTs,
        toTs,
        query.hasLastCreatedAt(),
        query.lastCreatedAt(),
        PageRequest.of(query.page(), query.size()));
    return slice.getContent().stream().map(mapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID id, GatewayUser user) {
    return mapper.toResponse(requireReadable(id, user));
  }

  @Transactional(readOnly = true)
  public TransferDetailResponse getDetail(UUID id, GatewayUser user) {
    TransferOrderEntity e = requireReadable(id, user);
    List<SagaStepResponse> steps = sagaStepLogRepository
        .findByTransferIdOrderByCreatedAtAsc(e.getId())
        .stream()
        .map(mapper::toStep)
        .toList();
    return new TransferDetailResponse(mapper.toResponse(e), steps);
  }

  private TransferOrderEntity requireReadable(UUID id, GatewayUser user) {
    TransferOrderEntity e = transferOrderRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    if (user.hasPermission("transactions:list:view") || user.hasRole("STAFF")) {
      return e;
    }
    if (!e.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your transfer");
    }
    return e;
  }

  private PageResponse<TransferResponse> mapPage(Page<TransferOrderEntity> p) {
    List<TransferResponse> items = p.getContent().stream().map(mapper::toResponse).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
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

  /**
   * PostgreSQL estimated row count from pg_stat_user_tables (O(1)).
   * Avoids slow COUNT(*) on large tables (1M+ rows).
   */
  private long estimatedRowCount(String tableName) {
    Long estimate = jdbcTemplate.queryForObject(
        "SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?",
        Long.class, tableName);
    return (estimate != null && estimate > 0) ? estimate : 0;
  }
}
