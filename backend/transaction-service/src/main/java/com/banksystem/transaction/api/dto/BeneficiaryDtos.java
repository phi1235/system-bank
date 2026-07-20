package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BeneficiaryDtos {
  private BeneficiaryDtos() {}

  public record CreateBeneficiaryRequest(
      @NotBlank @Size(max = 80) String nickname,
      @NotBlank @Pattern(regexp = "\\d{8,14}") String accountNumber
  ) {}

  public record UpdateBeneficiaryRequest(
      @NotBlank @Size(max = 80) String nickname
  ) {}

  public record BeneficiaryResponse(
      String id,
      String nickname,
      String accountNumber,
      String accountId,
      String currency,
      boolean active,
      Instant createdAt
  ) {}
}
