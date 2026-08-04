package com.banksystem.account.application.card.impl;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.api.dto.CardDtos.CardRevealResponse;
import com.banksystem.account.api.dto.CardDtos.UpdateCardLimitRequest;
import com.banksystem.account.application.mapper.CardMapper;
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
public class CardServiceImpl implements CardService {

  private final CardRepository cardRepository;
  private final AccountAccessService access;
  private final AccountRepository accountRepository;
  private final CardMapper cardMapper;
  private final Clock clock;
  private final ZoneId zone;
  private final String aesKey;
  private final BigDecimal defaultDailyLimit;
  private final BigDecimal maxDailyLimit;

  public CardServiceImpl(
      CardRepository cardRepository,
      AccountAccessService access,
      AccountRepository accountRepository,
      CardMapper cardMapper,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone,
      @Value("${bank.aes.secret-key}") String aesKey,
      @Value("${bank.card.default-daily-limit:20000000}") BigDecimal defaultDailyLimit,
      @Value("${bank.card.max-daily-limit:100000000}") BigDecimal maxDailyLimit) {
    this.cardRepository = cardRepository;
    this.access = access;
    this.accountRepository = accountRepository;
    this.cardMapper = cardMapper;
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
          "ACCOUNT_NOT_ACTIVE", "Account must be active to request a card");
    }
    if (cardRepository.existsByAccountIdAndStatusNotIn(
        accountId, List.of(CardStatus.CLOSED, CardStatus.REJECTED))) {
      throw new BusinessException(
          "CARD_ALREADY_EXISTS", "Account already has a card or a pending request");
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
          "Limits can only change on an issued card");
    }
    if (request.dailyLimit().compareTo(maxDailyLimit) > 0) {
      throw new BusinessException(
          "CARD_LIMIT_TOO_HIGH",
          "Daily limit must not exceed " + maxDailyLimit.toPlainString());
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
          "CARD_NOT_ACTIVE", "Card must be active to reveal");
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
        "Cannot move card from " + from + " to " + to);
  }

  private CardEntity requireOwnedCard(UUID cardId, GatewayUser user) {
    CardEntity card =
        cardRepository
            .findById(cardId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "CARD_NOT_FOUND", "Card not found"));
    access.requireOwnedOrStaff(card.getAccountId(), user);
    return card;
  }

  private CardResponse toResponse(CardEntity c, String accountNumber) {
    return cardMapper.toResponse(c, accountNumber);
  }
}
