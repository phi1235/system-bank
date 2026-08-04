package com.banksystem.customer.application.dashboard;

import com.banksystem.customer.api.dto.DashboardDtos.DashboardSummaryResponse;

public interface DashboardQueryService {
  DashboardSummaryResponse getSummary();
}
