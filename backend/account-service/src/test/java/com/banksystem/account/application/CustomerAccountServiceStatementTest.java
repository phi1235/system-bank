package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.account.application.query.LedgerStatementQuery;
import com.banksystem.account.config.GatewayUser;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.LedgerEntryEntity;
import com.banksystem.account.domain.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class CustomerAccountServiceStatementTest {

  private AccountRepository accountRepository;
  private LedgerEntryRepository ledgerEntryRepository;
  private CustomerAccountService service;

  private final UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID otherId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID accountId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    ledgerEntryRepository = mock(LedgerEntryRepository.class);
    AccountAccessService access = new AccountAccessService(accountRepository);
    AccountMapper mapper = new AccountMapper();
    AccountNumberGenerator numbers = new AccountNumberGenerator(accountRepository);
    service = new CustomerAccountService(
        accountRepository,
        ledgerEntryRepository,
        access,
        mapper,
        numbers,
        3,
        new BigDecimal("1000000"));
  }

  @Test
  void statement_forbidsOtherCustomer() {
    AccountEntity account = account(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    GatewayUser stranger = user(otherId, List.of("ib:accounts:view"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.statement(LedgerStatementQuery.of(accountId, 0, 20, null, null, null), stranger));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  @Test
  void statement_returnsSignedAmountsForOwner() {
    AccountEntity account = account(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    LedgerEntryEntity debit = entry("DEBIT", "1000.00");
    LedgerEntryEntity credit = entry("CREDIT", "500.00");
    when(ledgerEntryRepository.search(eq(accountId), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(debit, credit)));

    GatewayUser owner = user(ownerId, List.of("ib:accounts:view"));
    var page = service.statement(LedgerStatementQuery.of(accountId, 0, 20, null, null, null), owner);

    assertEquals(2, page.items().size());
    assertEquals(0, new BigDecimal("-1000.00").compareTo(page.items().get(0).signedAmount()));
    assertEquals(0, new BigDecimal("500.00").compareTo(page.items().get(1).signedAmount()));
    assertTrue(page.totalElements() >= 2);
  }

  @Test
  void statement_allowsStaffLookup() {
    AccountEntity account = account(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(ledgerEntryRepository.search(eq(accountId), eq("CREDIT"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entry("CREDIT", "10.00"))));

    GatewayUser staff = user(otherId, List.of("accounts:lookup:view"));
    var page = service.statement(
        LedgerStatementQuery.of(accountId, 0, 20, "CREDIT", null, null),
        staff);

    assertEquals(1, page.items().size());
    assertEquals("CREDIT", page.items().getFirst().entryType());
  }

  @Test
  void exportCsv_includesBomHeaderAndEscapedDescription() {
    AccountEntity account = account(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    LedgerEntryEntity withComma = entry("DEBIT", "1000.00");
    withComma.setDescription("Transfer, \"internal\"");
    when(ledgerEntryRepository.search(eq(accountId), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(withComma, entry("CREDIT", "500.00"))));

    GatewayUser owner = user(ownerId, List.of("ib:accounts:view"));
    byte[] csv = service.exportStatementCsv(
        LedgerStatementQuery.of(accountId, 0, LedgerStatementQuery.MAX_EXPORT_ROWS, null, null, null),
        owner);

    assertEquals((byte) 0xEF, csv[0]);
    assertEquals((byte) 0xBB, csv[1]);
    assertEquals((byte) 0xBF, csv[2]);
    String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(text.contains("createdAt,entryType,amount,signedAmount,referenceId,description"));
    assertTrue(text.contains("-1000.00"));
    // CSV escapes: "Transfer, ""internal"""
    assertTrue(text.contains("\"Transfer, \"\"internal\"\"\""));
  }

  @Test
  void exportCsv_forbidsOtherCustomer() {
    AccountEntity account = account(ownerId);
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    GatewayUser stranger = user(otherId, List.of("ib:accounts:view"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.exportStatementCsv(
            LedgerStatementQuery.of(accountId, 0, 20, null, null, null), stranger));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  private AccountEntity account(UUID userId) {
    AccountEntity a = new AccountEntity();
    a.setId(accountId);
    a.setUserId(userId);
    a.setAccountNumber("1012345678");
    a.setAccountType("PAYMENT");
    a.setCurrency("VND");
    a.setBalance(new BigDecimal("1000"));
    a.setStatus("ACTIVE");
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return a;
  }

  private LedgerEntryEntity entry(String type, String amount) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.setId(UUID.randomUUID());
    e.setAccountId(accountId);
    e.setEntryType(type);
    e.setAmount(new BigDecimal(amount));
    e.setReferenceId("ref-" + type);
    e.setDescription("test");
    e.setCreatedAt(Instant.parse("2026-07-21T00:00:00Z"));
    return e;
  }

  private GatewayUser user(UUID userId, List<String> permissions) {
    return new GatewayUser(userId, List.of(), permissions);
  }
}
