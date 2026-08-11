package com.banksystem.auth.application.auth;

import com.banksystem.auth.api.dto.AuthDtos.InternalUserCountsResponse;
import com.banksystem.auth.domain.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalUserQueryService {

  private final UserRepository userRepository;

  public InternalUserQueryService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public InternalUserCountsResponse counts() {
    return new InternalUserCountsResponse(
        userRepository.count(),
        userRepository.countByEnabled(false));
  }
}
