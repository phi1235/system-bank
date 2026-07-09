package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.customer.api.dto.CustomerDtos.ExistsResponse;
import com.banksystem.customer.application.CustomerAppService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerController {

  private final CustomerAppService service;
  private final String apiKey;

  public InternalCustomerController(
      CustomerAppService service,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.service = service;
    this.apiKey = apiKey;
  }

  @GetMapping("/{id}/exists")
  public ApiResponse<ExistsResponse> exists(
      @PathVariable UUID id,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(new ExistsResponse(service.exists(id)));
  }

  private void requireKey(String key) {
    if (key == null || !key.equals(apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
