package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.customer.api.dto.DashboardDtos.DashboardSummaryResponse;
import com.banksystem.customer.application.query.DashboardQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

  private final DashboardQueryService dashboardQueryService;

  public AdminDashboardController(DashboardQueryService dashboardQueryService) {
    this.dashboardQueryService = dashboardQueryService;
  }

  @GetMapping("/summary")
  @RequirePermission("dashboard:view")
  public ApiResponse<DashboardSummaryResponse> getSummary() {
    return ApiResponse.ok(dashboardQueryService.getSummary());
  }
}
