package com.banksystem.account.application.deposit.impl;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.DepositDtos.AdminDepositFilterRequest;
import com.banksystem.account.api.dto.DepositDtos.AdminTermDepositRow;
import com.banksystem.account.api.dto.DepositDtos.DepositAdminSummaryResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.UpdateDepositProductRequest;
import com.banksystem.account.application.gateway.AuditGateway;
import com.banksystem.account.application.gateway.CustomerGateway;
import com.banksystem.account.infrastructure.mybatis.DepositReportMapper;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff term-deposit operations: funding summary (MyBatis read model), per-contract drill-down
 * and product-rate management. Rate changes never touch existing contracts — they carry their
 * own rate snapshots from open time.
 */
@Service
public class DepositAdminServiceImpl implements DepositAdminService {

  private static final Logger log = LoggerFactory.getLogger(DepositAdminService.class);

  private final DepositReportMapper mapper;
  private final TermDepositRepository depositRepository;
  private final DepositProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final AuditGateway auditGateway;
  private final CustomerGateway customerGateway;
  private final Clock clock;
  private final ZoneId zone;

  public DepositAdminServiceImpl(
      DepositReportMapper mapper,
      TermDepositRepository depositRepository,
      DepositProductRepository productRepository,
      AccountRepository accountRepository,
      AuditGateway auditGateway,
      CustomerGateway customerGateway,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone) {
    this.mapper = mapper;
    this.depositRepository = depositRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.auditGateway = auditGateway;
    this.customerGateway = customerGateway;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  @Transactional(readOnly = true)
  public DepositAdminSummaryResponse summary() {
    LocalDate today = LocalDate.now(clock.withZone(zone));
    return new DepositAdminSummaryResponse(mapper.totals(today), mapper.byProduct());
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminTermDepositRow> list(AdminDepositFilterRequest req) {
    return list(
        AdminDepositListQuery.of(
            req.page(),
            req.size(),
            req.status(),
            req.productCode(),
            req.userId(),
            req.accountId(),
            req.accountNumber(),
            req.maturityFrom(),
            req.maturityTo()));
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminTermDepositRow> list(AdminDepositListQuery query) {
    // STK is the human search key; resolve it here — never bound into SQL.
    boolean hasAccount = query.hasAccount() || query.hasAccountNumber();
    UUID accountId = query.accountIdOrNil();
    if (query.hasAccountNumber()) {
      Optional<AccountEntity> byNumber =
          accountRepository.findByAccountNumber(query.accountNumber());
      if (byNumber.isEmpty()) {
        return new PageResponse<>(List.of(), query.page(), query.size(), 0, 0);
      }
      accountId = byNumber.get().getId();
    }

    Page<TermDepositEntity> page =
        depositRepository.searchAdmin(
            query.hasStatus(),
            query.statusOrDefault(),
            query.hasProduct(),
            query.productCodeOrEmpty(),
            query.hasUser(),
            query.userIdOrNil(),
            hasAccount,
            accountId,
            query.maturityFrom(),
            query.maturityTo(),
            PageRequest.of(query.page(), query.size()));

    Map<UUID, String> accountNumbers = accountNumbersFor(page.getContent());
    Map<String, String> ownerNames = ownerNamesFor(page.getContent());
    Map<String, Integer> productTenors =
        productRepository.findAll().stream()
            .collect(
                Collectors.toMap(
                    DepositProductEntity::getCode,
                    DepositProductEntity::getTenorMonths,
                    (a, b) -> a));
    List<AdminTermDepositRow> items =
        page.getContent().stream()
            .map(d -> toRow(d, accountNumbers, ownerNames, productTenors))
            .toList();
    return new PageResponse<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  private Map<UUID, String> accountNumbersFor(List<TermDepositEntity> deposits) {
    List<UUID> ids =
        deposits.stream().map(TermDepositEntity::getSourceAccountId).distinct().toList();
    return accountRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getAccountNumber));
  }

  /** Best-effort: a customer-service outage degrades owner names to null, never fails the page. */
  private Map<String, String> ownerNamesFor(List<TermDepositEntity> deposits) {
    List<UUID> userIds = deposits.stream().map(TermDepositEntity::getUserId).distinct().toList();
    if (userIds.isEmpty() || customerGateway == null) {
      return Map.of();
    }
    return customerGateway.getCustomerNames(userIds);
  }

  @Transactional(readOnly = true)
  public List<DepositProductResponse> allProducts() {
    return productRepository.findAllByOrderByTenorMonthsAsc().stream()
        .map(this::toProductResponse)
        .toList();
  }

  @Transactional
  @CacheEvict(value = "depositProducts", allEntries = true)
  public DepositProductResponse updateProduct(
      String code, UpdateDepositProductRequest request, UUID actorUserId) {
    DepositProductEntity product =
        productRepository
            .findById(code)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "DEPOSIT_PRODUCT_NOT_FOUND", "Deposit product not found"));

    String before = snapshot(product);
    if (request.rateBps() != null) {
      product.setRateBps(request.rateBps());
    }
    if (request.earlyRateBps() != null) {
      product.setEarlyRateBps(request.earlyRateBps());
    }
    if (request.minAmount() != null) {
      product.setMinAmount(request.minAmount());
    }
    if (request.active() != null) {
      product.setActive(request.active());
    }
    productRepository.save(product);
    recordAudit(actorUserId, code, before + " -> " + snapshot(product));
    return toProductResponse(product);
  }

  /** Best-effort audit to transaction-service; a failed audit never fails the rate change. */
  private void recordAudit(UUID actorUserId, String code, String metadata) {
    if (auditGateway != null) {
      auditGateway.recordAuditLog(actorUserId, "DEPOSIT_PRODUCT_UPDATE", "DEPOSIT_PRODUCT", code, metadata);
    }
  }

  private static String snapshot(DepositProductEntity p) {
    return "rate=" + p.getRateBps() + "bps early=" + p.getEarlyRateBps()
        + "bps min=" + p.getMinAmount().toPlainString() + " active=" + p.isActive();
  }

  private AdminTermDepositRow toRow(
      TermDepositEntity d,
      Map<UUID, String> accountNumbers,
      Map<String, String> ownerNames,
      Map<String, Integer> productTenors) {
    int tenor = productTenors.getOrDefault(d.getProductCode(), 0);
    return new AdminTermDepositRow(
        d.getId().toString(),
        d.getUserId().toString(),
        ownerNames.get(d.getUserId().toString()),
        d.getSourceAccountId().toString(),
        accountNumbers.get(d.getSourceAccountId()),
        d.getProductCode(),
        tenor,
        d.getAmount(),
        d.getRateBps(),
        d.getAccruedInterest(),
        d.getOpenedAt(),
        d.getMaturityDate(),
        d.getStatus().name(),
        d.getClosedAt());
  }

  private DepositProductResponse toProductResponse(DepositProductEntity p) {
    return new DepositProductResponse(
        p.getCode(),
        p.getTenorMonths(),
        p.getRateBps(),
        p.getEarlyRateBps(),
        p.getMinAmount(),
        p.isActive());
  }
}
