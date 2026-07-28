package com.banksystem.account.application.card;

import com.banksystem.account.api.dto.card.CardDtos.CardResponse;
import com.banksystem.account.api.dto.card.CardDtos.CardRevealResponse;
import com.banksystem.account.api.dto.card.CardDtos.UpdateCardLimitRequest;
import com.banksystem.account.application.account.AccountAccessService;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.entity.card.CardEntity;
import com.banksystem.account.domain.enums.account.AccountStatus;
import com.banksystem.account.domain.enums.card.CardStatus;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.account.domain.repository.card.CardRepository;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Virtual debit cards: issue, lifecycle (PENDING_ACTIVATION → ACTIVE ⇄ LOCKED → CLOSED),
 * daily-limit and owner-only PAN reveal. PAN is AES-GCM encrypted at rest; list/detail
 * responses only ever carry the masked form.
 */
@Service
public class CardService {

  private final CardRepository cardRepository;
  private final AccountAccessService access;
  private final AccountRepository accountRepository;
  private final com.banksystem.account.application.common.OpsAlertPublisher opsAlertPublisher;
  private final Clock clock;
  private final ZoneId zone;
  private final String aesKey;
  private final BigDecimal defaultDailyLimit;
  private final BigDecimal maxDailyLimit;

  public CardService(
      CardRepository cardRepository,
      AccountAccessService access,
      AccountRepository accountRepository,
      com.banksystem.account.application.common.OpsAlertPublisher opsAlertPublisher,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone,
      @Value("${bank.aes.secret-key}") String aesKey,
      @Value("${bank.card.default-daily-limit:20000000}") BigDecimal defaultDailyLimit,
      @Value("${bank.card.max-daily-limit:100000000}") BigDecimal maxDailyLimit) {
    this.cardRepository = cardRepository;
    this.access = access;
    this.accountRepository = accountRepository;
    this.opsAlertPublisher = opsAlertPublisher;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
    this.aesKey = aesKey;
    this.defaultDailyLimit = defaultDailyLimit;
    this.maxDailyLimit = maxDailyLimit;
  }

