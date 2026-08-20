package com.banksystem.transaction.application.transfer;

import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SandboxTopupService {

  private final SandboxTopupGateService gateService;
  private final AccountGateway accountGateway;

  public SandboxTopupService(
      SandboxTopupGateService gateService,
      AccountGateway accountGateway) {
    this.gateService = gateService;
    this.accountGateway = accountGateway;
  }

  public Result topup(UUID userId, UUID accountId, BigDecimal amount) {
    BigDecimal accumulated = gateService.validateAndAccumulate(userId, amount);
    AccountView account = accountGateway.getAccount(accountId);
    String referenceId = "SANDBOX-TOPUP-" + UUID.randomUUID();
    MoneyResult result = accountGateway.credit(
        accountId,
        new MoneyCommand(amount, referenceId, "Sandbox 1-Click Topup", referenceId));
    BigDecimal balanceAfter = result != null
        ? result.balanceAfter()
        : account.balance().add(amount);
    BigDecimal remaining = SandboxTopupGateService.MAX_DAILY_QUOTA
        .subtract(accumulated)
        .max(BigDecimal.ZERO);
    return new Result(accountId, amount, balanceAfter, accumulated, remaining);
  }

  public record Result(
      UUID accountId,
      BigDecimal amount,
      BigDecimal balanceAfter,
      BigDecimal accumulatedToday,
      BigDecimal remainingQuotaToday) {}
}
