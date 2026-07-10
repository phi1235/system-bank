package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CreateProfileRequest;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.KycUpdateRequest;
import com.banksystem.customer.api.dto.CustomerDtos.UpdateProfileRequest;
import com.banksystem.customer.application.CustomerAppService;
import com.banksystem.customer.config.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {

  private final CustomerAppService service;

  public CustomerController(CustomerAppService service) {
    this.service = service;
  }

  @PostMapping("/customers/me")
  public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateProfileRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.create(user.userId(), req)));
  }

  @GetMapping("/customers/me")
  public ApiResponse<CustomerResponse> me() {
    return ApiResponse.ok(service.getMe(UserContext.requireUser().userId()));
  }

  @PutMapping("/customers/me")
  public ApiResponse<CustomerResponse> update(@Valid @RequestBody UpdateProfileRequest req) {
    return ApiResponse.ok(service.updateMe(UserContext.requireUser().userId(), req));
  }

  @GetMapping({"/customers", "/admin/customers"})
  public ApiResponse<PageResponse<CustomerResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    UserContext.requirePermission("customers:list:view");
    return ApiResponse.ok(service.list(q, page, Math.min(size, 100)));
  }

  @PatchMapping({"/customers/{id}/kyc", "/admin/customers/{id}/kyc"})
  public ApiResponse<CustomerResponse> kyc(
      @PathVariable UUID id, @Valid @RequestBody KycUpdateRequest req) {
    UserContext.requirePermission("customers:kyc:decide");
    return ApiResponse.ok(service.updateKyc(id, req));
  }
}
