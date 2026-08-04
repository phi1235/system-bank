package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransferFeeGlServiceTest {

  private AccountGateway accountGateway;
  private TransferFeeGlService service;

  private final UUID incomeId = UUID.fromString("00000000-0000-0000-0000-0000000000fe");

  @BeforeEach
  void setUp() {
    accountGateway = mock(AccountGateway.class);
    service = new TransferFeeGlService(accountGateway, "1099999999");
  }

  @Test
  void requiresPosting_falseWhenFeeZero() {
    TransferOrderEntity order = order(BigDecimal.ZERO);
    assertFalse(service.requiresPosting(order));
  }

  @Test
  void requiresPosting_trueWhenFeePositive() {
    assertTrue(service.requiresPosting(order(new BigDecimal("1000.00"))));
  }

  @Test
  void postFee_skipsWhenZero() {
    assertNull(service.postFee(order(BigDecimal.ZERO)));
  }

  @Test
  void postFee_creditsIncomeAccountWithIdempotentRef() {
    TransferOrderEntity order = order(new BigDecimal("2500.00"));
    stubIncomeLookup();
    when(accountGateway.credit(eq(incomeId), any(MoneyCommand.class)))
        .thenReturn(new MoneyResult("ledger-fee-1", new BigDecimal("2500.00")));

    String ledgerId = service.postFee(order);

    assertEquals("ledger-fee-1", ledgerId);
    ArgumentCaptor<MoneyCommand> cmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(accountGateway).credit(eq(incomeId), cmd.capture());
    assertEquals(0, new BigDecimal("2500.00").compareTo(cmd.getValue().amount()));
    assertEquals(order.getId() + "-fee", cmd.getValue().referenceId());
  }

  @Test
  void postFee_failsWhenIncomeAccountMissing() {
    TransferOrderEntity order = order(new BigDecimal("100.00"));
    when(accountGateway.getAccountByNumber(eq("1099999999")))
        .thenReturn(null);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.postFee(order));
    assertEquals("FEE_INCOME_ACCOUNT_MISSING", ex.getCode());
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
    when(accountGateway.getAccountByNumber(eq("1099999999")))
        .thenReturn(view);
  }

  private TransferOrderEntity order(BigDecimal fee) {
    TransferOrderEntity o = new TransferOrderEntity();
    o.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    o.setFeeAmount(fee);
    o.setAmount(new BigDecimal("100000"));
    return o;
  }
}
