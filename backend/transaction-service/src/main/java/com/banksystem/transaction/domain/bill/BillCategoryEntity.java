package com.banksystem.transaction.domain.bill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bill_categories")
public class BillCategoryEntity {

  @Id
  private String id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "icon_url", length = 255)
  private String iconUrl;

  @Column(length = 50)
  private String icon;

  @Column(name = "sample_code", length = 100)
  private String sampleCode;

  @Column(name = "theme_class", length = 50)
  private String themeClass;

  @Column(name = "display_order")
  private int displayOrder;

  @Column(nullable = false)
  private boolean active = true;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getIconUrl() { return iconUrl; }
  public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

  public String getIcon() { return icon; }
  public void setIcon(String icon) { this.icon = icon; }

  public String getSampleCode() { return sampleCode; }
  public void setSampleCode(String sampleCode) { this.sampleCode = sampleCode; }

  public String getThemeClass() { return themeClass; }
  public void setThemeClass(String themeClass) { this.themeClass = themeClass; }

  public int getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
