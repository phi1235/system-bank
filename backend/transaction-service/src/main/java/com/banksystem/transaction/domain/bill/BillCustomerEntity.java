package com.banksystem.transaction.domain.bill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bill_customers")
public class BillCustomerEntity {

  @Id
  private UUID id;

  @Column(name = "provider_id", nullable = false, length = 50)
  private String providerId;

  @Column(name = "customer_code", nullable = false, length = 100)
  private String customerCode;

  @Column(name = "customer_name", nullable = false, length = 150)
  private String customerName;

  @Column(length = 255)
  private String address;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 50)
  private String period;

  @Column(length = 20)
  private String status = "UNPAID";

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getProviderId() { return providerId; }
  public void setProviderId(String providerId) { this.providerId = providerId; }

  public String getCustomerCode() { return customerCode; }
  public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

  public String getCustomerName() { return customerName; }
  public void setCustomerName(String customerName) { this.customerName = customerName; }

  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }

  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }

  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
