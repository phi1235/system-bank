package com.banksystem.customer.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class CustomerDtos {
  private CustomerDtos() {}

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
}
