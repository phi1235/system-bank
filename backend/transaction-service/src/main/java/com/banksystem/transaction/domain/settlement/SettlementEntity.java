package com.banksystem.transaction.domain.settlement;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "settlements")
public class SettlementEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "collection_order_id", nullable = false, unique = true)
  private UUID collectionOrderId;

  @Column(name = "command_id", nullable = false, unique = true, length = 100)
  private String commandId;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "overpaid_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal overpaidAmount = BigDecimal.ZERO;

  @Column(name = "platform_commission", nullable = false, precision = 19, scale = 2)
  private BigDecimal platformCommission = BigDecimal.ZERO;

  @Column(name = "seller_net_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal sellerNetAmount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SettlementStatus status = SettlementStatus.PREPARING;

  @Column(name = "ledger_journal_id")
  private UUID ledgerJournalId;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private List<SettlementLegEntity> legs = new ArrayList<>();

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static SettlementEntity create(
      UUID organizationId,
      UUID collectionOrderId,
      BigDecimal grossAmount,
      BigDecimal platformCommission,
      BigDecimal sellerNetAmount,
      String currency,
      Instant now) {
    UUID id = UUID.nameUUIDFromBytes(
        ("SETTLEMENT:" + collectionOrderId).getBytes(StandardCharsets.UTF_8));
    return create(
        id, organizationId, collectionOrderId,
        "SETTLEMENT:" + id, "LEGACY_HASH",
        grossAmount, BigDecimal.ZERO, platformCommission, sellerNetAmount, currency, now
    );
  }

  public static SettlementEntity create(
      UUID id,
      UUID organizationId,
      UUID collectionOrderId,
      String commandId,
      String requestHash,
      BigDecimal grossAmount,
      BigDecimal overpaidAmount,
      BigDecimal platformCommission,
      BigDecimal sellerNetAmount,
      String currency,
      Instant now) {
    SettlementEntity entity = new SettlementEntity();
    entity.id = id;
    entity.organizationId = organizationId;
    entity.collectionOrderId = collectionOrderId;
    entity.commandId = commandId;
    entity.requestHash = requestHash;
    entity.grossAmount = grossAmount;
    entity.overpaidAmount = overpaidAmount != null ? overpaidAmount : BigDecimal.ZERO;
    entity.platformCommission = platformCommission != null ? platformCommission : BigDecimal.ZERO;
    entity.sellerNetAmount = sellerNetAmount;
    entity.currency = currency != null ? currency.toUpperCase() : "VND";
    entity.status = SettlementStatus.PREPARING;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public UUID getCollectionOrderId() { return collectionOrderId; }
  public void setCollectionOrderId(UUID collectionOrderId) { this.collectionOrderId = collectionOrderId; }
  public String getCommandId() { return commandId; }
  public void setCommandId(String commandId) { this.commandId = commandId; }
  public String getRequestHash() { return requestHash; }
  public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
  public BigDecimal getGrossAmount() { return grossAmount; }
  public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
  public BigDecimal getOverpaidAmount() { return overpaidAmount; }
  public void setOverpaidAmount(BigDecimal overpaidAmount) { this.overpaidAmount = overpaidAmount; }
  public BigDecimal getPlatformCommission() { return platformCommission; }
  public void setPlatformCommission(BigDecimal platformCommission) { this.platformCommission = platformCommission; }
  public BigDecimal getSellerNetAmount() { return sellerNetAmount; }
  public void setSellerNetAmount(BigDecimal sellerNetAmount) { this.sellerNetAmount = sellerNetAmount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public SettlementStatus getStatus() { return status; }
  public void setStatus(SettlementStatus status) { this.status = status; }
  public UUID getLedgerJournalId() { return ledgerJournalId; }
  public void setLedgerJournalId(UUID ledgerJournalId) { this.ledgerJournalId = ledgerJournalId; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
  public List<SettlementLegEntity> getLegs() { return legs; }
  public void setLegs(List<SettlementLegEntity> legs) { this.legs = legs; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
