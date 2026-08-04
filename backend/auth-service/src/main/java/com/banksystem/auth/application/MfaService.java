package com.banksystem.auth.application;

import com.banksystem.auth.domain.MfaSettingsEntity;
import com.banksystem.auth.domain.MfaSettingsRepository;
import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

  private final TokenStore tokenStore;
  private final MfaSettingsRepository mfaSettingsRepository;
  private final UserRepository userRepository;
  private final String aesKey;
  private final CodeVerifier verifier;

  public MfaService(
      TokenStore tokenStore,
      MfaSettingsRepository mfaSettingsRepository,
      UserRepository userRepository,
      @Value("${bank.aes.secret-key}") String aesKey) {
    this.tokenStore = tokenStore;
    this.mfaSettingsRepository = mfaSettingsRepository;
    this.userRepository = userRepository;
    this.aesKey = aesKey;
    TimeProvider timeProvider = new SystemTimeProvider();
    CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    DefaultCodeVerifier v = new DefaultCodeVerifier(codeGenerator, timeProvider);
    v.setTimePeriod(30);
    v.setAllowedTimePeriodDiscrepancy(1);
    this.verifier = v;
  }

  public MfaSetupResult setup(UserEntity user) {
    if (user.isMfaEnabled()) {
      throw new BusinessException("MFA_ALREADY_ENABLED", "MFA is already enabled");
    }
    String secret = new DefaultSecretGenerator().generate();
    tokenStore.storeMfaSetupSecret(user.getId(), secret, 10);
    String otpauth = new QrData.Builder()
        .label(user.getUsername())
        .secret(secret)
        .issuer("BankSystem")
        .algorithm(HashingAlgorithm.SHA1)
        .digits(6)
        .period(30)
        .build()
        .getUri();
    return new MfaSetupResult(otpauth, secret);
  }

  @Transactional
  public void enable(UserEntity user, String code) {
    String secret = tokenStore.getMfaSetupSecret(user.getId())
        .orElseThrow(() -> new BusinessException("MFA_SETUP_EXPIRED",
            "MFA setup expired; call setup again"));
    if (!verifyCode(secret, code)) {
      throw new BusinessException("INVALID_MFA_CODE", "Invalid MFA code");
    }
    MfaSettingsEntity settings = mfaSettingsRepository.findById(user.getId()).orElseGet(MfaSettingsEntity::new);
    settings.setUserId(user.getId());
    settings.setSecretEncrypted(CryptoUtils.encrypt(secret, aesKey));
    settings.setEnabledAt(Instant.now());
    mfaSettingsRepository.save(settings);

    user.setMfaEnabled(true);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    tokenStore.clearMfaSetupSecret(user.getId());
  }

  public boolean verifyUserCode(UUID userId, String code) {
    MfaSettingsEntity settings = mfaSettingsRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("MFA_NOT_CONFIGURED", "MFA not configured"));
    String secret = CryptoUtils.decrypt(settings.getSecretEncrypted(), aesKey);
    return verifyCode(secret, code);
  }

  private boolean verifyCode(String secret, String code) {
    if (code == null || !code.matches("\\d{6}")) {
      return false;
    }
    return verifier.isValidCode(secret, code);
  }

  /** Optional QR PNG base64 for future UI. */
  public String qrPngBase64(String otpauthUri) {
    try {
      QrData data = new QrData.Builder()
          .label("user")
          .secret("TEMP")
          .issuer("BankSystem")
          .build();
      // Use URI-based generation via Zxing with otpauth string as label payload — keep simple: skip for MVP
      byte[] image = new ZxingPngQrGenerator().generate(data);
      return Base64.getEncoder().encodeToString(image);
    } catch (QrGenerationException e) {
      return null;
    }
  }

  public record MfaSetupResult(String otpauthUri, String secret) {}
}
