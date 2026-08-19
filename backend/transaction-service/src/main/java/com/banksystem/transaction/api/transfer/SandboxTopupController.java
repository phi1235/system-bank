package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequireAnyPermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.application.transfer.SandboxTopupGateService;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxTopupController {

  public record SandboxTopupRequest(
      @NotNull UUID accountId,
      @NotNull @Positive BigDecimal amount
  ) {}

  public record SandboxTopupResponse(
      UUID accountId,
      BigDecimal amount,
      BigDecimal balanceAfter,
      BigDecimal accumulatedToday,
      BigDecimal remainingQuotaToday
  ) {}

  public record SandboxConfigResponse(
      boolean enabled,
      BigDecimal maxDailyQuota
  ) {}

  private final SandboxTopupGateService gateService;
  private final AccountGateway accountGateway;

  public SandboxTopupController(
      SandboxTopupGateService gateService,
      AccountGateway accountGateway) {
    this.gateService = gateService;
    this.accountGateway = accountGateway;
  }

  @GetMapping("/config")
  public ApiResponse<SandboxConfigResponse> getConfig() {
    return ApiResponse.ok(new SandboxConfigResponse(
        gateService.isSandboxTopupEnabled(),
        SandboxTopupGateService.MAX_DAILY_QUOTA));
  }

  @PostMapping("/topup")
  @RequireAnyPermission({
      SecurityHeaders.PERM_IB_TRANSFER_EXECUTE,
      SecurityHeaders.PERM_IB_ACCOUNTS_VIEW
  })
  public ApiResponse<SandboxTopupResponse> topup(@Valid @RequestBody SandboxTopupRequest request) {
    GatewayUser user = UserContext.requireUser();
    BigDecimal accumulated = gateService.validateAndAccumulate(user.userId(), request.amount());

    AccountView account = accountGateway.getAccount(request.accountId());
    String refId = "SANDBOX-TOPUP-" + UUID.randomUUID();
    MoneyResult result = accountGateway.credit(
        request.accountId(),
        new MoneyCommand(request.amount(), refId, "Sandbox 1-Click Topup", refId));

    BigDecimal remaining = SandboxTopupGateService.MAX_DAILY_QUOTA.subtract(accumulated);
    return ApiResponse.ok(new SandboxTopupResponse(
        request.accountId(),
        request.amount(),
        result != null ? result.balanceAfter() : account.balance().add(request.amount()),
        accumulated,
        remaining.max(BigDecimal.ZERO)));
  }
}
