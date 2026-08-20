package com.banksystem.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BusinessDtos {
  private BusinessDtos() {}

  public record CreateBusinessOrganizationRequest(
      @NotBlank @Size(min = 2, max = 50)
      @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must be alphanumeric with underscores or dashes")
      String code,

      @NotBlank @Size(min = 2, max = 255)
      String legalName,

      @Size(max = 50)
      String taxNumber
  ) {}

  public record BusinessOrganizationResponse(
      UUID id,
      String code,
      String legalName,
      String taxNumber,
      String status,
      Instant createdAt,
      String userRole
  ) {}

  public record AddBusinessMemberRequest(
      String username,
      UUID userId,
      @NotBlank String businessRole
  ) {}

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
      String status,
      Instant joinedAt
  ) {}

  public record BusinessMembershipVerifyResponse(
      boolean valid,
      UUID organizationId,
      UUID userId,
      String businessRole,
      String status,
      List<String> permissions
  ) {}
}
