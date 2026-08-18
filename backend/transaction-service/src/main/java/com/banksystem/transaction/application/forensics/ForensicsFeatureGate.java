package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ForensicsFeatureGate {
  private final ForensicsFeatureProperties properties;

  public ForensicsFeatureGate(ForensicsFeatureProperties properties) {
    this.properties = properties;
  }

  public boolean isEnabled() { return properties.isEnabled(); }

  public void requireEnabled() {
    if (!isEnabled()) {
      throw new BusinessException(
          "FORENSICS_DISABLED", "Financial forensics is disabled", HttpStatus.SERVICE_UNAVAILABLE);
    }
  }
}
