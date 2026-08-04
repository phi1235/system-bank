package com.banksystem.account.application;

import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositQuoteResponse;
import com.banksystem.account.api.dto.DepositDtos.OpenDepositRequest;
import com.banksystem.account.api.dto.DepositDtos.TermDepositResponse;
import com.banksystem.account.application.mapper.TermDepositMapper;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.DepositProductEntity;
import com.banksystem.account.domain.DepositProductRepository;
import com.banksystem.account.domain.TermDepositEntity;
import com.banksystem.account.domain.TermDepositRepository;
import com.banksystem.account.domain.TermDepositStatus;
import com.banksystem.common.exception.BusinessException;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Term deposits (so tiet kiem): open, early close, quote. Money moves only through ledger
 * postings on the source account ({@code DEP-{id}} debit, {@code DEP-{id}-close} credit) —
 * same service, same DB, so a local transaction is sufficient (no saga).
 */
@Service
public class TermDepositService {

  private final TermDepositRepository depositRepository;
  private final DepositProductRepository productRepository;
  private final AccountAccessService access;
  private final AccountMoneyService moneyService;
  private final TermDepositMapper mapper;
  private final Clock clock;
  private final ZoneId zone;

  public TermDepositService(
      TermDepositRepository depositRepository,
      DepositProductRepository productRepository,
      AccountAccessService access,
      AccountMoneyService moneyService,
      TermDepositMapper mapper,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone) {
    this.depositRepository = depositRepository;
    this.productRepository = productRepository;
    this.access = access;
    this.moneyService = moneyService;
    this.mapper = mapper;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "depositProducts")
  public List<DepositProductResponse> products() {
    return productRepository.findByActiveTrueOrderByTenorMonthsAsc().stream()
        .map(this::toProductResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public DepositQuoteResponse quote(String productCode, BigDecimal amount) {
    DepositProductEntity product = requireActiveProduct(productCode);
    requireAtLeastMin(product, amount);
    LocalDate openDate = LocalDate.now(clock.withZone(zone));
    LocalDate maturityDate = openDate.plusMonths(product.getTenorMonths());
    long days = DepositInterestCalculator.daysBetween(openDate, maturityDate);
    BigDecimal interest = DepositInterestCalculator.interest(amount, product.getRateBps(), days);
    return new DepositQuoteResponse(
        product.getCode(),
        product.getTenorMonths(),
        product.getRateBps(),
        amount,
        openDate,
        maturityDate,
        days,
        interest,
        amount.add(interest));
  }

  @Transactional
  public TermDepositResponse open(OpenDepositRequest request, GatewayUser user) {
    AccountEntity source = access.requireOwnedOrStaff(request.sourceAccountId(), user);
    DepositProductEntity product = requireActiveProduct(request.productCode());
    requireAtLeastMin(product, request.amount());

    UUID id = UUID.randomUUID();
    Instant now = Instant.now(clock);
    LocalDate openDate = LocalDate.now(clock.withZone(zone));

    // Debit first: insufficient balance / frozen account aborts before the contract exists.
    moneyService.debit(
        source.getId(),
        new MoneyCommand(
            request.amount(),
            "DEP-" + id,
            "Mo so tiet kiem " + product.getCode(),
            "DEP-" + id));

    TermDepositEntity deposit = new TermDepositEntity();
    deposit.setId(id);
    deposit.setUserId(source.getUserId());
    deposit.setSourceAccountId(source.getId());
    deposit.setProductCode(product.getCode());
    deposit.setAmount(request.amount());
    deposit.setRateBps(product.getRateBps());
    deposit.setEarlyRateBps(product.getEarlyRateBps());
    deposit.setOpenedAt(now);
    deposit.setMaturityDate(openDate.plusMonths(product.getTenorMonths()));
    deposit.setStatus(TermDepositStatus.OPEN);
    deposit.setCreatedAt(now);
    deposit.setUpdatedAt(now);
    depositRepository.save(deposit);
    return toResponse(deposit, product.getTenorMonths());
  }

  /** Early settlement: demand rate on elapsed days, principal + interest back to source. */
  @Transactional
  public TermDepositResponse closeEarly(UUID depositId, GatewayUser user) {
    TermDepositEntity deposit = requireDeposit(depositId);
    access.requireOwnedOrStaff(deposit.getSourceAccountId(), user);
    if (deposit.getStatus() != TermDepositStatus.OPEN) {
      throw new BusinessException(
          "DEPOSIT_NOT_OPEN", "Deposit is not open");
    }

    LocalDate openDate = LocalDate.ofInstant(deposit.getOpenedAt(), zone);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    long days = DepositInterestCalculator.daysBetween(openDate, today);
    BigDecimal interest =
        DepositInterestCalculator.interest(deposit.getAmount(), deposit.getEarlyRateBps(), days);

    moneyService.credit(
        deposit.getSourceAccountId(),
        new MoneyCommand(
            deposit.getAmount().add(interest),
            "DEP-" + deposit.getId() + "-close",
            "Tat toan truoc han so tiet kiem",
            "DEP-" + deposit.getId() + "-close"));

    deposit.setStatus(TermDepositStatus.CLOSED_EARLY);
    deposit.setAccruedInterest(interest);
    deposit.setClosedAt(Instant.now(clock));
    deposit.setUpdatedAt(Instant.now(clock));
    depositRepository.save(deposit);
    return toResponse(deposit, tenorOf(deposit));
  }

  /**
   * Batch maturity settlement: principal + full-term interest back to source with ref
   * {@code DEP-{id}-mature}. Idempotent — an already-processed deposit is skipped, and the
   * ledger unique (account, ref, type) blocks double credits. One call = one transaction so a
   * failing deposit never rolls back its batch siblings.
   */
  @Transactional
  public boolean mature(UUID depositId) {
    TermDepositEntity deposit = requireDeposit(depositId);
    if (deposit.getStatus() != TermDepositStatus.OPEN) {
      return false;
    }
    LocalDate openDate = LocalDate.ofInstant(deposit.getOpenedAt(), zone);
    long days = DepositInterestCalculator.daysBetween(openDate, deposit.getMaturityDate());
    BigDecimal interest =
        DepositInterestCalculator.interest(deposit.getAmount(), deposit.getRateBps(), days);

    moneyService.credit(
        deposit.getSourceAccountId(),
        new MoneyCommand(
            deposit.getAmount().add(interest),
            "DEP-" + deposit.getId() + "-mature",
            "Tat toan dao han so tiet kiem",
            "DEP-" + deposit.getId() + "-mature"));

    deposit.setStatus(TermDepositStatus.MATURED);
    deposit.setAccruedInterest(interest);
    deposit.setClosedAt(Instant.now(clock));
    deposit.setUpdatedAt(Instant.now(clock));
    depositRepository.save(deposit);
    return true;
  }

  @Transactional(readOnly = true)
  public List<TermDepositResponse> listMine(UUID userId) {
    List<TermDepositEntity> list = depositRepository.findByUserIdOrderByOpenedAtDesc(userId);
    Map<String, Integer> tenorMap = productRepository.findAll().stream()
        .collect(Collectors.toMap(DepositProductEntity::getCode, DepositProductEntity::getTenorMonths, (a, b) -> a));
    return list.stream()
        .map(d -> toResponse(d, tenorMap.getOrDefault(d.getProductCode(), 0)))
        .toList();
  }

  @Transactional(readOnly = true)
  public TermDepositResponse get(UUID depositId, GatewayUser user) {
    TermDepositEntity deposit = requireDeposit(depositId);
    access.requireOwnedOrStaff(deposit.getSourceAccountId(), user);
    return toResponse(deposit, tenorOf(deposit));
  }

  private TermDepositEntity requireDeposit(UUID id) {
    return depositRepository
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(
                    "DEPOSIT_NOT_FOUND", "Term deposit not found"));
  }

  private DepositProductEntity requireActiveProduct(String code) {
    return productRepository
        .findById(code)
        .filter(DepositProductEntity::isActive)
        .orElseThrow(
            () ->
                new BusinessException(
                    "DEPOSIT_PRODUCT_NOT_FOUND",
                    "Deposit product not found or inactive"));
  }

  private void requireAtLeastMin(DepositProductEntity product, BigDecimal amount) {
    if (amount.compareTo(product.getMinAmount()) < 0) {
      throw new BusinessException(
          "DEPOSIT_BELOW_MINIMUM",
          "Minimum amount is " + product.getMinAmount().toPlainString());
    }
  }

  private int tenorOf(TermDepositEntity deposit) {
    return productRepository
        .findById(deposit.getProductCode())
        .map(DepositProductEntity::getTenorMonths)
        .orElse(0);
  }

  private TermDepositResponse toResponse(TermDepositEntity d, int tenorMonths) {
    return mapper.toResponse(d, tenorMonths, zone);
  }

  private DepositProductResponse toProductResponse(DepositProductEntity p) {
    return mapper.toProductResponse(p);
  }
}
