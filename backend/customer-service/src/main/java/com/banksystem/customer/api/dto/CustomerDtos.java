package com.banksystem.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {
  private CustomerDtos() {}

  /** Internal batch display-name lookup (e.g. deposit back office enriching owner columns). */
  public record CustomerNamesRequest(@NotNull @Size(min = 1, max = 500) List<UUID> userIds) {}

  public record CustomerNameResponse(String userId, String fullName) {}

  public record CustomerContactResponse(String userId, String email, String phone) {}

  public record CreateProfileRequest(
      @NotBlank String fullName,
      String phone,
      String email,
      String nationalId,
      String address
  ) {}

  public record UpdateProfileRequest(
      String fullName,
      String phone,
      String email,
      String address
  ) {}

  public record KycUpdateRequest(@NotBlank String kycStatus) {}

  public record CustomerResponse(
      String id,
      String fullName,
      String phone,
      String email,
      String nationalIdMasked,
      String kycStatus,
      String address
  ) {}

  public record ExistsResponse(boolean exists) {}

  public record CustomerSearchFilterRequest(
      Integer page,
      Integer size,
      String q,
      String kycStatus
  ) {}
}
