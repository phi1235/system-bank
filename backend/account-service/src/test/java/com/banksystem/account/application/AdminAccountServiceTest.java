package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
  private AdminAccountService service;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    AccountAccessService access = new AccountAccessService(accountRepository);
    AccountMapper mapper = new AccountMapper();
    service = new AdminAccountService(accountRepository, access, mapper);
  }

  @Test
  void adminList_mapsPageAndPassesFilters() {
    AccountEntity entity = sampleAccount("ACTIVE");
    when(accountRepository.adminSearch(eq("1001"), eq("ACTIVE"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

    PageResponse<AccountResponse> page = service.adminList(
        AdminAccountSearchQuery.of("1001", "active", 0, 20));

    assertEquals(1, page.items().size());
    assertEquals(entity.getAccountNumber(), page.items().get(0).accountNumber());
    assertEquals(1, page.totalElements());
    verify(accountRepository).adminSearch(eq("1001"), eq("ACTIVE"), isNull(), isNull(), any(Pageable.class));
  }

  @Test
  void adminList_parsesUuidQueryAsUserOrAccountId() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(accountRepository.adminSearch(eq(id.toString()), isNull(), eq(id), eq(id), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    service.adminList(AdminAccountSearchQuery.of(id.toString(), null, 0, 10));

    ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<UUID> accountIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(accountRepository).adminSearch(
        eq(id.toString()),
        isNull(),
        userIdCaptor.capture(),
        accountIdCaptor.capture(),
        any(Pageable.class));
    assertEquals(id, userIdCaptor.getValue());
    assertEquals(id, accountIdCaptor.getValue());
  }

  @Test
  void adminList_clampsPageSizeInQueryObject() {
    when(accountRepository.adminSearch(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    service.adminList(AdminAccountSearchQuery.of(null, null, -3, 500));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(accountRepository).adminSearch(isNull(), isNull(), isNull(), isNull(), pageableCaptor.capture());
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
  void freeze_isIdempotentWhenAlreadyFrozen() {
    AccountEntity entity = sampleAccount("FROZEN");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    AccountResponse response = service.freeze(entity.getId());

    assertEquals("FROZEN", response.status());
    verify(accountRepository, never()).save(any());
  }

  @Test
  void freeze_rejectsClosedAccount() {
    AccountEntity entity = sampleAccount("CLOSED");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.freeze(entity.getId()));
    assertEquals("ACCOUNT_CLOSED", ex.getCode());
    verify(accountRepository, never()).save(any());
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
