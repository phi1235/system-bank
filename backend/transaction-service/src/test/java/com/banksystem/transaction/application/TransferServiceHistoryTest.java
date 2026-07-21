package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.AuditLogRepository;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TransferServiceHistoryTest {

  private TransferOrderRepository transferOrderRepository;
  private TransferService service;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @BeforeEach
  void setUp() {
    transferOrderRepository = mock(TransferOrderRepository.class);
    service = new TransferService(
        transferOrderRepository,
        mock(AuditLogRepository.class),
        mock(SagaStepLogRepository.class),
        mock(AccountClient.class),
        mock(TransferSagaOrchestrator.class),
        mock(TransferLimitPolicy.class),
        mock(TransferFeePolicy.class),
        "test-internal-key");
  }

  @Test
  void myHistory_filtersByStatusAndRange() {
    TransferOrderEntity row = order(TransferStatus.COMPLETED);
    when(transferOrderRepository.searchMine(
            eq(userId),
            eq(TransferStatus.COMPLETED),
            any(Instant.class),
            any(Instant.class),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));

    Instant from = Instant.parse("2026-07-01T00:00:00Z");
    Instant to = Instant.parse("2026-07-21T23:59:59Z");
    var page = service.myHistory(userId, 0, 20, "COMPLETED", from, to);

    assertEquals(1, page.items().size());
    assertEquals("COMPLETED", page.items().getFirst().status());
  }

  @Test
  void myHistory_unknownStatusReturnsEmpty() {
    var page = service.myHistory(userId, 0, 20, "NOT_A_STATUS", null, null);
    assertTrue(page.items().isEmpty());
    assertEquals(0, page.totalElements());
  }

  @Test
  void myHistory_invalidDateRangeRejected() {
    Instant from = Instant.parse("2026-07-21T00:00:00Z");
    Instant to = Instant.parse("2026-07-01T00:00:00Z");
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.myHistory(userId, 0, 20, null, from, to));
    assertEquals("INVALID_DATE_RANGE", ex.getCode());
  }

  @Test
  void myHistory_blankStatusDelegatesWithNullStatus() {
    when(transferOrderRepository.searchMine(
            eq(userId), isNull(), isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(order(TransferStatus.FAILED))));

    var page = service.myHistory(userId, 0, 10, "  ", null, null);
    assertEquals(1, page.items().size());
    assertEquals("FAILED", page.items().getFirst().status());
  }

  private TransferOrderEntity order(TransferStatus status) {
    TransferOrderEntity e = new TransferOrderEntity();
    e.setId(UUID.randomUUID());
    e.setUserId(userId);
    e.setFromAccountId(UUID.randomUUID());
    e.setToAccountId(UUID.randomUUID());
    e.setToAccountNumber("1010000021");
    e.setAmount(new BigDecimal("1000.00"));
    e.setFeeAmount(BigDecimal.ZERO);
    e.setCurrency("VND");
    e.setDescription("demo");
    e.setStatus(status);
    e.setCreatedAt(Instant.parse("2026-07-10T10:00:00Z"));
    return e;
  }
}
