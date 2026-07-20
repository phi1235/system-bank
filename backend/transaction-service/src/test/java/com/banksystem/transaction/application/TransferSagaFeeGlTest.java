package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import com.banksystem.transaction.infrastructure.outbox.OutboxService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Saga + fee GL integration at unit level (AccountClient mocked).
 */
class TransferSagaFeeGlTest {

  private TransferOrderRepository transferOrderRepository;
  private SagaStepLogRepository sagaStepLogRepository;
  private AccountClient accountClient;
  private OutboxService outboxService;
  private TransferSagaOrchestrator saga;

  private final UUID fromId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private final UUID toId = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private final UUID incomeId = UUID.fromString("00000000-0000-0000-0000-0000000000fe");
  private final UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setUp() {
    transferOrderRepository = mock(TransferOrderRepository.class);
    sagaStepLogRepository = mock(SagaStepLogRepository.class);
    accountClient = mock(AccountClient.class);
    outboxService = mock(OutboxService.class);
    when(transferOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sagaStepLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TransferFeeGlService feeGl = new TransferFeeGlService(accountClient, "key", "1099999999");
    saga = new TransferSagaOrchestrator(
        transferOrderRepository,
        sagaStepLogRepository,
        accountClient,
        outboxService,
        feeGl,
        "key",
        false);
  }

  @Test
  void completesWithFeeGlCredit() {
    TransferOrderEntity order = pendingOrder(new BigDecimal("1000.00"), new BigDecimal("50.00"));
    stubDebitCreditOk();
    stubIncomeLookup();
    when(accountClient.credit(eq(incomeId), any(MoneyCommand.class), eq("key")))
        .thenReturn(ApiResponse.ok(new MoneyResult("fee-ledger", new BigDecimal("50.00"))));

    TransferOrderEntity result = saga.run(order);

    assertEquals(TransferStatus.COMPLETED, result.getStatus());
    assertEquals("fee-ledger", result.getFeeEntryRef());
    // debit source, credit dest, credit income
    verify(accountClient).debit(eq(fromId), any(MoneyCommand.class), eq("key"));
    verify(accountClient, times(2)).credit(any(UUID.class), any(MoneyCommand.class), eq("key"));

    ArgumentCaptor<MoneyCommand> incomeCmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(accountClient).credit(eq(incomeId), incomeCmd.capture(), eq("key"));
    assertEquals(0, new BigDecimal("50.00").compareTo(incomeCmd.getValue().amount()));
    assertEquals(order.getId() + "-fee", incomeCmd.getValue().referenceId());
    verify(outboxService).enqueue(eq("TRANSACTION_COMPLETED"), eq(order.getId()), any());
  }

  @Test
  void skipsFeeGlWhenFeeZero() {
    TransferOrderEntity order = pendingOrder(new BigDecimal("1000.00"), BigDecimal.ZERO);
    stubDebitCreditOk();

    TransferOrderEntity result = saga.run(order);

    assertEquals(TransferStatus.COMPLETED, result.getStatus());
    assertNull(result.getFeeEntryRef());
    verify(accountClient, never()).getByNumber(any(), any());
    // only dest credit — no income credit
    verify(accountClient, times(1)).credit(any(UUID.class), any(MoneyCommand.class), eq("key"));
  }

  private void stubDebitCreditOk() {
    when(accountClient.debit(eq(fromId), any(MoneyCommand.class), eq("key")))
        .thenReturn(ApiResponse.ok(new MoneyResult("debit-ledger", new BigDecimal("9000"))));
    when(accountClient.credit(eq(toId), any(MoneyCommand.class), eq("key")))
        .thenReturn(ApiResponse.ok(new MoneyResult("credit-ledger", new BigDecimal("2000"))));
  }

  private void stubIncomeLookup() {
    AccountView view = new AccountView(
        incomeId.toString(),
        "00000000-0000-0000-0000-000000000001",
        "1099999999",
        "INTERNAL",
        "VND",
        BigDecimal.ZERO,
        "ACTIVE");
    when(accountClient.getByNumber(eq("1099999999"), eq("key")))
        .thenReturn(ApiResponse.ok(view));
  }

  private TransferOrderEntity pendingOrder(BigDecimal amount, BigDecimal fee) {
    TransferOrderEntity o = new TransferOrderEntity();
    o.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    o.setUserId(userId);
    o.setFromAccountId(fromId);
    o.setToAccountId(toId);
    o.setToAccountNumber("1011111111");
    o.setAmount(amount);
    o.setFeeAmount(fee);
    o.setCurrency("VND");
    o.setDescription("test");
    o.setStatus(TransferStatus.PENDING);
    return o;
  }
}
