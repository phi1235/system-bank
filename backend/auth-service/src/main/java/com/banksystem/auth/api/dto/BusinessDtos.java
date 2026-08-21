package com.banksystem.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BusinessDtos {
  private BusinessDtos() {}

  public record CreateBusinessOrganizationRequest(
      @NotBlank @Size(min = 2, max = 50)
      @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must be alphanumeric with underscores or dashes")
      String code,

      @NotBlank @Size(min = 2, max = 255)
      @JsonAlias({"name"})
      String legalName,

      @Size(max = 50)
      @JsonAlias({"taxCode"})
      String taxNumber
  ) {
    public CreateBusinessOrganizationRequest(String code, String legalName, String taxNumber) {
      this.code = code;
      this.legalName = legalName;
      this.taxNumber = taxNumber;
    }
  }

  public record BusinessOrganizationResponse(
      UUID id,
      String code,
      String legalName,
      String taxNumber,
      String status,
      Instant createdAt,
      String userRole
  ) {
    @JsonProperty("name")
    public String name() {
      return legalName;
    }

    @JsonProperty("taxCode")
    public String taxCode() {
      return taxNumber;
    }
  }

  public record AddBusinessMemberRequest(
      String username,
      String userId,
      String userIdentifier,
      @JsonProperty("role") String role,
      String businessRole
  ) {
    public String effectiveRole() {
      if (businessRole != null && !businessRole.isBlank()) return businessRole;
      if (role != null && !role.isBlank()) return role;
      return null;
    }

    public String effectiveIdentifier() {
      if (userIdentifier != null && !userIdentifier.isBlank()) return userIdentifier.trim();
      if (username != null && !username.isBlank()) return username.trim();
      if (userId != null && !userId.isBlank()) return userId.trim();
      return null;
    }
  }

  public record UpdateBusinessMemberRequest(
      @NotBlank String businessRole,
      String status
  ) {}

  public record BusinessMemberResponse(
      UUID id,
      UUID organizationId,
      UUID userId,
      String username,
      String email,
      String businessRole,
      String roleDisplayName,
      String status,
      Instant joinedAt
  ) {
    @JsonProperty("userFullName")
    public String userFullName() {
      return username;
    }

    @JsonProperty("userEmail")
    public String userEmail() {
      return email;
    }

    @JsonProperty("createdAt")
    public Instant createdAt() {
      return joinedAt;
    }
  }

  public record BusinessMembershipVerifyResponse(
      boolean valid,
      UUID organizationId,
      UUID userId,
      String businessRole,
      String roleDisplayName,
      String status,
      List<String> permissions
  ) {}

  // --- KYC Onboarding DTOs ---

  public record RegisterBusinessRequest(
      @NotBlank @Size(min = 2, max = 255) String legalName,
      @NotBlank @Size(max = 50) String taxNumber,
      @Size(max = 160) String contactEmail,
      @Size(max = 50) String contactPhone,
      @Size(max = 500) String address,
      @Size(max = 255) String representativeName,
      @Size(max = 100) String industry
  ) {}

  public record AdminBusinessResponse(
      UUID id,
      String code,
      String legalName,
      String taxNumber,
      String status,
      String kycStatus,
      String contactEmail,
      String contactPhone,
      String address,
      String representativeName,
      String industry,
      String businessLicenseUrl,
      String idCardUrl,
      String kycRejectReason,
      UUID kycReviewedBy,
      Instant kycReviewedAt,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record AdminKycReviewRequest(
      @NotBlank String action,
      String rejectReason
  ) {
    public boolean isApprove() {
      return "APPROVE".equalsIgnoreCase(action);
    }
  }

  // --- Custom Role DTOs ---

  public record CustomRoleRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 100) String displayName,
      @Size(max = 255) String description,
      @NotNull List<String> permissions
  ) {}

  public record CustomRoleResponse(
      UUID id,
      String code,
      String displayName,
      String description,
      boolean ownerRole,
      boolean defaultRole,
      List<String> permissions,
      Instant createdAt
  ) {}

  // --- Dynamic Permission Matrix Schema DTOs ---

  public record BusinessPermissionActionDto(
      String key,
      String labelKey,
      String icon
  ) {}

  public record BusinessPermissionFeatureDto(
      String id,
      String name,
      String nameKey,
      Map<String, String> actions
  ) {}

  public record BusinessPermissionModuleDto(
      String id,
      String name,
      String nameKey,
      String icon,
      List<BusinessPermissionFeatureDto> features
  ) {}

  public record BusinessPermissionMatrixResponse(
      List<BusinessPermissionActionDto> actionColumns,
      List<BusinessPermissionModuleDto> modules
  ) {}
}

