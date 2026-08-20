package com.banksystem.transaction.api.virtualaccount;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.ProvisionVirtualAccountRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountFilterRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountResponse;
import com.banksystem.transaction.application.virtualaccount.VirtualAccountSearchQuery;
import com.banksystem.transaction.application.virtualaccount.VirtualAccountService;
import com.banksystem.transaction.infrastructure.security.RequireBusinessPermission;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/virtual-accounts")
public class BusinessVirtualAccountController {

  private final VirtualAccountService virtualAccountService;

  public BusinessVirtualAccountController(VirtualAccountService virtualAccountService) {
    this.virtualAccountService = virtualAccountService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequireBusinessPermission(value = "business:va:manage", businessIdParam = "businessId")
  public ApiResponse<VirtualAccountResponse> provision(
      @PathVariable UUID businessId,
      @Valid @RequestBody ProvisionVirtualAccountRequest request) {
    return ApiResponse.ok(virtualAccountService.provision(businessId, request));
  }

  @GetMapping
  @RequireBusinessPermission(value = "business:va:view", businessIdParam = "businessId")
  public ApiResponse<PageResponse<VirtualAccountResponse>> search(
      @PathVariable UUID businessId,
      @Valid @ModelAttribute VirtualAccountFilterRequest req) {
    VirtualAccountSearchQuery query = VirtualAccountSearchQuery.of(businessId, req);
    Page<VirtualAccountResponse> result = virtualAccountService.search(query);
    return ApiResponse.ok(PageResponse.from(result));
  }

  @GetMapping("/{id}")
  @RequireBusinessPermission(value = "business:va:view", businessIdParam = "businessId")
  public ApiResponse<VirtualAccountResponse> getById(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    return ApiResponse.ok(virtualAccountService.getById(businessId, id));
  }

  @PostMapping("/{id}/close")
  @RequireBusinessPermission(value = "business:va:manage", businessIdParam = "businessId")
  public ApiResponse<Void> close(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    virtualAccountService.close(businessId, id);
    return ApiResponse.ok(null);
  }
}
