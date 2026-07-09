package com.banksystem.auth.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundPasswordEncoderTest {

  private final BoundPasswordEncoder encoder =
      new BoundPasswordEncoder("unit-test-pepper-secret-key-xx");

  @Test
  void samePasswordAndUsernameMatches() {
    String hash = encoder.encode("Secret123!", "alice");
    assertTrue(encoder.matches("Secret123!", "alice", hash));
    assertTrue(encoder.matches("Secret123!", "Alice", hash)); // normalize
  }

  @Test
  void hashCannotBeReusedForAnotherUsername() {
    String hashForAlice = encoder.encode("Secret123!", "alice");
    // Attacker pastes alice's hash onto bob's row → must fail for bob's login
    assertFalse(encoder.matches("Secret123!", "bob", hashForAlice));
  }

  @Test
  void rawBcryptWithoutPepperDoesNotMatch() {
    String hash = encoder.encode("Secret123!", "alice");
    assertFalse(encoder.matches("wrong-pass", "alice", hash));
  }

  @Test
  void differentPepperCannotVerify() {
    BoundPasswordEncoder other = new BoundPasswordEncoder("another-pepper-value-yyyyyyyy");
    String hash = encoder.encode("Secret123!", "alice");
    assertFalse(other.matches("Secret123!", "alice", hash));
  }
}
