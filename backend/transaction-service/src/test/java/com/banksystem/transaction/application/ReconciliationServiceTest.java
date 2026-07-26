package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ReconDtos.ReconRunResponse;
import com.banksystem.transaction.application.ReconciliationMatcher.Discrepancy;
import com.banksystem.transaction.domain.ReconItemRepository;
import com.banksystem.transaction.domain.ReconRunEntity;
import com.banksystem.transaction.domain.ReconRunRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.LedgerClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReconciliationServiceTest {

  // 2026-07-26 10:00 Asia/Bangkok
  private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");
  private static final LocalDate YESTERDAY = LocalDate.of(2026, 7, 25);

  private TransferOrderRepository transferRepo;
  private ReconRunRepository runRepo;
  private ReconItemRepository itemRepo;
  private LedgerClient ledgerClient;
  private ReconciliationMatcher matcher;
  private ReconciliationService service;

  @BeforeEach
  void setUp() {
    transferRepo = mock(TransferOrderRepository.class);
    runRepo = mock(ReconRunRepository.class);
    itemRepo = mock(ReconItemRepository.class);
    ledgerClient = mock(LedgerClient.class);
    matcher = mock(ReconciliationMatcher.class);
    service =
        new ReconciliationService(
            transferRepo,
            runRepo,
            itemRepo,
            ledgerClient,
            matcher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            "Asia/Bangkok",
            "test-key");
  }

  @Test
  void emptyDayIsMatchedWithoutLedgerCall() {
    when(transferRepo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
        .thenReturn(List.of());
    when(matcher.match(anyList(), anyList())).thenReturn(List.of());

    ReconRunResponse res = service.runForDate(YESTERDAY, ReconRunEntity.TRIGGER_MANUAL);

    assertEquals(ReconRunEntity.STATUS_MATCHED, res.status());
    assertEquals(0, res.ordersChecked());
    verify(ledgerClient, never()).search(any(), anyString());
  }

  @Test
  void discrepanciesMarkRunMismatchedAndPersistItems() {
    TransferOrderEntity t = transfer();
    when(transferRepo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
        .thenReturn(List.of(t));
    when(ledgerClient.search(any(), anyString())).thenReturn(ApiResponse.ok(List.of()));
    when(matcher.match(anyList(), anyList()))
        .thenReturn(
            List.of(
                new Discrepancy(
                    t.getId(),
                    ReconciliationMatcher.MISSING_DEBIT,
                    t.getId().toString(),
                    new BigDecimal("100.00"),
                    null,
                    "No source debit in ledger")));

    ReconRunResponse res = service.runForDate(YESTERDAY, ReconRunEntity.TRIGGER_SCHEDULED);

    assertEquals(ReconRunEntity.STATUS_MISMATCHED, res.status());
    assertEquals(1, res.discrepancyCount());
    assertEquals(1, res.ordersChecked());
    ArgumentCaptor<List<com.banksystem.transaction.domain.ReconItemEntity>> captor =
        ArgumentCaptor.captor();
    verify(itemRepo).saveAll(captor.capture());
    assertEquals(ReconciliationMatcher.MISSING_DEBIT, captor.getValue().get(0).getKind());
  }

  @Test
  void ledgerFailureMarksRunFailedInsteadOfThrowing() {
    when(transferRepo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
        .thenReturn(List.of(transfer()));
    when(ledgerClient.search(any(), anyString())).thenThrow(new RuntimeException("account down"));

    ReconRunResponse res = service.runForDate(YESTERDAY, ReconRunEntity.TRIGGER_SCHEDULED);

    assertEquals(ReconRunEntity.STATUS_FAILED, res.status());
    assertEquals("account down", res.errorDetail());
  }

  @Test
  void futureDateIsRejected() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.runForDate(LocalDate.of(2026, 7, 27), ReconRunEntity.TRIGGER_MANUAL));
    assertEquals("RECON_DATE_IN_FUTURE", ex.getCode());
  }

  private static TransferOrderEntity transfer() {
    TransferOrderEntity t = new TransferOrderEntity();
    t.setId(UUID.randomUUID());
    t.setFromAccountId(UUID.randomUUID());
    t.setToAccountId(UUID.randomUUID());
    t.setAmount(new BigDecimal("100.00"));
    t.setFeeAmount(BigDecimal.ZERO);
    t.setStatus(TransferStatus.COMPLETED);
    return t;
  }
}
