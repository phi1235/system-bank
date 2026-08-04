package com.banksystem.transaction.domain.bill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bill_providers")
public class BillProviderEntity {

  @Id
  private String id;

  @Column(name = "category_id", nullable = false, length = 50)
  private String categoryId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private boolean active = true;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }

  public String getCategoryId() { return categoryId; }
  public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
