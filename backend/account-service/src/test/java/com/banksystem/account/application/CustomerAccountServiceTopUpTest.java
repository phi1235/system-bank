package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.AccountDtos.MoneyCommand;
import com.banksystem.account.api.dto.AccountDtos.MoneyResult;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerAccountServiceTopUpTest {

  private AccountRepository accountRepository;
  private LedgerEntryRepository ledgerEntryRepository;
  private AccountMoneyService moneyService;
  private CustomerAccountService service;

  private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final UUID strangerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private final UUID accountId = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    ledgerEntryRepository = mock(LedgerEntryRepository.class);
    moneyService = mock(AccountMoneyService.class);
    AccountAccessService access = new AccountAccessService(accountRepository);
    AccountMapper mapper = new AccountMapper();
    AccountNumberGenerator numbers = new AccountNumberGenerator(accountRepository);

    service = new CustomerAccountService(
        accountRepository,
        ledgerEntryRepository,
        access,
        mapper,
        numbers,
        moneyService,
        3,
        new BigDecimal("1000000"),
        new BigDecimal("50000000"));
  }

  @Test
  void topUp_ownerSuccess() {
    AccountEntity acc = sampleAccount(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
    when(moneyService.credit(eq(accountId), any(MoneyCommand.class)))
        .thenReturn(new MoneyResult("ledger-cust-1", new BigDecimal("2000000")));

    GatewayUser owner = new GatewayUser(ownerId, List.of("CUSTOMER"), List.of("ib:accounts:view"));
    TopUpResponse resp = service.topUp(accountId, new TopUpRequest(new BigDecimal("1000000"), "Mock deposit"), owner);

    assertEquals(accountId.toString(), resp.accountId());
    assertEquals("1000000001", resp.accountNumber());
    assertEquals("ledger-cust-1", resp.ledgerEntryId());
    assertEquals(new BigDecimal("1000000"), resp.amount());
    assertEquals(new BigDecimal("2000000"), resp.balanceAfter());
    assertEquals("CUSTOMER_MOCK", resp.channel());
    assertTrue(resp.referenceId().startsWith("CUST-TOPUP-"));
  }

  @Test
  void topUp_forbidsNonOwner() {
    AccountEntity acc = sampleAccount(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));

    GatewayUser stranger = new GatewayUser(strangerId, List.of("CUSTOMER"), List.of("ib:accounts:view"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.topUp(accountId, new TopUpRequest(new BigDecimal("500000"), "hack"), stranger));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  @Test
  void topUp_rejectsInvalidAmount() {
    GatewayUser owner = new GatewayUser(ownerId, List.of("CUSTOMER"), List.of("ib:accounts:view"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.topUp(accountId, new TopUpRequest(new BigDecimal("-100"), "neg"), owner));
    assertEquals("INVALID_AMOUNT", ex.getCode());
  }

  @Test
  void topUp_rejectsOverMaxAmount() {
    GatewayUser owner = new GatewayUser(ownerId, List.of("CUSTOMER"), List.of("ib:accounts:view"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.topUp(accountId, new TopUpRequest(new BigDecimal("100000000"), "too large"), owner));
    assertEquals("TOPUP_MAX_EXCEEDED", ex.getCode());
  }

  private AccountEntity sampleAccount(UUID userId) {
    AccountEntity a = new AccountEntity();
    a.setId(accountId);
    a.setUserId(userId);
    a.setAccountNumber("1000000001");
    a.setAccountType("PAYMENT");
    a.setCurrency("VND");
    a.setBalance(new BigDecimal("1000000"));
    a.setStatus("ACTIVE");
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return a;
  }
}
