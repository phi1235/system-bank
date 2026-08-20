package com.banksystem.corporate.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CorporateDtos {
  private CorporateDtos() {}

  public record CreateCorporationRequest(
      @NotBlank String taxId,
      @NotBlank String companyName,
      String shortName,
      @Email String contactEmail,
      String contactPhone,
      String address
  ) {}

  public record CorporationResponse(
      UUID id,
      String taxId,
      String companyName,
      String shortName,
      String kycStatus,
      String status,
      String contactEmail,
      String contactPhone,
      String address,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record AddMemberRequest(
      @NotNull UUID userId,
      @NotEmpty Set<String> roles,
      Instant expiresAt
  ) {}

  public record UpdateMemberRolesRequest(
      @NotEmpty Set<String> roles
  ) {}

  public record CorporateMemberResponse(
      UUID id,
      UUID corporateId,
      UUID userId,
      String status,
      Set<String> roles,
      Instant joinedAt,
      Instant expiresAt
  ) {}

  public record LinkAccountRequest(
      @NotNull UUID accountId,
      @NotBlank String accountNumber,
      String accountName,
      String currency,
      boolean isPrimary,
      BigDecimal dailyPayoutLimit
  ) {}

  public record CreateCorporateAccountRequest(
      @NotNull UUID commandId,
      @NotBlank String accountType,
      @NotBlank String currency
  ) {}

  public record CorporateAccountResponse(
      UUID id,
      UUID corporateId,
      UUID accountId,
      String accountNumber,
      String accountName,
      String currency,
      BigDecimal balance,
      boolean isPrimary,
      String status,
      BigDecimal dailyPayoutLimit,
      Instant createdAt
  ) {}
}
