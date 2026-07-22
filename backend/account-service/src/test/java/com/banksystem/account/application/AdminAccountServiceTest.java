package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AdminAccountServiceTest {

  private AccountRepository accountRepository;
  private OpsAlertPublisher opsAlertPublisher;
  private AdminAccountService service;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    opsAlertPublisher = mock(OpsAlertPublisher.class);
    AccountAccessService access = new AccountAccessService(accountRepository);
    AccountMapper mapper = new AccountMapper();
    service = new AdminAccountService(accountRepository, access, mapper, opsAlertPublisher);
  }

  @Test
  void adminList_mapsPageAndPassesFilters() {
    AccountEntity entity = sampleAccount("ACTIVE");
    when(accountRepository.adminSearch(
            eq(true),
            eq("1001"),
            eq(true),
            eq("ACTIVE"),
            eq(false),
            eq(""),
            eq(false),
            any(UUID.class),
            eq(false),
            any(UUID.class),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

    PageResponse<AccountResponse> page = service.adminList(
        AdminAccountSearchQuery.of("1001", "active", 0, 20));

    assertEquals(1, page.items().size());
    assertEquals(entity.getAccountNumber(), page.items().get(0).accountNumber());
    assertEquals(1, page.totalElements());
    assertNotNull(page.items().get(0).createdAt());
    assertNotNull(page.items().get(0).updatedAt());
    verify(accountRepository).adminSearch(
        eq(true),
        eq("1001"),
        eq(true),
        eq("ACTIVE"),
        eq(false),
        eq(""),
        eq(false),
        any(UUID.class),
        eq(false),
        any(UUID.class),
        any(Pageable.class));
  }

  @Test
  void adminList_passesAccountTypeFilter() {
    when(accountRepository.adminSearch(
            eq(false),
            eq(""),
            eq(false),
            eq(""),
            eq(true),
            eq("SAVINGS"),
            eq(false),
            any(UUID.class),
            eq(false),
            any(UUID.class),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    service.adminList(AdminAccountSearchQuery.of(null, null, "savings", 0, 20));

    verify(accountRepository).adminSearch(
        eq(false),
        eq(""),
        eq(false),
        eq(""),
        eq(true),
        eq("SAVINGS"),
        eq(false),
        any(UUID.class),
        eq(false),
        any(UUID.class),
        any(Pageable.class));
  }

  @Test
  void adminList_parsesUuidQueryAsUserOrAccountId() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(accountRepository.adminSearch(
            eq(true),
            eq(id.toString()),
            eq(false),
            eq(""),
            eq(false),
            eq(""),
            eq(true),
            eq(id),
            eq(true),
            eq(id),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    service.adminList(AdminAccountSearchQuery.of(id.toString(), null, 0, 10));

    ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<UUID> accountIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(accountRepository).adminSearch(
        eq(true),
        eq(id.toString()),
        eq(false),
        eq(""),
        eq(false),
        eq(""),
        eq(true),
        userIdCaptor.capture(),
        eq(true),
        accountIdCaptor.capture(),
        any(Pageable.class));
    assertEquals(id, userIdCaptor.getValue());
    assertEquals(id, accountIdCaptor.getValue());
  }

  @Test
  void adminList_clampsPageSizeInQueryObject() {
    when(accountRepository.adminSearch(
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(UUID.class),
            anyBoolean(),
            any(UUID.class),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    service.adminList(AdminAccountSearchQuery.of(null, null, -3, 500));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(accountRepository).adminSearch(
        anyBoolean(),
        any(),
        anyBoolean(),
        any(),
        anyBoolean(),
        any(),
        anyBoolean(),
        any(UUID.class),
        anyBoolean(),
        any(UUID.class),
        pageableCaptor.capture());
    assertEquals(0, pageableCaptor.getValue().getPageNumber());
    assertEquals(AdminAccountSearchQuery.MAX_SIZE, pageableCaptor.getValue().getPageSize());
  }

  @Test
  void adminList_rejectsInvalidStatus() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.adminList(AdminAccountSearchQuery.of(null, "HOLD", 0, 20)));
    assertEquals("INVALID_STATUS", ex.getCode());
  }

  @Test
  void adminList_rejectsInvalidAccountType() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.adminList(AdminAccountSearchQuery.of(null, null, "LOAN", 0, 20)));
    assertEquals("INVALID_ACCOUNT_TYPE", ex.getCode());
  }

  @Test
  void get_returnsMappedAccount() {
    AccountEntity entity = sampleAccount("ACTIVE");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    AccountResponse response = service.get(entity.getId());

    assertEquals(entity.getAccountNumber(), response.accountNumber());
    assertEquals("ACTIVE", response.status());
    assertNotNull(response.createdAt());
  }

  @Test
  void get_notFound() {
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(java.util.Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class, () -> service.get(id));
    assertEquals("ACCOUNT_NOT_FOUND", ex.getCode());
  }

  @Test
  void freeze_isIdempotentWhenAlreadyFrozen() {
    AccountEntity entity = sampleAccount("FROZEN");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    AccountResponse response = service.freeze(entity.getId());

    assertEquals("FROZEN", response.status());
    verify(accountRepository, never()).save(any());
    verify(opsAlertPublisher, never()).accountFrozen(any());
  }

  @Test
  void freeze_publishesOpsAlertWhenStatusChanges() {
    AccountEntity entity = sampleAccount("ACTIVE");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));
    when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    AccountResponse response = service.freeze(entity.getId());

    assertEquals("FROZEN", response.status());
    verify(opsAlertPublisher).accountFrozen(any(AccountEntity.class));
  }

  @Test
  void unfreeze_publishesOpsAlertWhenStatusChanges() {
    AccountEntity entity = sampleAccount("FROZEN");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));
    when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    AccountResponse response = service.unfreeze(entity.getId());

    assertEquals("ACTIVE", response.status());
    verify(opsAlertPublisher).accountUnfrozen(any(AccountEntity.class));
  }

  @Test
  void freeze_rejectsClosedAccount() {
    AccountEntity entity = sampleAccount("CLOSED");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.freeze(entity.getId()));
    assertEquals("ACCOUNT_CLOSED", ex.getCode());
    verify(accountRepository, never()).save(any());
    verify(opsAlertPublisher, never()).accountFrozen(any());
  }

  private AccountEntity sampleAccount(String status) {
    AccountEntity a = new AccountEntity();
    a.setId(UUID.randomUUID());
    a.setUserId(UUID.randomUUID());
    a.setAccountNumber("1012345678");
    a.setAccountType("PAYMENT");
    a.setCurrency("VND");
    a.setBalance(new BigDecimal("500000"));
    a.setStatus(status);
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return a;
  }
}
