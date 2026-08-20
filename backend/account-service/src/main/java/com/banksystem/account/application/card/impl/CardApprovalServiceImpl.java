package com.banksystem.account.application.card.impl;
import com.banksystem.account.api.dto.CardDtos.AdminCardFilterRequest;
import com.banksystem.account.api.dto.CardDtos.AdminCardRow;
import com.banksystem.account.api.dto.CardDtos.BatchApproveResult;
import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.application.card.CardApprovalService;
import com.banksystem.account.application.card.CardNumberGenerator;
import com.banksystem.account.application.mapper.CardMapper;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.card.CardEntity;
import com.banksystem.account.domain.card.CardRepository;
import com.banksystem.account.domain.card.CardStatus;
import com.banksystem.account.infrastructure.feign.AuditClient;
import com.banksystem.account.infrastructure.feign.CustomerClient;
import com.banksystem.account.infrastructure.feign.NotificationClient;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateNotificationRequest;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office card approval (checker side of the request flow). The PAN is generated only
 * here, at approval time — a REQUESTED or REJECTED card never had one. Both decisions are
 * audited and pushed to the customer's inbox (best-effort).
 */
@Service
public class CardApprovalServiceImpl implements CardApprovalService {

  private static final Logger log = LoggerFactory.getLogger(CardApprovalService.class);

  private final CardRepository cardRepository;
  private final AccountRepository accountRepository;
  private final CustomerClient customerClient;
  private final AuditClient auditClient;
  private final NotificationClient notificationClient;
  private final CardMapper cardMapper;
  private final String customerApiKey;
  private final String transactionApiKey;
  private final String notificationApiKey;
  private final Clock clock;
  private final ZoneId zone;
  private final String aesKey;
  private final int validityYears;

