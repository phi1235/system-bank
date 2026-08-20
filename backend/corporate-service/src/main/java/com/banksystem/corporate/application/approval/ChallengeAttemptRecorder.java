package com.banksystem.corporate.application.approval;

import com.banksystem.corporate.domain.approval.SigningChallengeEntity;
import com.banksystem.corporate.domain.approval.SigningChallengeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeAttemptRecorder {

  private static final int MAX_ATTEMPTS = 5;

  private final SigningChallengeRepository challengeRepository;

  public ChallengeAttemptRecorder(SigningChallengeRepository challengeRepository) {
    this.challengeRepository = challengeRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int recordFailedAttempt(UUID challengeId) {
    challengeRepository.incrementFailedAttempt(challengeId, MAX_ATTEMPTS);
    return challengeRepository.findById(challengeId)
        .map(SigningChallengeEntity::getAttemptCount)
        .orElse(0);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean redeem(UUID challengeId, Instant now) {
    return challengeRepository.redeem(challengeId, now, MAX_ATTEMPTS) == 1;
  }
}
