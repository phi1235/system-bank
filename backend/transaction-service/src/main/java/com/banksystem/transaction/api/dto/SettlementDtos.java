package com.banksystem.transaction.api.dto;

import com.banksystem.transaction.domain.settlement.BeneficiaryType;
import com.banksystem.transaction.domain.settlement.SettlementLegStatus;
import com.banksystem.transaction.domain.settlement.SettlementLegType;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import com.banksystem.transaction.domain.settlement.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SettlementDtos {
  private SettlementDtos() {}

  public record SplitLegRequest(
      @NotNull BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      @NotNull SplitType splitType,
      @NotNull @DecimalMin(value = "0") BigDecimal value,
      int priority
  ) {}

  public record CreateSplitRuleRequest(
      @NotBlank String name,
      @NotEmpty List<@Valid SplitLegRequest> items
  ) {}

  public record SplitRuleResponse(
      UUID id,
      UUID organizationId,
      String name,
      String status,
      List<SplitLegResponse> items,
      Instant createdAt
  ) {}

  public record SplitLegResponse(
      UUID id,
      BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      SplitType splitType,
      BigDecimal value,
      int priority
  ) {}

  public record SettlementResponse(
      UUID id,
      UUID organizationId,
      UUID collectionOrderId,
      BigDecimal grossAmount,
      BigDecimal platformCommission,
      BigDecimal sellerNetAmount,
      String currency,
      SettlementStatus status,
      UUID ledgerJournalId,
      String failureReason,
      List<SettlementLegResponse> legs,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record SettlementLegResponse(
      UUID id,
      BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      BigDecimal amount,
      String currency,
      SettlementLegType legType,
      SettlementLegStatus status,
      UUID payoutId
  ) {}

  public record SettlementPreviewRequest(
      @NotNull @DecimalMin(value = "0.01") BigDecimal grossAmount,
      UUID splitRuleId,
      List<SplitLegRequest> customLegs
  ) {}

  public record SettlementPreviewResponse(
      BigDecimal grossAmount,
      BigDecimal platformCommission,
      BigDecimal sellerNetAmount,
      List<SettlementLegResponse> legs
  ) {}

  public record SettlementLegPreparedContext(
      UUID legId,
      String legKey,
      BeneficiaryType beneficiaryType,
      String beneficiaryId,
      UUID accountId,
      String bankBin,
      String accountNumber,
      String beneficiaryName,
      BigDecimal amount,
      String currency,
      SettlementLegType legType,
      SettlementLegStatus status,
      String ledgerAccountCode
  ) {}

  public record SettlementPreparedContext(
      UUID settlementId,
      UUID organizationId,
      UUID collectionOrderId,
      UUID merchantEscrowAccountId,
      BigDecimal grossAmount,
      BigDecimal overpaidAmount,
      BigDecimal platformCommission,
      BigDecimal sellerNetAmount,
      String currency,
      String commandId,
      String requestHash,
      UUID ledgerJournalId,
      SettlementStatus status,
      List<SettlementLegPreparedContext> legs
  ) {}

  public record SettlementFinalizedContext(
      UUID settlementId,
      UUID organizationId,
      UUID collectionOrderId,
      SettlementStatus status,
      UUID ledgerJournalId,
      List<UUID> externalPayoutLegIds
  ) {}

  public record SettlementFilterRequest(
      SettlementStatus status,
      Integer page,
      Integer size
  ) {}

  public record AdminSettlementFilterRequest(
      UUID organizationId,
      SettlementStatus status,
      Integer page,
      Integer size
  ) {}
}
