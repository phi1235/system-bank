package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.CreateBeneficiaryRequest;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.UpdateBeneficiaryRequest;
import com.banksystem.transaction.domain.BeneficiaryEntity;
import com.banksystem.transaction.domain.BeneficiaryRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BeneficiaryServiceTest {

  private BeneficiaryRepository repository;
  private AccountClient accountClient;
  private BeneficiaryService service;
  private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    repository = mock(BeneficiaryRepository.class);
    accountClient = mock(AccountClient.class);
    service = new BeneficiaryService(repository, accountClient, "test-key");
  }

  @Test
  void create_rejectsDuplicateAccountNumber() {
    when(repository.existsByUserIdAndAccountNumber(userId, "1012345678")).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.create(userId, new CreateBeneficiaryRequest("Mom", "1012345678")));
    assertEquals("BENEFICIARY_EXISTS", ex.getCode());
    verify(accountClient, never()).getByNumber(any(), any());
  }

  @Test
  void create_savesActiveDestination() {
    AccountView account = new AccountView(
        "11111111-1111-1111-1111-111111111111",
        "22222222-2222-2222-2222-222222222222",
        "1012345678",
        "PAYMENT",
        "VND",
        new BigDecimal("1000"),
        "ACTIVE");
    when(repository.existsByUserIdAndAccountNumber(userId, "1012345678")).thenReturn(false);
    when(accountClient.getByNumber("1012345678", "test-key")).thenReturn(ApiResponse.ok(account));
    when(repository.save(any(BeneficiaryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = service.create(userId, new CreateBeneficiaryRequest("  Mom  ", "1012345678"));

    assertEquals("Mom", response.nickname());
    assertEquals("1012345678", response.accountNumber());
    assertEquals(account.id(), response.accountId());
    verify(repository).save(any(BeneficiaryEntity.class));
  }

  @Test
  void rename_requiresOwnership() {
    UUID id = UUID.randomUUID();
    when(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.rename(userId, id, new UpdateBeneficiaryRequest("New")));
    assertEquals("BENEFICIARY_NOT_FOUND", ex.getCode());
  }

  @Test
  void deactivate_isIdempotentWhenAlreadyInactive() {
    UUID id = UUID.randomUUID();
    BeneficiaryEntity entity = new BeneficiaryEntity();
    entity.setId(id);
    entity.setUserId(userId);
    entity.setNickname("Mom");
    entity.setAccountNumber("1012345678");
    entity.setActive(false);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    when(repository.findByIdAndUserId(eq(id), eq(userId))).thenReturn(Optional.of(entity));

    service.deactivate(userId, id);

    verify(repository, never()).save(any());
  }
}
