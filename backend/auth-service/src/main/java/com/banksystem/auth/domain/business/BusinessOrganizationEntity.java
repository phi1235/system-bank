package com.banksystem.auth.domain.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_organizations")
public class BusinessOrganizationEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "legal_name", nullable = false, length = 255)
  private String legalName;

  @Column(name = "tax_number", length = 50)
  private String taxNumber;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "kyc_status", nullable = false, length = 30)
  private String kycStatus = "PENDING_KYC";

  @Column(name = "short_name", length = 100)
  private String shortName;

  @Column(name = "contact_email", length = 160)
  private String contactEmail;

  @Column(name = "contact_phone", length = 50)
  private String contactPhone;

  @Column(length = 500)
  private String address;

  @Column(name = "representative_name", length = 255)
  private String representativeName;

  @Column(name = "business_license_url", length = 500)
  private String businessLicenseUrl;

  @Column(name = "id_card_url", length = 500)
  private String idCardUrl;

  @Column(length = 100)
  private String industry;

  @Column(name = "kyc_reviewed_by")
  private UUID kycReviewedBy;

  @Column(name = "kyc_reviewed_at")
  private Instant kycReviewedAt;

  @Column(name = "kyc_reject_reason", length = 500)
  private String kycRejectReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Factory: tạo org đã duyệt (dùng cho seed/admin tạo) */
  public static BusinessOrganizationEntity create(UUID id, String code, String legalName, String taxNumber, Instant now) {
    BusinessOrganizationEntity entity = new BusinessOrganizationEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.code = code.toUpperCase().trim();
    entity.legalName = legalName.trim();
    entity.taxNumber = taxNumber != null ? taxNumber.trim() : null;
    entity.status = "ACTIVE";
    entity.kycStatus = "APPROVED";
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  /** Factory: đăng ký mới — trạng thái chờ duyệt KYC */
  public static BusinessOrganizationEntity register(String code, String legalName, String taxNumber,
      String contactEmail, String contactPhone, String address, String representativeName,
      String industry, Instant now) {
    BusinessOrganizationEntity entity = new BusinessOrganizationEntity();
    entity.id = UUID.randomUUID();
    entity.code = code.toUpperCase().trim();
    entity.legalName = legalName.trim();
    entity.taxNumber = taxNumber != null ? taxNumber.trim() : null;
    entity.status = "ACTIVE";
    entity.kycStatus = "PENDING_KYC";
    entity.contactEmail = contactEmail;
    entity.contactPhone = contactPhone;
    entity.address = address;
    entity.representativeName = representativeName;
    entity.industry = industry;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public void approveKyc(UUID reviewedBy, Instant now) {
    this.kycStatus = "APPROVED";
    this.kycReviewedBy = reviewedBy;
    this.kycReviewedAt = now;
    this.kycRejectReason = null;
    this.updatedAt = now;
  }

  public void rejectKyc(UUID reviewedBy, String reason, Instant now) {
    this.kycStatus = "REJECTED";
    this.kycReviewedBy = reviewedBy;
    this.kycReviewedAt = now;
    this.kycRejectReason = reason;
    this.updatedAt = now;
  }

  public boolean isKycApproved() {
    return "APPROVED".equals(kycStatus);
  }

  // --- Getters & Setters ---

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getLegalName() { return legalName; }
  public void setLegalName(String legalName) { this.legalName = legalName; }
  public String getTaxNumber() { return taxNumber; }
  public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public String getKycStatus() { return kycStatus; }
  public void setKycStatus(String kycStatus) { this.kycStatus = kycStatus; }
  public String getShortName() { return shortName; }
  public void setShortName(String shortName) { this.shortName = shortName; }
  public String getContactEmail() { return contactEmail; }
  public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
  public String getContactPhone() { return contactPhone; }
  public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getRepresentativeName() { return representativeName; }
  public void setRepresentativeName(String representativeName) { this.representativeName = representativeName; }
  public String getBusinessLicenseUrl() { return businessLicenseUrl; }
  public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }
  public String getIdCardUrl() { return idCardUrl; }
  public void setIdCardUrl(String idCardUrl) { this.idCardUrl = idCardUrl; }
  public String getIndustry() { return industry; }
  public void setIndustry(String industry) { this.industry = industry; }
  public UUID getKycReviewedBy() { return kycReviewedBy; }
  public void setKycReviewedBy(UUID kycReviewedBy) { this.kycReviewedBy = kycReviewedBy; }
  public Instant getKycReviewedAt() { return kycReviewedAt; }
  public void setKycReviewedAt(Instant kycReviewedAt) { this.kycReviewedAt = kycReviewedAt; }
  public String getKycRejectReason() { return kycRejectReason; }
  public void setKycRejectReason(String kycRejectReason) { this.kycRejectReason = kycRejectReason; }
}

