package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.DepositDtos.DepositQuoteResponse;
import com.banksystem.account.api.dto.DepositDtos.OpenDepositRequest;
import com.banksystem.account.api.dto.DepositDtos.TermDepositResponse;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.banksystem.account.application.mapper.TermDepositMapper;

class TermDepositServiceTest {

  // 2026-07-26 10:00 Asia/Bangkok
  private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");
  private static final LocalDate TODAY_BKK = LocalDate.of(2026, 7, 26);

  private TermDepositRepository depositRepository;
  private DepositProductRepository productRepository;
  private AccountAccessService access;
  private AccountMoneyService moneyService;
  private TermDepositService service;

  private final UUID accountId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final GatewayUser owner = new GatewayUser(ownerId, List.of(), List.of("ib:wealth:view"));

  @BeforeEach
  void setUp() {
    depositRepository = mock(TermDepositRepository.class);
    productRepository = mock(DepositProductRepository.class);
    access = mock(AccountAccessService.class);
    moneyService = mock(AccountMoneyService.class);
    service =
        new TermDepositService(
            depositRepository,
            productRepository,
            access,
            moneyService,
            new TermDepositMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            "Asia/Bangkok");
    when(productRepository.findById("TD6M")).thenReturn(Optional.of(product("TD6M", 6, 460)));
  }

  @Test
  void quoteComputesInterestAtMaturity() {
    DepositQuoteResponse quote = service.quote("TD6M", new BigDecimal("10000000"));

    assertEquals(TODAY_BKK, quote.openDate());
    assertEquals(TODAY_BKK.plusMonths(6), quote.maturityDate());
    // 2026-07-26 -> 2027-01-26 = 184 days; 10,000,000 * 4.60% * 184/365 = 231,890.41
    assertEquals(184, quote.days());
    assertEquals(new BigDecimal("231890.41"), quote.expectedInterest());
    assertEquals(new BigDecimal("10231890.41"), quote.totalAtMaturity());
  }

  @Test
  void openDebitsSourceThenSavesContractWithRateSnapshot() {
    AccountEntity source = account();
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(source);
    when(moneyService.debit(eq(accountId), any()))
        .thenReturn(new MoneyResult(UUID.randomUUID().toString(), BigDecimal.ZERO));

    TermDepositResponse res =
        service.open(new OpenDepositRequest(accountId, "TD6M", new BigDecimal("10000000")), owner);

    ArgumentCaptor<MoneyCommand> cmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(moneyService).debit(eq(accountId), cmd.capture());
    assertEquals("DEP-" + res.id(), cmd.getValue().referenceId());

    ArgumentCaptor<TermDepositEntity> saved = ArgumentCaptor.forClass(TermDepositEntity.class);
    verify(depositRepository).save(saved.capture());
    assertEquals(TermDepositStatus.OPEN, saved.getValue().getStatus());
    assertEquals(460, saved.getValue().getRateBps());
    assertEquals(TODAY_BKK.plusMonths(6), saved.getValue().getMaturityDate());
    assertEquals(ownerId, saved.getValue().getUserId());
  }

