package com.banksystem.transaction.api.dto;

import com.banksystem.transaction.domain.virtualaccount.VirtualAccountMode;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class VirtualAccountDtos {
  private VirtualAccountDtos() {}

  public record ProvisionVirtualAccountRequest(
      String provider,
      String bankBin,
      UUID parentAccountId,
      @NotNull VirtualAccountMode mode,
      String customerReference,
      String displayName,
      Instant expiresAt
  ) {
    public ProvisionVirtualAccountRequest(
        String provider,
        String bankBin,
        UUID parentAccountId,
        VirtualAccountMode mode,
        String customerReference,
        Instant expiresAt
    ) {
      this(provider, bankBin, parentAccountId, mode, customerReference, null, expiresAt);
    }
  }


  public record VirtualAccountResponse(
      UUID id,
      UUID organizationId,
      String provider,
      String bankBin,
      String accountNumber,
      UUID parentAccountId,
      VirtualAccountMode mode,
      String customerReference,
      String displayName,
      VirtualAccountStatus status,
      String vietQrUrl,
      Instant activatedAt,
      Instant expiresAt,
      Instant createdAt
  ) {}


  public record VirtualAccountFilterRequest(
      String q,
      VirtualAccountStatus status,
      Integer page,
      Integer size
  ) {}

  public record AdminVirtualAccountFilterRequest(
      UUID organizationId,
      String q,
      VirtualAccountStatus status,
      Integer page,
      Integer size
  ) {}
}
