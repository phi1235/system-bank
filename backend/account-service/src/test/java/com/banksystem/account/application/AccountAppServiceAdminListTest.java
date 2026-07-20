package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.account.domain.LedgerEntryRepository;
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

class AccountAppServiceAdminListTest {

  private AccountRepository accountRepository;
  private AccountAppService service;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    service = new AccountAppService(accountRepository, ledgerEntryRepository, 3, new BigDecimal("1000000"));
  }

  @Test
  void adminList_mapsPageAndPassesFilters() {
    AccountEntity entity = sampleAccount("ACTIVE");
    when(accountRepository.adminSearch(eq("1001"), eq("ACTIVE"), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

    PageResponse<AccountResponse> page = service.adminList("1001", "active", 0, 20);

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

    service.adminList(id.toString(), null, 0, 10);

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
  void adminList_rejectsInvalidStatus() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.adminList(null, "HOLD", 0, 20));
    assertEquals("INVALID_STATUS", ex.getCode());
  }

  @Test
  void freeze_isIdempotentWhenAlreadyFrozen() {
    AccountEntity entity = sampleAccount("FROZEN");
    when(accountRepository.findById(entity.getId())).thenReturn(java.util.Optional.of(entity));

    AccountResponse response = service.freeze(entity.getId());

    assertEquals("FROZEN", response.status());
    assertTrue(true);
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
