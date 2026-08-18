package com.banksystem.customer.infrastructure.feign;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.customer.api.dto.DashboardDtos.InternalAccountCountsResponse;
import com.banksystem.customer.api.dto.DashboardDtos.InternalTransactionCountsResponse;
import com.banksystem.customer.api.dto.DashboardDtos.InternalUserCountsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

public final class DashboardClients {
  private DashboardClients() {}

  @FeignClient(name = "ACCOUNT-SERVICE", contextId = "accountCountsClient", url = "${bank.feign.account-url}")
  public interface AccountCountsClient {
    @GetMapping("/internal/accounts/counts")
    ApiResponse<InternalAccountCountsResponse> counts(
        @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String key);
  }

  @FeignClient(name = "TRANSACTION-SERVICE", contextId = "transactionCountsClient", url = "${bank.feign.transaction-url}")
  public interface TransactionCountsClient {
    @GetMapping("/internal/transactions/counts")
    ApiResponse<InternalTransactionCountsResponse> counts(
        @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String key);
  }

  @FeignClient(name = "AUTH-SERVICE", contextId = "userCountsClient", url = "${bank.feign.auth-url}")
  public interface UserCountsClient {
    @GetMapping("/internal/users/counts")
    ApiResponse<InternalUserCountsResponse> counts(
        @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String key);
  }
}
