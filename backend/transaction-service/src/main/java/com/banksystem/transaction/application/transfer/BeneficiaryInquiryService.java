package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.transfer.BankDirectoryEntity;
import com.banksystem.transaction.domain.transfer.BankDirectoryRepository;
import com.banksystem.transaction.domain.transfer.BeneficiaryInquiryRecordEntity;
import com.banksystem.transaction.domain.transfer.BeneficiaryInquiryRecordRepository;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityEntity;
import com.banksystem.transaction.domain.transfer.ProviderBankCapabilityRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryInquiryService {

  private static final Logger log = LoggerFactory.getLogger(BeneficiaryInquiryService.class);
  public record BankInfo(String bin, String code, String shortName) implements Serializable {}

  public record InquiryRequest(
      String bankBin,
      String bankCode,
      @NotBlank String accountNumber
  ) implements Serializable {}

  public record InquiryResponse(
      String inquiryId,
      BankInfo bank,
      String accountNumberMasked,
      String accountName,
      String accountType,
      String status,
      String provider,
      Instant verifiedAt,
      Instant expiresAt
  ) implements Serializable {}

  public record VerifiedBinding(
      String inquiryId,
      String bankBin,
      String bankCode,
      String accountName,
      String provider
  ) {}

  private final BeneficiaryInquiryPort inquiryPort;
  private final AccountGateway accountGateway;
  private final BankDirectoryRepository bankDirectoryRepository;
  private final ProviderBankCapabilityRepository capabilityRepository;
  private final BeneficiaryInquiryRecordRepository inquiryRecordRepository;
  private final BeneficiaryCryptoService cryptoService;
  private final Duration inquiryTtl;
  private final String configuredProvider;
  private final String providerIdentity;
  private final String payoutProvider;

  public BeneficiaryInquiryService(
      BeneficiaryInquiryPort inquiryPort,
      AccountGateway accountGateway,
      BankDirectoryRepository bankDirectoryRepository,
      ProviderBankCapabilityRepository capabilityRepository,
      BeneficiaryInquiryRecordRepository inquiryRecordRepository,
      BeneficiaryCryptoService cryptoService,
      @Value("${bank.inquiry.ttl}") Duration inquiryTtl,
      @Value("${bank.inquiry.provider}") String configuredProvider,
      @Value("${bank.napas.provider}") String napasProvider) {
    this.inquiryPort = inquiryPort;
    this.accountGateway = accountGateway;
    this.bankDirectoryRepository = bankDirectoryRepository;
    this.capabilityRepository = capabilityRepository;
    this.inquiryRecordRepository = inquiryRecordRepository;
    this.cryptoService = cryptoService;
    this.inquiryTtl = inquiryTtl;
    this.configuredProvider = (configuredProvider != null ? configuredProvider : "MOCK").trim().toUpperCase(Locale.ROOT);
    this.providerIdentity = "EXTERNAL".equals(this.configuredProvider) ? "VIETQR" : this.configuredProvider;
    this.payoutProvider = "http".equalsIgnoreCase(napasProvider == null ? "" : napasProvider.trim())
        ? "NAPAS" : "MOCK_NAPAS";
  }

  @Transactional
  public InquiryResponse inquire(UUID currentUserId, InquiryRequest request) {
    return inquire(currentUserId, BeneficiaryInquiryQuery.of(request));
  }

  @Transactional
  public InquiryResponse inquire(UUID currentUserId, BeneficiaryInquiryQuery query) {
    if (currentUserId == null) {
      throw new BusinessException("UNAUTHORIZED", "Authenticated user identity is required for inquiry");
    }
    if (query == null) {
      throw new BusinessException("INVALID_ACCOUNT", "Inquiry query is required");
    }
    String rawBin = query.bankIdentifier();
    String rawAccNum = query.accountNumber();

    // 1. Resolve Bank Info from Directory
    BankDirectoryEntity bank = resolveBank(rawBin);
    String resolvedBin = bank.getBin();
    boolean isInternal = "970499".equals(resolvedBin) || "SYSTEM_BANK".equalsIgnoreCase(bank.getCode());

    String accountName;
    String provider;
    String accountType;

    // 2. Perform Resolution (Internal vs External)
    if (isInternal) {
      accountType = "INTERNAL";
      provider = "SYSTEM";
      try {
        AccountView acc = accountGateway.getAccountByNumber(rawAccNum);
        if (acc == null) {
          throw new BusinessException("BENEFICIARY_NOT_FOUND", "Internal account not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(acc.status())) {
          throw new BusinessException("ACCOUNT_FROZEN", "Internal account is not active");
        }
        accountName = "TK KH (" + acc.accountNumber().substring(Math.max(0, acc.accountNumber().length() - 4)) + ")";
      } catch (BusinessException be) {
        throw be;
      } catch (Exception ex) {
        log.warn("Internal beneficiary lookup failed for account={}: {}",
            cryptoService.maskAccountNumber(rawAccNum), ex.getClass().getSimpleName());
        throw new BusinessException("BENEFICIARY_NOT_FOUND", "Internal account not found");
      }
    } else {
      accountType = "INTERBANK";
      provider = providerIdentity;

      // VietQR directory metadata only authorizes lookup; it never authorizes payout.
      if (!bank.isLookupSupported()) {
        throw new BusinessException("BANK_NOT_SUPPORTED", "Bank does not support real-time 24/7 lookup");
      }
      ProviderBankCapabilityEntity inquiryCapability = capabilityRepository
          .findByProviderAndBankBin(providerIdentity, resolvedBin)
          .orElseThrow(() -> new BusinessException(
              "BANK_NOT_SUPPORTED", "Configured provider has no lookup capability for this bank"));
      if (!inquiryCapability.isInquirySupported()
          || !"ACTIVE".equalsIgnoreCase(inquiryCapability.getStatus())) {
        throw new BusinessException("BANK_NOT_SUPPORTED", "Bank lookup is not active at the configured provider");
      }
      if (!inquiryPort.supports(resolvedBin)) {
        throw new BusinessException("BANK_NOT_SUPPORTED", "Configured provider does not support this bank");
      }

      BeneficiaryInquiryPort.InquiryResult result;
      try {
        result = inquiryPort.inquire(resolvedBin, rawAccNum);
      } catch (RuntimeException ex) {
        log.warn("Beneficiary provider unavailable for bankBin={}: {}", resolvedBin, ex.getMessage());
        if (ex instanceof RequestNotPermitted) {
          throw new BusinessException(
              "INQUIRY_RATE_LIMITED", "Too many beneficiary verification requests", HttpStatus.TOO_MANY_REQUESTS);
        }
        throw new BusinessException(
            "BENEFICIARY_INQUIRY_UNAVAILABLE",
            "Beneficiary verification is temporarily unavailable",
            HttpStatus.SERVICE_UNAVAILABLE);
      }
      if (!result.verified() || result.accountName() == null || result.accountName().isBlank()) {
        String errCode = result.errorCode() != null ? result.errorCode() : "BENEFICIARY_NOT_FOUND";
        String errMsg = result.errorMessage() != null ? result.errorMessage() : "Beneficiary account not found";
        HttpStatus status = "INQUIRY_RATE_LIMITED".equals(errCode)
            ? HttpStatus.TOO_MANY_REQUESTS
            : null;
        throw new BusinessException(errCode, errMsg, status);
      }
      accountName = result.accountName();
      provider = result.provider() != null ? result.provider() : "EXTERNAL";
      if (!providerIdentity.equalsIgnoreCase(provider)) {
        throw new BusinessException(
            "BENEFICIARY_INQUIRY_UNAVAILABLE",
            "Provider identity did not match configuration",
            HttpStatus.SERVICE_UNAVAILABLE);
      }
    }

    // 3. Create Immutable Server-Side Snapshot
    String inquiryId = "inq_" + UUID.randomUUID().toString().replace("-", "");
    String encryptedAccount = cryptoService.encryptEnvelope(rawAccNum);
    String hmacAccount = cryptoService.computeHmac(rawAccNum);
    Instant now = Instant.now();
    Instant expiresAt = now.plus(inquiryTtl);

    BeneficiaryInquiryRecordEntity record = new BeneficiaryInquiryRecordEntity(
        UUID.randomUUID(),
        inquiryId,
        currentUserId,
        resolvedBin,
        encryptedAccount,
        hmacAccount,
        accountName,
        accountType,
        "VERIFIED",
        provider,
        1,
        now,
        expiresAt
    );
    inquiryRecordRepository.save(record);

    // 4. Return Standardized DTO
    BankInfo bankInfo = new BankInfo(bank.getBin(), bank.getCode(), bank.getShortName());
    String maskedAcc = cryptoService.maskAccountNumber(rawAccNum);

    return new InquiryResponse(
        inquiryId,
        bankInfo,
        maskedAcc,
        accountName,
        accountType,
        "VERIFIED",
        provider,
        now,
        expiresAt
    );
  }

  @Transactional(readOnly = true)
  public VerifiedBinding validateForTransfer(
      UUID currentUserId,
      String inquiryId,
      String bankIdentifier,
      String accountNumber) {
    if (inquiryId == null || inquiryId.isBlank()) {
      throw new BusinessException("BENEFICIARY_INQUIRY_REQUIRED", "A verified beneficiary inquiry is required");
    }
    if (accountNumber == null || accountNumber.isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required");
    }

    Instant now = Instant.now();
    BeneficiaryInquiryRecordEntity record = inquiryRecordRepository.findByInquiryId(inquiryId.trim())
        .orElseThrow(() -> new BusinessException("BENEFICIARY_INQUIRY_INVALID", "Beneficiary inquiry was not found"));
    if (!record.getUserId().equals(currentUserId)) {
      throw new BusinessException("BENEFICIARY_INQUIRY_INVALID", "Beneficiary inquiry does not belong to the current user");
    }
    if (!"VERIFIED".equals(record.getStatus()) || record.getConsumedAt() != null) {
      throw new BusinessException("BENEFICIARY_INQUIRY_CONSUMED", "Beneficiary inquiry has already been used");
    }
    if (!record.getExpiresAt().isAfter(now)) {
      throw new BusinessException("BENEFICIARY_INQUIRY_EXPIRED", "Beneficiary inquiry has expired");
    }
    if (!"INTERBANK".equals(record.getAccountType())) {
      throw new BusinessException("INVALID_TRANSFER_TYPE", "Inquiry is not valid for an interbank transfer");
    }

    BankDirectoryEntity bank = resolveBank(bankIdentifier);
    if (!record.getBankBin().equals(bank.getBin())) {
      throw new BusinessException("BENEFICIARY_INQUIRY_MISMATCH", "Beneficiary bank does not match the verified inquiry");
    }
    String normalizedAccount = BeneficiaryInquiryQuery.normalizeAccountNumber(accountNumber);
    if (!cryptoService.matchesHmac(normalizedAccount, record.getAccountNumberHmac())) {
      throw new BusinessException("BENEFICIARY_INQUIRY_MISMATCH", "Beneficiary account does not match the verified inquiry");
    }

    ProviderBankCapabilityEntity capability = capabilityRepository
        .findByProviderAndBankBin(payoutProvider, record.getBankBin())
        .orElseThrow(() -> new BusinessException("BANK_NOT_SUPPORTED", "Provider capability is unavailable"));
    if (!capability.isPayoutSupported() || !"ACTIVE".equalsIgnoreCase(capability.getStatus())) {
      throw new BusinessException("BANK_NOT_SUPPORTED", "Bank does not currently support interbank payout");
    }
    return new VerifiedBinding(
        record.getInquiryId(), record.getBankBin(), bank.getCode(),
        record.getProviderAccountName(), record.getProvider());
  }

  public void consumeForTransfer(UUID currentUserId, String inquiryId) {
    int updated = inquiryRecordRepository.atomicConsume(inquiryId, currentUserId, Instant.now());
    if (updated != 1) {
      throw new BusinessException("BENEFICIARY_INQUIRY_CONSUMED", "Beneficiary inquiry is expired or already used");
    }
  }

  private BankDirectoryEntity resolveBank(String binOrCode) {
    if (binOrCode == null || binOrCode.isBlank()) {
      throw new BusinessException("TARGET_BANK_REQUIRED", "Beneficiary bank is required");
    }
    Optional<BankDirectoryEntity> byBin = bankDirectoryRepository.findByBin(binOrCode);
    if (byBin.isPresent()) {
      return requireActive(byBin.get());
    }
    Optional<BankDirectoryEntity> byCode = bankDirectoryRepository.findByCodeIgnoreCase(binOrCode);
    if (byCode.isPresent()) {
      return requireActive(byCode.get());
    }
    throw new BusinessException("BANK_NOT_SUPPORTED", "Beneficiary bank is not in the active bank directory");
  }

  private BankDirectoryEntity requireActive(BankDirectoryEntity bank) {
    if (!bank.isActive()) {
      throw new BusinessException("BANK_NOT_SUPPORTED", "Beneficiary bank is inactive");
    }
    return bank;
  }
}
