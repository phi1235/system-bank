package com.banksystem.customer.application.mapper;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.domain.customer.CustomerEntity;
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
