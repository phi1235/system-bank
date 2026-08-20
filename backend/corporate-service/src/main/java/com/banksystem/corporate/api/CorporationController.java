package com.banksystem.corporate.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.corporate.api.dto.CorporateDtos.AddMemberRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporateAccountResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporateMemberResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CreateCorporateAccountRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporationResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CreateCorporationRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.LinkAccountRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.UpdateMemberRolesRequest;
import com.banksystem.corporate.application.corporation.CorporationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/corporations")
public class CorporationController {

  private final CorporationService corporationService;

  public CorporationController(CorporationService corporationService) {
    this.corporationService = corporationService;
  }

  @PostMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporationResponse>> createCorporation(
      @Valid @RequestBody CreateCorporationRequest req) {
    GatewayUser user = UserContext.requireUser();
    CorporationResponse res = corporationService.createCorporation(user.userId(), req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<CorporationResponse>>> listMyCorporations() {
    GatewayUser user = UserContext.requireUser();
    List<CorporationResponse> list = corporationService.listUserCorporations(user.userId());
    return ResponseEntity.ok(ApiResponse.ok(list));
  }

  @GetMapping("/{corporateId}")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporationResponse>> getCorporation(
      @PathVariable("corporateId") UUID corporateId) {
    GatewayUser user = UserContext.requireUser();
    CorporationResponse res = corporationService.getCorporation(corporateId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{corporateId}/members")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<CorporateMemberResponse>>> listMembers(
      @PathVariable("corporateId") UUID corporateId) {
    GatewayUser user = UserContext.requireUser();
    List<CorporateMemberResponse> list = corporationService.listMembers(corporateId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(list));
  }

  @PostMapping("/{corporateId}/members")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporateMemberResponse>> addMember(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody AddMemberRequest req) {
    GatewayUser user = UserContext.requireUser();
    CorporateMemberResponse res = corporationService.addMember(corporateId, user.userId(), req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PutMapping("/{corporateId}/members/{targetUserId}/roles")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporateMemberResponse>> updateMemberRoles(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("targetUserId") UUID targetUserId,
      @Valid @RequestBody UpdateMemberRolesRequest req) {
    GatewayUser user = UserContext.requireUser();
    CorporateMemberResponse res = corporationService.updateMemberRoles(
        corporateId, user.userId(), targetUserId, req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{corporateId}/accounts")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<CorporateAccountResponse>>> listAccounts(
      @PathVariable("corporateId") UUID corporateId) {
    GatewayUser user = UserContext.requireUser();
    List<CorporateAccountResponse> list = corporationService.listAccounts(corporateId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(list));
  }

  @PostMapping("/{corporateId}/accounts/link")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporateAccountResponse>> linkAccount(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody LinkAccountRequest req) {
    GatewayUser user = UserContext.requireUser();
    CorporateAccountResponse res = corporationService.linkAccount(corporateId, user.userId(), req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{corporateId}/accounts/create")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CorporateAccountResponse>> createAndLinkAccount(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody CreateCorporateAccountRequest request) {
    GatewayUser user = UserContext.requireUser();
    CorporateAccountResponse res = corporationService.createAndLinkNewAccount(
        corporateId, user.userId(), request.commandId(), request.accountType(), request.currency());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }
}