  public CardApprovalServiceImpl(
      CardRepository cardRepository,
      AccountRepository accountRepository,
      CustomerClient customerClient,
      AuditClient auditClient,
      NotificationClient notificationClient,
      CardMapper cardMapper,
      @Value("${bank.internal.customer-api-key}") String customerApiKey,
      @Value("${bank.internal.transaction-api-key}") String transactionApiKey,
      @Value("${bank.internal.notification-api-key}") String notificationApiKey,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone,
      @Value("${bank.aes.secret-key}") String aesKey,
      @Value("${bank.card.validity-years}") int validityYears) {
    this.cardRepository = cardRepository;
    this.accountRepository = accountRepository;
    this.customerClient = customerClient;
    this.auditClient = auditClient;
    this.notificationClient = notificationClient;
    this.cardMapper = cardMapper;
    this.customerApiKey = customerApiKey;
    this.transactionApiKey = transactionApiKey;
    this.notificationApiKey = notificationApiKey;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
    this.aesKey = aesKey;
    this.validityYears = validityYears;
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminCardRow> queue(AdminCardFilterRequest req) {
    return queue(req.status(), req.page(), req.size(), req.q());
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminCardRow> queue(String status, Integer page, Integer size, String q) {
    CardStatus parsed = parseStatus(status == null || status.isBlank() ? "REQUESTED" : status);
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? 20 : Math.min(size, 100);
    Page<CardEntity> result =
        cardRepository.findByStatusOrderByCreatedAtAsc(parsed, PageRequest.of(p, s));

    Map<UUID, String> numbers =
        accountRepository
            .findAllById(result.getContent().stream().map(CardEntity::getAccountId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getAccountNumber));
    Map<String, String> names = ownerNames(result.getContent());

    List<AdminCardRow> rows =
        result.getContent().stream()
            .map(
                c ->
                    new AdminCardRow(
                        c.getId().toString(),
                        c.getUserId().toString(),
                        names.get(c.getUserId().toString()),
                        c.getAccountId().toString(),
                        numbers.get(c.getAccountId()),
                        c.getStatus().name(),
                        c.getDailyLimit(),
                        c.getRejectReason(),
                        c.getCreatedAt()))
            .filter(row -> {
              if (q == null || q.isBlank()) {
                return true;
              }
              String term = q.trim().toLowerCase();
              return (row.ownerName() != null && row.ownerName().toLowerCase().contains(term))
                  || (row.accountNumber() != null && row.accountNumber().toLowerCase().contains(term))
                  || (row.userId() != null && row.userId().toLowerCase().contains(term))
                  || (row.id() != null && row.id().toLowerCase().contains(term));
            })
            .toList();
    return new PageResponse<>(
        rows, result.getNumber(), result.getSize(), result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminCardRow> queue(String status, Integer page, Integer size) {
    return queue(status, page, size, null);
  }

  /** Checker batch approves requested cards. */
  @Transactional
  public BatchApproveResult batchApprove(List<UUID> cardIds, UUID staffId) {
    if (cardIds == null || cardIds.isEmpty()) {
      return new BatchApproveResult(0, 0, List.of());
    }
    int approved = 0;
    int failed = 0;
    List<String> errors = new ArrayList<>();
    for (UUID id : cardIds) {
      try {
        approve(id, staffId);
        approved++;
      } catch (Exception ex) {
        failed++;
        errors.add("Card " + id + ": " + ex.getMessage());
      }
    }
    return new BatchApproveResult(approved, failed, errors);
  }

  /** Checker approves: the PAN comes into existence here, never earlier. */
  @Transactional
  public CardResponse approve(UUID cardId, UUID staffId) {
    CardEntity card = requireRequested(cardId);
    String pan = CardNumberGenerator.generate();
    Instant now = Instant.now();
    card.setPanEncrypted(CryptoUtils.encrypt(pan, aesKey));
    card.setPanLast4(pan.substring(pan.length() - 4));
    card.setExpiresOn(LocalDate.now(clock.withZone(zone)).plusYears(validityYears));
    card.setStatus(CardStatus.PENDING_ACTIVATION);
    card.setApprovedBy(staffId);
    card.setApprovedAt(now);
    card.setUpdatedAt(now);
    cardRepository.save(card);

    audit(staffId, "CARD_APPROVE", card, "last4=" + card.getPanLast4());
    notify(
        card,
        "CARD_APPROVED",
        "The the ao duoi so " + card.getPanLast4()
            + " da duoc duyet. Vao muc The de kich hoat va su dung.");
    log.info("[CARD-APPROVE] Card [{}] approved by staff [{}], panLast4=[{}]",
        card.getId(), staffId, card.getPanLast4());
    return toResponse(card);
  }

  /** Checker rejects with a mandatory reason; the account may request again afterwards. */
  @Transactional
  public CardResponse reject(UUID cardId, String reason, UUID staffId) {
    CardEntity card = requireRequested(cardId);
    Instant now = Instant.now();
    card.setStatus(CardStatus.REJECTED);
    card.setRejectReason(reason);
    card.setRejectedBy(staffId);
    card.setRejectedAt(now);
    card.setUpdatedAt(now);
    cardRepository.save(card);

    audit(staffId, "CARD_REJECT", card, "reason=" + reason);
    notify(card, "CARD_REJECTED", "Yeu cau mo the bi tu choi: " + reason);
    log.warn("[CARD-REJECT] Card [{}] rejected by staff [{}]: Reason=[{}]",
        card.getId(), staffId, reason);
    return toResponse(card);
  }

  private CardEntity requireRequested(UUID cardId) {
    CardEntity card =
        cardRepository
            .findById(cardId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "CARD_NOT_FOUND", "Card not found"));
    if (card.getStatus() != CardStatus.REQUESTED) {
      throw new BusinessException(
          "CARD_NOT_REQUESTED",
          "Only REQUESTED cards can be decided (current: " + card.getStatus() + ")");
    }
    return card;
  }

  /** Best-effort: a failed audit push never fails the decision. */
  private void audit(UUID staffId, String action, CardEntity card, String metadata) {
    try {
      auditClient.createAuditLog(
          new AuditClient.CreateAuditLogRequest(
              staffId, action, "CARD", card.getId().toString(), "127.0.0.1", metadata),
          transactionApiKey);
    } catch (Exception ex) {
      log.warn("Audit push failed for card {}: {}", card.getId(), ex.getMessage());
    }
  }

  /** Best-effort: a failed inbox push never fails the decision. */
  private void notify(CardEntity card, String template, String body) {
    try {
      notificationClient.createNotification(
          new CreateNotificationRequest(
              "INAPP",
              card.getUserId().toString(),
              template,
              "SENT",
              body,
              card.getUserId(),
              "CUSTOMER",
              "CARD",
              card.getId().toString(),
              "/customer/cards"),
          notificationApiKey);
    } catch (Exception ex) {
      log.warn("Inbox push failed for card {}: {}", card.getId(), ex.getMessage());
    }
  }

  private Map<String, String> ownerNames(List<CardEntity> cards) {
    List<UUID> userIds = cards.stream().map(CardEntity::getUserId).distinct().toList();
    if (userIds.isEmpty()) {
      return Map.of();
    }
    try {
      ApiResponse<List<CustomerClient.CustomerNameView>> res =
          customerClient.names(new CustomerClient.CustomerNamesRequest(userIds), customerApiKey);
      if (res == null || !res.success() || res.data() == null) {
        return Map.of();
      }
      return res.data().stream()
          .collect(
              Collectors.toMap(
                  CustomerClient.CustomerNameView::userId,
                  CustomerClient.CustomerNameView::fullName,
                  (a, b) -> a));
    } catch (Exception ex) {
      log.warn("Owner-name enrichment failed: {}", ex.getMessage());
      return Map.of();
    }
  }

  private CardStatus parseStatus(String raw) {
    try {
      return CardStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "CARD_STATUS_INVALID", "Unknown card status: " + raw);
    }
  }

  private CardResponse toResponse(CardEntity c) {
    String accountNumber = accountRepository
        .findById(c.getAccountId())
        .map(AccountEntity::getAccountNumber)
        .orElse(null);
    return cardMapper.toResponse(c, accountNumber);
  }
}
