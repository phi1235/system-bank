package com.banksystem.corporate.domain.corporation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "corporations")
public class CorporationEntity {

  @Id
  private UUID id;

  @Column(name = "tax_id", nullable = false, unique = true, length = 50)
  private String taxId;

  @Column(name = "company_name", nullable = false)
  private String companyName;

  @Column(name = "short_name", length = 100)
  private String shortName;

  @Column(name = "kyc_status", nullable = false, length = 30)
  private String kycStatus = "VERIFIED";

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "contact_email", length = 160)
  private String contactEmail;

  @Column(name = "contact_phone", length = 50)
  private String contactPhone;

  @Column(length = 255)
  private String address;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "corporation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<CorporateMembershipEntity> memberships = new ArrayList<>();

  @OneToMany(mappedBy = "corporation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<CorporateAccountEntity> accounts = new ArrayList<>();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getTaxId() { return taxId; }
  public void setTaxId(String taxId) { this.taxId = taxId; }
  public String getCompanyName() { return companyName; }
  public void setCompanyName(String companyName) { this.companyName = companyName; }
  public String getShortName() { return shortName; }
  public void setShortName(String shortName) { this.shortName = shortName; }
  public String getKycStatus() { return kycStatus; }
  public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getContactEmail() { return contactEmail; }
  public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
  public String getContactPhone() { return contactPhone; }
  public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public List<CorporateMembershipEntity> getMemberships() { return memberships; }
  public void setMemberships(List<CorporateMembershipEntity> memberships) { this.memberships = memberships; }
  public List<CorporateAccountEntity> getAccounts() { return accounts; }
  public void setAccounts(List<CorporateAccountEntity> accounts) { this.accounts = accounts; }
}
