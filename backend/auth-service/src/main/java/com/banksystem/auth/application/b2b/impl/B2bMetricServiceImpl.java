package com.banksystem.auth.application.b2b.impl;

import com.banksystem.auth.api.dto.B2bDtos.B2bMetricSummary;
import com.banksystem.auth.application.b2b.B2bMetricService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class B2bMetricServiceImpl implements B2bMetricService {

  @Override
  public List<B2bMetricSummary> getMetrics() {
    return List.of(
        new B2bMetricSummary("client_misa_erp_prod", 14200, 14165, 35, 42.5, 300),
        new B2bMetricSummary("client_sap_s4hana_hub", 8500, 8490, 10, 38.2, 600)
    );
  }
}
