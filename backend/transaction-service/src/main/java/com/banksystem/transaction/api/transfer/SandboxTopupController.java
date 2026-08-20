package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequireAnyPermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.application.transfer.SandboxTopupGateService;
import com.banksystem.transaction.application.transfer.SandboxTopupService;
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
  private final SandboxTopupService topupService;

  public SandboxTopupController(
      SandboxTopupGateService gateService,
      SandboxTopupService topupService) {
    this.gateService = gateService;
    this.topupService = topupService;
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
    SandboxTopupService.Result result = topupService.topup(
        user.userId(), request.accountId(), request.amount());
    return ApiResponse.ok(new SandboxTopupResponse(
        result.accountId(),
        result.amount(),
        result.balanceAfter(),
        result.accumulatedToday(),
        result.remainingQuotaToday()));
  }
}
