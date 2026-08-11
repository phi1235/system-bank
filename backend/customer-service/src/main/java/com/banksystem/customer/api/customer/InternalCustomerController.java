package com.banksystem.customer.api.customer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerContactResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNameResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNamesRequest;
import com.banksystem.customer.api.dto.CustomerDtos.ExistsResponse;
import com.banksystem.customer.application.customer.CustomerContactResult;
import com.banksystem.customer.application.customer.CustomerQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
@RequireInternalApiKey
public class InternalCustomerController {

  private final CustomerQueryService queryService;

  public InternalCustomerController(CustomerQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/{id}/exists")
  public ApiResponse<ExistsResponse> exists(@PathVariable UUID id) {
    return ApiResponse.ok(new ExistsResponse(queryService.exists(id)));
  }

  @GetMapping("/{id}/contact")
  public ApiResponse<CustomerContactResponse> contact(@PathVariable UUID id) {
    CustomerContactResult contact = queryService.contact(id);
    return ApiResponse.ok(new CustomerContactResponse(
        contact.userId(), contact.email(), contact.phone()));
  }

  /** Batch display names for back-office enrichment (e.g. deposit owner columns). */
  @PostMapping("/names")
  public ApiResponse<List<CustomerNameResponse>> names(
      @Valid @RequestBody CustomerNamesRequest request) {
    return ApiResponse.ok(queryService.namesByIds(request.userIds()));
  }
}
