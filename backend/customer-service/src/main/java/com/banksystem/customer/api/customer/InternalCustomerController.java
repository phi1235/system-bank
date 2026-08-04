package com.banksystem.customer.api.customer;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNameResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNamesRequest;
import com.banksystem.customer.api.dto.CustomerDtos.ExistsResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerController {

  private final CustomerQueryService queryService;
  private final String apiKey;

  public InternalCustomerController(
      CustomerQueryService queryService,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.queryService = queryService;
    this.apiKey = apiKey;
  }

  @GetMapping("/{id}/exists")
  public ApiResponse<ExistsResponse> exists(
      @PathVariable UUID id,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(new ExistsResponse(queryService.exists(id)));
  }

  /** Batch display names for back-office enrichment (e.g. deposit owner columns). */
  @PostMapping("/names")
  public ApiResponse<List<CustomerNameResponse>> names(
      @Valid @RequestBody CustomerNamesRequest request,
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    return ApiResponse.ok(queryService.namesByIds(request.userIds()));
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
