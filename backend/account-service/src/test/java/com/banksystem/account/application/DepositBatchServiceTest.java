package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.DepositDtos.BatchRunResponse;
import com.banksystem.account.domain.TermDepositEntity;
import com.banksystem.account.domain.TermDepositRepository;
import com.banksystem.account.domain.TermDepositStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class DepositBatchServiceTest {

  // 2026-07-26 10:00 Asia/Bangkok
  private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");

  private TermDepositRepository repository;
  private TermDepositService termDepositService;
  private TransactionTemplate transactionTemplate;
  private DepositBatchService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    repository = mock(TermDepositRepository.class);
    termDepositService = mock(TermDepositService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            inv ->
                ((TransactionCallback<Integer>) inv.getArgument(0))
                    .doInTransaction(mock(TransactionStatus.class)));
    service =
        new DepositBatchService(
            repository,
            termDepositService,
            transactionTemplate,
            Clock.fixed(NOW, ZoneOffset.UTC),
            "Asia/Bangkok");
  }

  @Test
  void runAccruesThenMaturesEachDueDepositIsolatingFailures() {
    UUID ok = UUID.randomUUID();
    UUID boom = UUID.randomUUID();
    UUID alreadyDone = UUID.randomUUID();
    when(repository.accrueDailyInterest("Asia/Bangkok")).thenReturn(5);
    when(repository.findByStatusAndMaturityDateLessThanEqual(
            eq(TermDepositStatus.OPEN), eq(LocalDate.of(2026, 7, 26))))
        .thenReturn(List.of(deposit(ok), deposit(boom), deposit(alreadyDone)));
    when(termDepositService.mature(ok)).thenReturn(true);
    when(termDepositService.mature(boom)).thenThrow(new RuntimeException("ledger down"));
    when(termDepositService.mature(alreadyDone)).thenReturn(false);

    BatchRunResponse result = service.run();

    assertEquals(5, result.accruedUpdated());
    assertEquals(1, result.matured());
    assertEquals(1, result.failed());
    // The failure on the 2nd deposit must not stop the 3rd from being attempted.
    verify(termDepositService).mature(alreadyDone);
  }

  private static TermDepositEntity deposit(UUID id) {
    TermDepositEntity d = new TermDepositEntity();
    d.setId(id);
    return d;
  }
}
