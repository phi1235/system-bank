package com.banksystem.transaction.application.sepay;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SepayDtos.CreateTopUpRequest;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookResponse;
import com.banksystem.transaction.api.dto.SepayDtos.TopUpOrderResponse;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.config.SepayProperties;
import com.banksystem.transaction.domain.sepay.SepayOrderStatus;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrder;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrderRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.sepay.SepayWebhookVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SepayService {

  private static final Logger log = LoggerFactory.getLogger(SepayService.class);
  private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(?i)\\b(SB\\d{6,10})\\b");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SepayPaymentOrderRepository orderRepository;
  private final SepayWebhookLogRepository webhookLogRepository;
  private final SepayWebhookInboxService inboxService;
  private final SepayOrderExecutionService executionService;
  private final AccountGateway accountGateway;
  private final SepayWebhookVerifier verifier;
  private final SepayProperties properties;
  private final ObjectMapper objectMapper;

  public SepayService(
      SepayPaymentOrderRepository orderRepository,
      SepayWebhookLogRepository webhookLogRepository,
      SepayWebhookInboxService inboxService,
      SepayOrderExecutionService executionService,
      AccountGateway accountGateway,
      SepayWebhookVerifier verifier,
      SepayProperties properties,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.webhookLogRepository = webhookLogRepository;
    this.inboxService = inboxService;
    this.executionService = executionService;
    this.accountGateway = accountGateway;
    this.verifier = verifier;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public SepayWebhookResponse processWebhook(
      String authHeader, SepayWebhookPayload payload) {
    try {
      return processWebhook(authHeader, payload, objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new BusinessException(
          "INVALID_WEBHOOK_PAYLOAD", "Unable to preserve SePay webhook payload",
          HttpStatus.BAD_REQUEST);
    }
  }

  @Transactional
  public TopUpOrderResponse createTopUpOrder(UUID userId, CreateTopUpRequest request) {
    AccountView account = accountGateway.getAccountByNumber(request.accountNumber());
    if (account == null) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found: " + request.accountNumber(), HttpStatus.NOT_FOUND);
    }

    if (!account.userIdUuid().equals(userId)) {
      throw new BusinessException("FORBIDDEN", "Account does not belong to the authenticated customer", HttpStatus.FORBIDDEN);
    }

    if (!"ACTIVE".equalsIgnoreCase(account.status())) {
      throw new BusinessException("ACCOUNT_NOT_ACTIVE", "Account is not active", HttpStatus.BAD_REQUEST);
    }

    String orderCode = generateOrderCode();
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.getOrderExpiryMinutes(), ChronoUnit.MINUTES);

    String vietQrUrl = buildVietQrUrl(orderCode, request.amount());

    SepayPaymentOrder order = new SepayPaymentOrder(
        UUID.randomUUID(),
        orderCode,
        userId,
        request.accountNumber(),
        request.amount(),
        SepayOrderStatus.PENDING,
        vietQrUrl,
        properties.getBankName(),
        orderCode,
        now,
        expiresAt);

    orderRepository.save(order);
    log.info("Created SePay top-up order: code={}, user={}, amount={}", orderCode, userId, request.amount());

    return toResponse(order);
  }

  public SepayWebhookResponse processWebhook(String authHeader, SepayWebhookPayload payload, String rawPayload) {
    verifier.verify(authHeader);

    Long sepayTxId = payload.id();

    // TX #1: Durably commit webhook log to PostgreSQL in isolated TX before any remote side effects
    SepayWebhookInboxService.InboxPersistResult inboxResult = inboxService.persistOrGet(payload, rawPayload);
    if (!inboxResult.created()) {
      log.info("Duplicate SePay webhook transaction ID {} short-circuited gracefully", sepayTxId);
      return new SepayWebhookResponse(true, "Duplicate transaction skipped");
    }

    SepayWebhookLog webhookLog = inboxResult.log();

    if (!"in".equalsIgnoreCase(payload.transferType())) {
      log.info("Ignored non-incoming SePay transfer type: {}", payload.transferType());
      inboxService.updateStatusIndependent(webhookLog.getId(), SepayWebhookProcessingStatus.IGNORED, "Ignored transfer type: " + payload.transferType());
      return new SepayWebhookResponse(true, "Outbound transfer ignored");
    }

    String orderCode = extractOrderCode(payload);
    if (orderCode == null || orderCode.isBlank()) {
      log.warn("No order code found in SePay payload: content='{}', code='{}'", payload.content(), payload.code());
      inboxService.updateStatusIndependent(webhookLog.getId(), SepayWebhookProcessingStatus.IGNORED, "No recognizable order code");
      return new SepayWebhookResponse(true, "No order code found");
    }

    Optional<SepayPaymentOrder> orderOpt = orderRepository.findByOrderCode(orderCode);
    if (orderOpt.isEmpty()) {
      log.warn("No matching SePay order for code: {}", orderCode);
      inboxService.updateStatusIndependent(webhookLog.getId(), SepayWebhookProcessingStatus.IGNORED, "Order not found: " + orderCode);
      return new SepayWebhookResponse(true, "Order not found");
    }

    SepayPaymentOrder order = orderOpt.get();
    if (order.getStatus() == SepayOrderStatus.SUCCESS) {
      log.info("Order {} already marked as SUCCESS", orderCode);
      inboxService.updateStatusIndependent(webhookLog.getId(), SepayWebhookProcessingStatus.PROCESSED, "Order already SUCCESS");
      return new SepayWebhookResponse(true, "Order already completed");
    }

    BigDecimal transferAmount = payload.transferAmount() != null ? payload.transferAmount() : BigDecimal.ZERO;
    if (transferAmount.compareTo(order.getAmount()) == 0) {
      AccountView account = accountGateway.getAccountByNumber(order.getAccountNumber());
      if (account == null) {
        throw new BusinessException("TARGET_ACCOUNT_NOT_FOUND", "Target account not found for order " + orderCode, HttpStatus.INTERNAL_SERVER_ERROR);
      }

      inboxService.updateStatusIndependent(webhookLog.getId(), SepayWebhookProcessingStatus.PROCESSING, null);

      String idempotencyKey = "SEPAY-TX-" + sepayTxId + "-" + order.getOrderCode();
      MoneyCommand creditCommand = new MoneyCommand(
          order.getAmount(),
          idempotencyKey,
          "Nạp tiền VietQR SePay " + order.getOrderCode(),
          idempotencyKey);

      // Execute remote side-effect after durable log write has already committed
      accountGateway.credit(account.idUuid(), creditCommand);

      // TX #2: Executed atomically via dedicated Spring bean proxy (order SUCCESS + webhook PROCESSED)
      executionService.completeOrderAndLog(order, sepayTxId, webhookLog.getId());
      log.info("Successfully credited {} to account {} for SePay order {}", order.getAmount(), order.getAccountNumber(), orderCode);
    } else {
      // TX #2 (Manual review branch): Executed atomically via dedicated Spring bean proxy
      executionService.markManualReview(order, sepayTxId, webhookLog.getId(), transferAmount);
      log.warn("SePay order {} amount mismatch: expected={}, received={}", orderCode, order.getAmount(), transferAmount);
    }

    return new SepayWebhookResponse(true, "Webhook processed successfully");
  }

  @Transactional(readOnly = true)
  public TopUpOrderResponse getOrderByCode(String orderCode) {
    SepayPaymentOrder order = orderRepository.findByOrderCode(orderCode)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found: " + orderCode, HttpStatus.NOT_FOUND));
    return toResponse(order);
  }

  @Transactional(readOnly = true)
  public List<TopUpOrderResponse> getMyOrders(UUID userId) {
    return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private String extractOrderCode(SepayWebhookPayload payload) {
    if (payload.code() != null && !payload.code().isBlank()) {
      return payload.code().trim().toUpperCase();
    }
    if (payload.content() != null && !payload.content().isBlank()) {
      Matcher matcher = ORDER_CODE_PATTERN.matcher(payload.content());
      if (matcher.find()) {
        return matcher.group(1).toUpperCase();
      }
    }
    return null;
  }

  private String generateOrderCode() {
    long timestampPart = Instant.now().toEpochMilli() % 1000000L;
    int randomPart = 10 + RANDOM.nextInt(90);
    return "SB" + timestampPart + randomPart;
  }

  private String buildVietQrUrl(String orderCode, BigDecimal amount) {
    String formattedAmount = amount.stripTrailingZeros().toPlainString();
    return "https://qr.sepay.vn/img?acc="
        + properties.getAccountNumber()
        + "&bank="
        + properties.getBankName()
        + "&amount="
        + formattedAmount
        + "&des="
        + orderCode
        + "&template="
        + properties.getQrTemplate();
  }

  private TopUpOrderResponse toResponse(SepayPaymentOrder order) {
    return new TopUpOrderResponse(
        order.getId(),
        order.getOrderCode(),
        order.getAccountNumber(),
        order.getAmount(),
        order.getStatus().name(),
        order.getVietQrUrl(),
        properties.getBankName(),
        properties.getAccountNumber(),
        properties.getAccountName(),
        order.getTransferContent(),
        order.getCreatedAt(),
        order.getExpiresAt(),
        order.getCompletedAt());
  }
}
