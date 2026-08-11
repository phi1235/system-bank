package com.banksystem.customer.application.customer;

import com.banksystem.common.api.PageResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNameResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerSearchFilterRequest;
import com.banksystem.customer.domain.customer.CustomerEntity;
import java.util.List;
import java.util.UUID;

public interface CustomerQueryService {
  CustomerResponse getMe(UUID userId);
  List<CustomerNameResponse> namesByIds(List<UUID> userIds);
  PageResponse<CustomerResponse> list(CustomerSearchFilterRequest req);
  PageResponse<CustomerResponse> list(CustomerSearchQuery query);
  boolean exists(UUID id);
  CustomerContactResult contact(UUID id);
  CustomerEntity require(UUID id);
}
