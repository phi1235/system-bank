package com.banksystem.account.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Batch display-name lookup for back-office enrichment (deposit owner columns). */
@FeignClient(name = "CUSTOMER-SERVICE", contextId = "customerClient", url = "${bank.feign.customer-url}")
public interface CustomerClient {

  record CustomerNamesRequest(List<UUID> userIds) {}

  record CustomerNameView(String userId, String fullName) {}

  @PostMapping("/internal/customers/names")
  ApiResponse<List<CustomerNameView>> names(
      @RequestBody CustomerNamesRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