  @Test
  void openBelowMinimumRejectsBeforeAnyDebit() {
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account());

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.open(
                    new OpenDepositRequest(accountId, "TD6M", new BigDecimal("500000")), owner));

    assertEquals("DEPOSIT_BELOW_MINIMUM", ex.getCode());
    verify(moneyService, never()).debit(any(), any());
  }

  @Test
  void openUnknownProductRejects() {
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account());
    when(productRepository.findById("NOPE")).thenReturn(Optional.empty());

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.open(
                    new OpenDepositRequest(accountId, "NOPE", new BigDecimal("2000000")), owner));
    assertEquals("DEPOSIT_PRODUCT_NOT_FOUND", ex.getCode());
  }

  @Test
  void closeEarlyPaysDemandRateOnElapsedDays() {
    TermDepositEntity deposit = openDeposit(NOW.minusSeconds(30L * 24 * 3600)); // opened 30 days ago
    when(depositRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account());
    when(moneyService.credit(eq(accountId), any()))
        .thenReturn(new MoneyResult(UUID.randomUUID().toString(), BigDecimal.ZERO));

    TermDepositResponse res = service.closeEarly(deposit.getId(), owner);

    // 10,000,000 * 0.50% * 30/365 = 4,109.59
    assertEquals(new BigDecimal("4109.59"), res.interest());
    assertEquals(TermDepositStatus.CLOSED_EARLY.name(), res.status());
    ArgumentCaptor<MoneyCommand> cmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(moneyService).credit(eq(accountId), cmd.capture());
    assertEquals(new BigDecimal("10004109.59"), cmd.getValue().amount());
    assertEquals("DEP-" + deposit.getId() + "-close", cmd.getValue().referenceId());
  }

  @Test
  void matureCreditsFullTermInterestWithMatureRef() {
    // Opened 2026-01-26 BKK, matures 2026-07-26 -> 181 days at 4.60%
    TermDepositEntity deposit = openDeposit(Instant.parse("2026-01-26T03:00:00Z"));
    deposit.setMaturityDate(LocalDate.of(2026, 7, 26));
    when(depositRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));
    when(moneyService.credit(eq(accountId), any()))
        .thenReturn(new MoneyResult(UUID.randomUUID().toString(), BigDecimal.ZERO));

    boolean processed = service.mature(deposit.getId());

    assertEquals(true, processed);
    ArgumentCaptor<MoneyCommand> cmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(moneyService).credit(eq(accountId), cmd.capture());
    // 10,000,000 * 4.60% * 181/365 = 228,109.59
    assertEquals(new BigDecimal("10228109.59"), cmd.getValue().amount());
    assertEquals("DEP-" + deposit.getId() + "-mature", cmd.getValue().referenceId());
    assertEquals(TermDepositStatus.MATURED, deposit.getStatus());
    assertEquals(new BigDecimal("228109.59"), deposit.getAccruedInterest());
  }

  @Test
  void matureSkipsAlreadyProcessedDeposit() {
    TermDepositEntity deposit = openDeposit(NOW.minusSeconds(3600));
    deposit.setStatus(TermDepositStatus.MATURED);
    when(depositRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));

    assertEquals(false, service.mature(deposit.getId()));
    verify(moneyService, never()).credit(any(), any());
  }

  @Test
  void closeEarlyRejectsNonOpenDeposit() {
    TermDepositEntity deposit = openDeposit(NOW.minusSeconds(3600));
    deposit.setStatus(TermDepositStatus.CLOSED_EARLY);
    when(depositRepository.findById(deposit.getId())).thenReturn(Optional.of(deposit));
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account());

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.closeEarly(deposit.getId(), owner));
    assertEquals("DEPOSIT_NOT_OPEN", ex.getCode());
    verify(moneyService, never()).credit(any(), any());
  }

  private AccountEntity account() {
    AccountEntity a = new AccountEntity();
    a.setId(accountId);
    a.setUserId(ownerId);
    return a;
  }

  private TermDepositEntity openDeposit(Instant openedAt) {
    TermDepositEntity d = new TermDepositEntity();
    d.setId(UUID.randomUUID());
    d.setUserId(ownerId);
    d.setSourceAccountId(accountId);
    d.setProductCode("TD6M");
    d.setAmount(new BigDecimal("10000000"));
    d.setRateBps(460);
    d.setEarlyRateBps(50);
    d.setOpenedAt(openedAt);
    d.setMaturityDate(TODAY_BKK.plusMonths(6));
    d.setStatus(TermDepositStatus.OPEN);
    return d;
  }

  private DepositProductEntity product(String code, int tenor, int rateBps) {
    DepositProductEntity p = new DepositProductEntity();
    p.setCode(code);
    p.setTenorMonths(tenor);
    p.setRateBps(rateBps);
    p.setEarlyRateBps(50);
    p.setMinAmount(new BigDecimal("1000000"));
    p.setActive(true);
    return p;
  }
}
