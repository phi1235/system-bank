package com.banksystem.customer.application.mapper;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

  public CustomerResponse toResponse(CustomerEntity e, String maskedNationalId) {
    return new CustomerResponse(
        e.getId().toString(),
        e.getFullName(),
        e.getPhone(),
        e.getEmail(),
        maskedNationalId,
        e.getKycStatus(),
        e.getAddress()
    );
  }
}
