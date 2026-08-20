package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "AUTH-SERVICE",
    contextId = "authBusinessClient",
    url = "${bank.feign.auth-url}",
    configuration = AuthBusinessClientConfig.class
)
public interface AuthBusinessClient {

  @GetMapping("/internal/businesses/{businessId}/members/{userId}/verify")
  ApiResponse<BusinessMembershipView> verifyMembership(
      @PathVariable("businessId") UUID businessId,
      @PathVariable("userId") UUID userId,
      @RequestParam(value = "permission", required = false) String permission);

  record BusinessMembershipView(
      boolean valid,
      UUID organizationId,
      UUID userId,
      String businessRole,
      String status,
      List<String> permissions
  ) {}
}