  /**
   * Customer card request: enters the back-office approval queue as REQUESTED. No PAN exists
   * until a staff member approves (see {@code CardApprovalService}).
   */
  @Transactional
  public CardResponse request(UUID accountId, GatewayUser user) {
    AccountEntity account = access.requireOwnedOrStaff(accountId, user);
    if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
      throw new BusinessException(
          "ACCOUNT_NOT_ACTIVE", "Account must be active to request a card",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (cardRepository.existsByAccountIdAndStatusNotIn(
        accountId, List.of(CardStatus.CLOSED, CardStatus.REJECTED))) {
      throw new BusinessException(
          "CARD_ALREADY_EXISTS", "Account already has a card or a pending request",
          HttpStatus.CONFLICT);
    }

    Instant now = Instant.now(clock);
    CardEntity card = new CardEntity();
    card.setId(UUID.randomUUID());
    card.setAccountId(accountId);
    card.setUserId(account.getUserId());
    card.setStatus(CardStatus.REQUESTED);
    card.setDailyLimit(defaultDailyLimit);
    card.setCreatedAt(now);
    card.setUpdatedAt(now);
    cardRepository.save(card);
    if (opsAlertPublisher != null) {
      opsAlertPublisher.cardRequested(card, account.getAccountNumber());
    }
    return toResponse(card, account.getAccountNumber());
  }

  @Transactional
  public CardResponse activate(UUID cardId, GatewayUser user) {
    return transition(cardId, user, CardStatus.PENDING_ACTIVATION, CardStatus.ACTIVE);
  }

  @Transactional
  public CardResponse lock(UUID cardId, GatewayUser user) {
    return transition(cardId, user, CardStatus.ACTIVE, CardStatus.LOCKED);
  }

  @Transactional
  public CardResponse unlock(UUID cardId, GatewayUser user) {
    return transition(cardId, user, CardStatus.LOCKED, CardStatus.ACTIVE);
  }

  /** Also serves as request cancellation: a REQUESTED card can be closed by its owner. */
  @Transactional
  public CardResponse close(UUID cardId, GatewayUser user) {
    CardEntity card = requireOwnedCard(cardId, user);
    if (card.getStatus() == CardStatus.CLOSED || card.getStatus() == CardStatus.REJECTED) {
      throw invalidTransition(card.getStatus(), CardStatus.CLOSED);
    }
    return saveStatus(card, CardStatus.CLOSED);
  }

  @Transactional
  public CardResponse updateLimit(UUID cardId, UpdateCardLimitRequest request, GatewayUser user) {
    CardEntity card = requireOwnedCard(cardId, user);
    if (card.getStatus() != CardStatus.PENDING_ACTIVATION
        && card.getStatus() != CardStatus.ACTIVE
        && card.getStatus() != CardStatus.LOCKED) {
      throw new BusinessException(
          "CARD_NOT_EDITABLE",
          "Limits can only change on an issued card",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    if (request.dailyLimit().compareTo(maxDailyLimit) > 0) {
      throw new BusinessException(
          "CARD_LIMIT_TOO_HIGH",
          "Daily limit must not exceed " + maxDailyLimit.toPlainString(),
          HttpStatus.BAD_REQUEST);
    }
    card.setDailyLimit(request.dailyLimit());
    card.setUpdatedAt(Instant.now(clock));
    cardRepository.save(card);
    return toResponse(card, accountNumberOf(card.getAccountId()));
  }

  /** Full PAN, owner only, ACTIVE card only — a virtual card is unusable without its number. */
  @Transactional(readOnly = true)
  public CardRevealResponse reveal(UUID cardId, GatewayUser user) {
    CardEntity card = requireOwnedCard(cardId, user);
    if (card.getStatus() != CardStatus.ACTIVE) {
      throw new BusinessException(
          "CARD_NOT_ACTIVE", "Card must be active to reveal", HttpStatus.UNPROCESSABLE_ENTITY);
    }
    return new CardRevealResponse(
        card.getId().toString(),
        CryptoUtils.decrypt(card.getPanEncrypted(), aesKey),
        card.getExpiresOn());
  }

  @Transactional(readOnly = true)
  public List<CardResponse> listMine(UUID userId) {
    List<CardEntity> cards = cardRepository.findByUserIdOrderByCreatedAtDesc(userId);
    Map<UUID, String> numbers =
        accountRepository
            .findAllById(cards.stream().map(CardEntity::getAccountId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(AccountEntity::getId, AccountEntity::getAccountNumber));
    return cards.stream().map(c -> toResponse(c, numbers.get(c.getAccountId()))).toList();
  }

  private CardResponse transition(
      UUID cardId, GatewayUser user, CardStatus expected, CardStatus target) {
    CardEntity card = requireOwnedCard(cardId, user);
    if (card.getStatus() != expected) {
      throw invalidTransition(card.getStatus(), target);
    }
    return saveStatus(card, target);
  }

  private CardResponse saveStatus(CardEntity card, CardStatus target) {
    card.setStatus(target);
    card.setUpdatedAt(Instant.now(clock));
    cardRepository.save(card);
    return toResponse(card, accountNumberOf(card.getAccountId()));
  }

  private String accountNumberOf(UUID accountId) {
    return accountRepository
        .findById(accountId)
        .map(AccountEntity::getAccountNumber)
        .orElse(null);
  }

  private BusinessException invalidTransition(CardStatus from, CardStatus to) {
    return new BusinessException(
        "CARD_INVALID_TRANSITION",
        "Cannot move card from " + from + " to " + to,
        HttpStatus.UNPROCESSABLE_ENTITY);
  }

  private CardEntity requireOwnedCard(UUID cardId, GatewayUser user) {
    CardEntity card =
        cardRepository
            .findById(cardId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "CARD_NOT_FOUND", "Card not found", HttpStatus.NOT_FOUND));
    access.requireOwnedOrStaff(card.getAccountId(), user);
    return card;
  }

  private CardResponse toResponse(CardEntity c, String accountNumber) {
    return new CardResponse(
        c.getId().toString(),
        c.getAccountId().toString(),
        accountNumber,
        c.getPanLast4() == null ? null : "9704 **** **** " + c.getPanLast4(),
        c.getBrand(),
        c.getStatus().name(),
        c.getDailyLimit(),
        c.getExpiresOn(),
        c.getRejectReason(),
        c.getCreatedAt());
  }
}
