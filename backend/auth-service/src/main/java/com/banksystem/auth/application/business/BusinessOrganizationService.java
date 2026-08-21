package com.banksystem.auth.application.business;

import com.banksystem.auth.api.dto.BusinessDtos.AddBusinessMemberRequest;
import com.banksystem.auth.api.dto.BusinessDtos.AdminBusinessResponse;
import com.banksystem.auth.api.dto.BusinessDtos.AdminKycReviewRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMemberResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMembershipVerifyResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessOrganizationResponse;
import com.banksystem.auth.api.dto.BusinessDtos.CreateBusinessOrganizationRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessPermissionMatrixResponse;
import com.banksystem.auth.api.dto.BusinessDtos.CustomRoleRequest;
import com.banksystem.auth.api.dto.BusinessDtos.CustomRoleResponse;
import com.banksystem.auth.api.dto.BusinessDtos.RegisterBusinessRequest;
import com.banksystem.auth.api.dto.BusinessDtos.UpdateBusinessMemberRequest;
import java.util.List;
import java.util.UUID;

public interface BusinessOrganizationService {

  BusinessOrganizationResponse createOrganization(UUID userId, CreateBusinessOrganizationRequest request);

  BusinessOrganizationResponse registerBusiness(UUID userId, RegisterBusinessRequest request);

  BusinessOrganizationResponse reviewKyc(UUID organizationId, UUID adminUserId, AdminKycReviewRequest request);

  List<AdminBusinessResponse> listAdminBusinesses(String kycStatus);

  List<BusinessOrganizationResponse> listUserOrganizations(UUID userId);

  BusinessOrganizationResponse getOrganization(UUID organizationId, UUID userId);

  List<BusinessMemberResponse> listMembers(UUID organizationId, UUID requestingUserId);

  BusinessMemberResponse addMember(UUID organizationId, UUID requestingUserId, AddBusinessMemberRequest request);

  BusinessMemberResponse updateMemberRole(UUID organizationId, UUID targetUserId, UUID requestingUserId, UpdateBusinessMemberRequest request);

  void removeMember(UUID organizationId, UUID targetUserId, UUID requestingUserId);

  BusinessMembershipVerifyResponse verifyMembership(UUID organizationId, UUID userId, String requiredPermission);

  BusinessMembershipVerifyResponse getMyMembership(UUID organizationId, UUID userId);

  // --- Dynamic Custom Roles ---
  List<CustomRoleResponse> listCustomRoles(UUID organizationId, UUID requestingUserId);

  CustomRoleResponse createCustomRole(UUID organizationId, UUID requestingUserId, CustomRoleRequest request);

  CustomRoleResponse updateCustomRole(UUID organizationId, UUID roleId, UUID requestingUserId, CustomRoleRequest request);

  void deleteCustomRole(UUID organizationId, UUID roleId, UUID requestingUserId);

  List<String> listAvailablePermissions();

  BusinessPermissionMatrixResponse getPermissionMatrix();
}

