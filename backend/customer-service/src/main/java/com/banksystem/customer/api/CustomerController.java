package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.CustomerDtos.CreateProfileRequest;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerSearchFilterRequest;
import com.banksystem.customer.api.dto.CustomerDtos.KycUpdateRequest;
import com.banksystem.customer.api.dto.CustomerDtos.UpdateProfileRequest;
import com.banksystem.customer.application.command.CustomerCommandService;
import com.banksystem.customer.application.query.CustomerQueryService;
import com.banksystem.customer.application.query.CustomerSearchQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {

  private final CustomerQueryService queryService;
  private final CustomerCommandService commandService;

  public CustomerController(
      CustomerQueryService queryService,
      CustomerCommandService commandService) {
    this.queryService = queryService;
    this.commandService = commandService;
  }

  @PostMapping("/customers/me")
  public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateProfileRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(commandService.create(user.userId(), req)));
  }

  @GetMapping("/customers/me")
  public ApiResponse<CustomerResponse> me() {
    return ApiResponse.ok(queryService.getMe(UserContext.requireUser().userId()));
  }

  @PutMapping("/customers/me")
  public ApiResponse<CustomerResponse> update(@Valid @RequestBody UpdateProfileRequest req) {
    return ApiResponse.ok(commandService.updateMe(UserContext.requireUser().userId(), req));
  }

  @GetMapping({"/customers", "/admin/customers"})
  @RequirePermission("customers:list:view")
  public ApiResponse<PageResponse<CustomerResponse>> list(
      @Valid @ModelAttribute CustomerSearchFilterRequest req) {
    return ApiResponse.ok(queryService.list(CustomerSearchQuery.of(req)));
  }

  @PostMapping({"/customers/findCustomerByCondition", "/admin/customers/findCustomerByCondition"})
  @RequirePermission("customers:list:view")
  public ApiResponse<PageResponse<CustomerResponse>> findCustomerByCondition(
      @Valid @RequestBody CustomerSearchFilterRequest req) {
    return ApiResponse.ok(queryService.list(CustomerSearchQuery.of(req)));
  }

  @PatchMapping({"/customers/{id}/kyc", "/admin/customers/{id}/kyc"})
  @RequirePermission("customers:kyc:decide")
  public ApiResponse<CustomerResponse> kyc(
      @PathVariable UUID id, @Valid @RequestBody KycUpdateRequest req) {
    return ApiResponse.ok(commandService.updateKyc(id, req));
  }
}
