package com.banksystem.auth.api;

import com.banksystem.auth.domain.UserRepository;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.common.security.SecurityHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

  private final UserRepository userRepository;
  private final String apiKey;

  public InternalUserController(
      UserRepository userRepository,
      @Value("${bank.internal.api-key:internal-dev-key}") String apiKey) {
    this.userRepository = userRepository;
    this.apiKey = apiKey;
  }

  public record InternalUserCountsResponse(
      long users,
      long usersLocked
  ) {}

  @GetMapping("/counts")
  public ApiResponse<InternalUserCountsResponse> counts(
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key) {
    requireKey(key);
    long users = userRepository.count();
    long usersLocked = userRepository.countByEnabled(false);
    return ApiResponse.ok(new InternalUserCountsResponse(users, usersLocked));
  }

  private void requireKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
  }
}
