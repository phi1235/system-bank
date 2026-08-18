package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.application.forensics.ForensicsFeatureGate;
import com.banksystem.transaction.api.dto.ForensicDtos.ForensicsCapabilitiesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/capabilities")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
public class AdminForensicsCapabilitiesController {
  private final ForensicsFeatureGate featureGate;

  public AdminForensicsCapabilitiesController(ForensicsFeatureGate featureGate) {
    this.featureGate = featureGate;
  }

  @GetMapping
  public ApiResponse<ForensicsCapabilitiesResponse> get() {
    return ApiResponse.ok(new ForensicsCapabilitiesResponse(featureGate.isEnabled()));
  }
}
