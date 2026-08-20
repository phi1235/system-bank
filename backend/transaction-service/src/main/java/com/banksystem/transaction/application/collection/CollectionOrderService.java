package com.banksystem.transaction.application.collection;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.CollectionDtos.CollectionOrderResponse;
import com.banksystem.transaction.api.dto.CollectionDtos.CreateCollectionOrderRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.BusinessDashboardSummaryResponse;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.ProvisionVirtualAccountRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountResponse;
import com.banksystem.transaction.application.settlement.SplitRuleService;
import com.banksystem.transaction.application.virtualaccount.VirtualAccountService;
import com.banksystem.transaction.domain.collection.CollectionOrderEntity;
import com.banksystem.transaction.domain.collection.CollectionOrderRepository;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import com.banksystem.transaction.domain.settlement.SettlementEntity;
import com.banksystem.transaction.domain.settlement.SettlementRepository;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountEntity;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountMode;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionOrderService {

  private static final Logger log = LoggerFactory.getLogger(CollectionOrderService.class);

  private final CollectionOrderRepository collectionOrderRepository;
  private final VirtualAccountRepository virtualAccountRepository;
  private final VirtualAccountService virtualAccountService;
  private final SplitRuleService splitRuleService;
  private final SettlementRepository settlementRepository;
  private final InboundPaymentEventRepository inboundPaymentEventRepository;

  @Value("${bank.va.default-bank-bin}")
  private String defaultBankBin;

  @Value("${bank.va.default-provider:MOCK}")
  private String defaultProvider;

  public CollectionOrderService(
      CollectionOrderRepository collectionOrderRepository,
      VirtualAccountRepository virtualAccountRepository,
      VirtualAccountService virtualAccountService,
      SplitRuleService splitRuleService,
      SettlementRepository settlementRepository,
      InboundPaymentEventRepository inboundPaymentEventRepository) {
    this.collectionOrderRepository = collectionOrderRepository;
    this.virtualAccountRepository = virtualAccountRepository;
    this.virtualAccountService = virtualAccountService;
    this.splitRuleService = splitRuleService;
    this.settlementRepository = settlementRepository;
    this.inboundPaymentEventRepository = inboundPaymentEventRepository;
  }

  @Transactional
  public CollectionOrderResponse createCollectionOrder(
      UUID organizationId, CreateCollectionOrderRequest request, String idempotencyKey) {

    Optional<CollectionOrderEntity> existing =
        collectionOrderRepository.findByOrganizationIdAndMerchantOrderId(organizationId, request.merchantOrderId());

    if (existing.isPresent()) {
      CollectionOrderEntity order = existing.get();
      if (order.getExpectedAmount().compareTo(request.expectedAmount()) == 0
          && order.getCurrency().equalsIgnoreCase(request.currency() != null ? request.currency() : "VND")) {
        log.info("[COLLECTION-ORDER] Idempotent return for merchantOrderId={}", request.merchantOrderId());
        return toResponse(order);
      } else {
        throw new BusinessException("IDEMPOTENCY_CONFLICT", "Collection order exists with different payload or amount");
      }
    }

    UUID vaId = request.virtualAccountId();
    if (vaId == null) {
      VirtualAccountMode mode = request.vaMode() != null ? request.vaMode() : VirtualAccountMode.SINGLE_USE;
      VirtualAccountResponse vaResp = virtualAccountService.provision(
          organizationId,
          new ProvisionVirtualAccountRequest(
              defaultProvider, defaultBankBin, null, mode, request.customerReference(), request.expiresAt()
          )
      );
      vaId = vaResp.id();
    } else {
      VirtualAccountEntity va = virtualAccountRepository.findById(vaId).orElseThrow(() ->
          new BusinessException("VA_NOT_FOUND", "Virtual account not found"));
      if (!va.getOrganizationId().equals(organizationId)) {
        throw new BusinessException("FORBIDDEN", "Unauthorized virtual account");
      }
    }

    String splitSnapshot = splitRuleService.serializeSnapshot(request.splitRuleId(), request.splitLegs());
    Instant now = Instant.now();

    CollectionOrderEntity order = CollectionOrderEntity.create(
        organizationId,
        request.merchantOrderId().trim(),
        vaId,
        request.expectedAmount(),
        request.currency(),
        request.customerReference(),
        splitSnapshot,
        request.expiresAt(),
        now
    );

    collectionOrderRepository.save(order);
    log.info("[COLLECTION-ORDER] Created order id={}, merchantOrderId={}, amount={}, org={}",
        order.getId(), order.getMerchantOrderId(), order.getExpectedAmount(), organizationId);

    return toResponse(order);
  }

  @Transactional(readOnly = true)
  public CollectionOrderResponse getByMerchantOrderId(UUID organizationId, String merchantOrderId) {
    CollectionOrderEntity order = collectionOrderRepository.findByOrganizationIdAndMerchantOrderId(organizationId, merchantOrderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Collection order not found: " + merchantOrderId));
    return toResponse(order);
  }

  @Transactional(readOnly = true)
  public CollectionOrderResponse getById(UUID organizationId, UUID orderId) {
    CollectionOrderEntity order = collectionOrderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Collection order not found"));
    if (organizationId != null && !order.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to order");
    }
    return toResponse(order);
  }

  @Transactional
  public void cancelOrder(UUID organizationId, UUID orderId) {
    CollectionOrderEntity order = collectionOrderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Collection order not found"));
    if (!order.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to order");
    }
    if (order.getStatus() != CollectionOrderStatus.PENDING) {
      throw new BusinessException("CANNOT_CANCEL_ORDER", "Only PENDING collection orders can be cancelled");
    }
    order.setStatus(CollectionOrderStatus.CANCELLED);
    order.setUpdatedAt(Instant.now());
    collectionOrderRepository.save(order);
    log.info("[COLLECTION-ORDER] Cancelled order id={}", orderId);
  }

  @Transactional(readOnly = true)
  public Page<CollectionOrderResponse> search(CollectionOrderSearchQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    return collectionOrderRepository.search(query.organizationId(), query.q(), query.status(), pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<CollectionOrderResponse> search(UUID organizationId, String q, CollectionOrderStatus status, Pageable pageable) {
    return collectionOrderRepository.search(organizationId, q, status, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public BusinessDashboardSummaryResponse getDashboardSummary(UUID organizationId) {
    long totalVAs = virtualAccountRepository.count();
    long activeVAs = virtualAccountRepository.search(organizationId, null, null, Pageable.unpaged()).getTotalElements();
    long pendingOrders = collectionOrderRepository.countByOrganizationIdAndStatus(organizationId, CollectionOrderStatus.PENDING);
    long paidOrders = collectionOrderRepository.countByOrganizationIdAndStatus(organizationId, CollectionOrderStatus.PAID);
    long reviewOrders = collectionOrderRepository.countByOrganizationIdAndStatus(organizationId, CollectionOrderStatus.REVIEW);
    long pendingSettlements = settlementRepository.countByOrganizationIdAndStatus(organizationId, SettlementStatus.LEDGER_PENDING);

    List<CollectionOrderEntity> paidList = collectionOrderRepository.search(organizationId, null, CollectionOrderStatus.PAID, Pageable.unpaged()).getContent();
    BigDecimal totalCollected = paidList.stream().map(CollectionOrderEntity::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    List<SettlementEntity> settledList = settlementRepository.search(organizationId, SettlementStatus.COMPLETED, Pageable.unpaged()).getContent();
    BigDecimal totalSettled = settledList.stream().map(SettlementEntity::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    long totalInbound = inboundPaymentEventRepository.count();
    long processedInbound = inboundPaymentEventRepository.search(null, null, InboundPaymentStatus.PROCESSED, Pageable.unpaged()).getTotalElements();
    double autoMatchRate = totalInbound > 0 ? ((double) processedInbound / totalInbound) * 100.0 : 100.0;

    return new BusinessDashboardSummaryResponse(
        totalVAs, activeVAs, pendingOrders, paidOrders, reviewOrders,
        totalCollected, totalSettled, pendingSettlements, autoMatchRate
    );
  }

  public CollectionOrderResponse toResponse(CollectionOrderEntity order) {
    VirtualAccountEntity va = virtualAccountRepository.findById(order.getVirtualAccountId()).orElse(null);
    String vaNumber = va != null ? va.getAccountNumber() : "N/A";
    String bankBin = va != null ? va.getBankBin() : defaultBankBin;
    String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s",
        bankBin, vaNumber, order.getExpectedAmount().toPlainString(), order.getMerchantOrderId());

    return new CollectionOrderResponse(
        order.getId(),
        order.getOrganizationId(),
        order.getMerchantOrderId(),
        order.getVirtualAccountId(),
        vaNumber,
        bankBin,
        qrUrl,
        order.getExpectedAmount(),
        order.getPaidAmount(),
        order.getCurrency(),
        order.getStatus(),
        order.getCustomerReference(),
        order.getSplitRuleSnapshot(),
        order.getExpiresAt(),
        order.getPaidAt(),
        order.getCreatedAt()
    );
  }
}
