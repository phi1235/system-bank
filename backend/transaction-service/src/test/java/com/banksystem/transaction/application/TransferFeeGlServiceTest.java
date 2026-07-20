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

import com.banksystem.common.api.ApiError;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransferFeeGlServiceTest {

  private AccountClient accountClient;
  private TransferFeeGlService service;

  private final UUID incomeId = UUID.fromString("00000000-0000-0000-0000-0000000000fe");

  @BeforeEach
  void setUp() {
    accountClient = mock(AccountClient.class);
    service = new TransferFeeGlService(accountClient, "secret", "1099999999");
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
    when(accountClient.credit(eq(incomeId), any(MoneyCommand.class), eq("secret")))
        .thenReturn(ApiResponse.ok(new MoneyResult("ledger-fee-1", new BigDecimal("2500.00"))));

    String ledgerId = service.postFee(order);

    assertEquals("ledger-fee-1", ledgerId);
    ArgumentCaptor<MoneyCommand> cmd = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(accountClient).credit(eq(incomeId), cmd.capture(), eq("secret"));
    assertEquals(0, new BigDecimal("2500.00").compareTo(cmd.getValue().amount()));
    assertEquals(order.getId() + "-fee", cmd.getValue().referenceId());
  }

  @Test
  void postFee_failsWhenIncomeAccountMissing() {
    TransferOrderEntity order = order(new BigDecimal("100.00"));
    when(accountClient.getByNumber(eq("1099999999"), eq("secret")))
        .thenReturn(ApiResponse.fail(new ApiError("NOT_FOUND", "missing")));

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
    when(accountClient.getByNumber(eq("1099999999"), eq("secret")))
        .thenReturn(ApiResponse.ok(view));
  }

  private TransferOrderEntity order(BigDecimal fee) {
    TransferOrderEntity o = new TransferOrderEntity();
    o.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    o.setFeeAmount(fee);
    o.setAmount(new BigDecimal("100000"));
    return o;
  }
}
