package com.banksystem.corporate.domain.approval;

import com.banksystem.corporate.domain.corporation.CorporationEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_policies")
public class ApprovalPolicyEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "corporate_id", nullable = false)
  private CorporationEntity corporation;

  @Column(name = "corporate_id", insertable = false, updatable = false)
  private UUID corporateId;

  @Column(name = "policy_name", nullable = false, length = 160)
  private String policyName;

  @Column(name = "version_number", nullable = false)
  private int versionNumber = 1;

  @Column(nullable = false, length = 30)
  private String status = "DRAFT"; // DRAFT, ACTIVE, RETIRED

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "allow_self_approval", nullable = false)
  private boolean allowSelfApproval = false;

  @Column(name = "require_role_separation", nullable = false)
  private boolean requireRoleSeparation = true;

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("priorityOrder ASC")
  private List<ApprovalTierEntity> tiers = new ArrayList<>();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CorporationEntity getCorporation() { return corporation; }
  public void setCorporation(CorporationEntity corporation) {
    this.corporation = corporation;
    if (corporation != null) {
      this.corporateId = corporation.getId();
    }
  }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public String getPolicyName() { return policyName; }
  public void setPolicyName(String policyName) { this.policyName = policyName; }
  public int getVersionNumber() { return versionNumber; }
  public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public boolean isAllowSelfApproval() { return allowSelfApproval; }
  public void setAllowSelfApproval(boolean allowSelfApproval) { this.allowSelfApproval = allowSelfApproval; }
  public boolean isRequireRoleSeparation() { return requireRoleSeparation; }
  public void setRequireRoleSeparation(boolean requireRoleSeparation) { this.requireRoleSeparation = requireRoleSeparation; }
  public Instant getEffectiveFrom() { return effectiveFrom; }
  public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
  public Instant getEffectiveTo() { return effectiveTo; }
  public void setEffectiveTo(Instant effectiveTo) { this.effectiveTo = effectiveTo; }
  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public List<ApprovalTierEntity> getTiers() { return tiers; }
  public void setTiers(List<ApprovalTierEntity> tiers) { this.tiers = tiers; }
}
