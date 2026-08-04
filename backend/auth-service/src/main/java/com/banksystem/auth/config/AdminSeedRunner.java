package com.banksystem.auth.config;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Bootstrap first ADMIN from <strong>environment</strong> (not hardcoded secrets in source).
 * Disable with {@code bank.admin.seed-enabled=false} in non-demo environments after creating staff via API.
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

  private final UserRepository userRepository;
  private final BoundPasswordEncoder boundPasswordEncoder;
  private final boolean seedEnabled;
  private final String username;
  private final String password;
  private final String email;

  public AdminSeedRunner(
      UserRepository userRepository,
      BoundPasswordEncoder boundPasswordEncoder,
      @Value("${bank.admin.seed-enabled:true}") boolean seedEnabled,
      @Value("${bank.admin.username}") String username,
      @Value("${bank.admin.password}") String password,
      @Value("${bank.admin.email}") String email) {
    this.userRepository = userRepository;
    this.boundPasswordEncoder = boundPasswordEncoder;
    this.seedEnabled = seedEnabled;
    this.username = BoundPasswordEncoder.normalizeUsername(username);
    this.password = password;
    this.email = email;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!seedEnabled) {
      log.info("Admin seed disabled (bank.admin.seed-enabled=false)");
      return;
    }
    if (userRepository.existsByUsername(username)) {
      return;
    }
    UserEntity admin = new UserEntity();
    admin.setId(UUID.randomUUID());
    admin.setUsername(username);
    admin.setEmail(email);
    admin.setPasswordHash(boundPasswordEncoder.encode(password, username));
    admin.setRoles("SUPER_ADMIN");
    admin.setEnabled(true);
    admin.setMfaEnabled(false);
    admin.setCreatedAt(Instant.now());
    admin.setUpdatedAt(Instant.now());
    userRepository.save(admin);
    log.info("Seeded bootstrap SUPER_ADMIN username={} (password from env ADMIN_PASSWORD only)", username);
  }
}
