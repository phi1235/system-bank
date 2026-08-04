package com.banksystem.account.application.gateway;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CustomerGateway {
  Map<String, String> getCustomerNames(List<UUID> userIds);
}
