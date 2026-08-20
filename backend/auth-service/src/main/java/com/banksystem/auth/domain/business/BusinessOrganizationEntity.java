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

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static BusinessOrganizationEntity create(UUID id, String code, String legalName, String taxNumber, Instant now) {
    BusinessOrganizationEntity entity = new BusinessOrganizationEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.code = code.toUpperCase().trim();
    entity.legalName = legalName.trim();
    entity.taxNumber = taxNumber != null ? taxNumber.trim() : null;
    entity.status = "ACTIVE";
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

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
}
