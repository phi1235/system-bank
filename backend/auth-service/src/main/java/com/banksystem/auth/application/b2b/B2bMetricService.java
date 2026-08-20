package com.banksystem.auth.application.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bMetricSummary;
import java.util.List;

public interface B2bMetricService {

  List<B2bMetricSummary> getMetrics();
}
