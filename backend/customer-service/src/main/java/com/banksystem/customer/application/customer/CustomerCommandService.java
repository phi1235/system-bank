package com.banksystem.customer.application.customer;

import com.banksystem.customer.api.dto.CustomerDtos.*;
import java.util.UUID;

public interface CustomerCommandService {
  CustomerResponse create(UUID userId, CreateProfileRequest req);
  CustomerResponse updateMe(UUID userId, UpdateProfileRequest req);
  CustomerResponse updateKyc(UUID id, KycUpdateRequest req);
}
