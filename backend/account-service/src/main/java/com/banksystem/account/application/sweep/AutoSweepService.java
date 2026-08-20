package com.banksystem.account.application.sweep;

import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepOperationResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepProfileResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.SweepProductResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.UpsertAutoSweepRequest;
import com.banksystem.account.application.sweep.AutoSweepQueries.ListQuery;
import com.banksystem.account.application.sweep.AutoSweepQueries.OperationsQuery;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.sweep.AutoSweepOperationEntity;
import com.banksystem.account.domain.sweep.AutoSweepOperationRepository;
import com.banksystem.account.domain.sweep.AutoSweepPositionEntity;
import com.banksystem.account.domain.sweep.AutoSweepPositionRepository;
import com.banksystem.account.domain.sweep.AutoSweepProfileEntity;
import com.banksystem.account.domain.sweep.AutoSweepProfileRepository;
import com.banksystem.account.domain.sweep.SweepProductEntity;
import com.banksystem.account.domain.sweep.SweepProductRepository;
import com.banksystem.account.infrastructure.sweep.AutoSweepProfileQueryRepository;
import com.banksystem.account.infrastructure.sweep.AutoSweepProfileQueryRepository.ProfileRow;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutoSweepService {
  private final AccountRepository accountRepository;
  private final AutoSweepProfileRepository profileRepository;
  private final AutoSweepPositionRepository positionRepository;
  private final AutoSweepOperationRepository operationRepository;
  private final SweepProductRepository productRepository;
  private final AutoSweepProfileQueryRepository queryRepository;
  private final Clock clock;
  private final ZoneId zone;

  public AutoSweepService(
      AccountRepository accountRepository,
      AutoSweepProfileRepository profileRepository,
      AutoSweepPositionRepository positionRepository,
      AutoSweepOperationRepository operationRepository,
      SweepProductRepository productRepository,
      AutoSweepProfileQueryRepository queryRepository,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone) {
    this.accountRepository = accountRepository;
    this.profileRepository = profileRepository;
    this.positionRepository = positionRepository;
    this.operationRepository = operationRepository;
    this.productRepository = productRepository;
    this.queryRepository = queryRepository;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  @Transactional(readOnly = true)
  public List<SweepProductResponse> products() {
    return productRepository.findByActiveTrueOrderByCodeAsc().stream()
        .map(product -> new SweepProductResponse(
            product.getCode(), product.getCurrency(), product.getAnnualRateBps(),
            product.getMinThreshold(), product.getDefaultThreshold(), product.getMinSweepAmount(),
            product.getMaxPositionAmount()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AutoSweepProfileResponse> listMine(UUID userId) {
    return queryRepository.findByUserId(userId).stream().map(this::response).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<AutoSweepProfileResponse> listAll(ListQuery query) {
    var result = queryRepository.findAll(query.page(), query.size());
    int totalPages = (int) Math.ceil((double) result.totalElements() / query.size());
    return new PageResponse<>(result.items().stream().map(this::response).toList(),
        query.page(), query.size(), result.totalElements(), totalPages);
  }

  @Transactional
  public AutoSweepProfileResponse upsert(
      UUID sourceAccountId, UpsertAutoSweepRequest request, GatewayUser user) {
    AccountEntity account = accountRepository.findByIdForUpdate(sourceAccountId)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    requireOwner(account, user);
    if (!"PAYMENT".equalsIgnoreCase(account.getAccountType())) {
      throw new BusinessException("AUTO_SWEEP_PAYMENT_ACCOUNT_REQUIRED", "Auto-sweep requires a PAYMENT account");
    }
    if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
      throw new BusinessException("ACCOUNT_FROZEN", "Account must be active to configure auto-sweep");
    }
    SweepProductEntity product = productRepository.findById(request.productCode())
        .filter(SweepProductEntity::isActive)
        .orElseThrow(() -> new BusinessException("SWEEP_PRODUCT_NOT_FOUND", "Sweep product is not active"));
    if (!product.getCurrency().equalsIgnoreCase(account.getCurrency())) {
      throw new BusinessException("CURRENCY_MISMATCH", "Sweep product currency does not match account");
    }
    if (request.thresholdAmount().compareTo(product.getMinThreshold()) < 0) {
      throw new BusinessException(
          "SWEEP_THRESHOLD_TOO_LOW",
          "Threshold must be at least " + product.getMinThreshold().toPlainString());
    }
    BigDecimal minSweep = request.minSweepAmount() == null
        ? product.getMinSweepAmount() : request.minSweepAmount();
    if (minSweep.compareTo(product.getMinSweepAmount()) < 0) {
      throw new BusinessException(
          "SWEEP_AMOUNT_TOO_LOW",
          "Minimum sweep amount must be at least " + product.getMinSweepAmount().toPlainString());
    }

    AutoSweepProfileEntity profile = profileRepository
        .findBySourceAccountIdForUpdate(sourceAccountId).orElse(null);
    var now = clock.instant();
    if (profile == null) {
      profile = new AutoSweepProfileEntity();
      profile.setId(UUID.randomUUID());
      profile.setUserId(account.getUserId());
      profile.setSourceAccountId(account.getId());
      profile.setStatus("ENABLED");
      profile.setCreatedAt(now);
    } else if (request.version() != null && profile.getVersion() != request.version()) {
      throw new BusinessException("AUTO_SWEEP_VERSION_CONFLICT", "Auto-sweep settings changed; reload and try again");
    }
    profile.setProductCode(product.getCode());
    profile.setThresholdAmount(request.thresholdAmount());
    profile.setMinSweepAmount(minSweep);
    profile.setStatus("ENABLED");
    profile.setUpdatedAt(now);
    profile = profileRepository.saveAndFlush(profile);

    if (positionRepository.findByProfileIdForUpdate(profile.getId()).isEmpty()) {
      AutoSweepPositionEntity position = new AutoSweepPositionEntity();
      position.setId(UUID.randomUUID());
      position.setProfileId(profile.getId());
      position.setSourceAccountId(account.getId());
      position.setCurrency(account.getCurrency());
      position.setPrincipalBalance(BigDecimal.ZERO.setScale(2));
      position.setAccruedInterest(BigDecimal.ZERO.setScale(2));
      position.setLastAccrualDate(LocalDate.now(clock.withZone(zone)));
      position.setCreatedAt(now);
      position.setUpdatedAt(now);
      positionRepository.saveAndFlush(position);
    }
    return response(profile.getId());
  }

  @Transactional
  public AutoSweepProfileResponse setEnabled(UUID sourceAccountId, boolean enabled, GatewayUser user) {
    AutoSweepProfileEntity snapshot = profileRepository.findBySourceAccountId(sourceAccountId)
        .orElseThrow(() -> new BusinessException("AUTO_SWEEP_NOT_FOUND", "Auto-sweep is not configured"));
    AccountEntity account = accountRepository.findByIdForUpdate(snapshot.getSourceAccountId())
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    requireOwner(account, user);
    AutoSweepProfileEntity profile = profileRepository.findBySourceAccountIdForUpdate(sourceAccountId)
        .orElseThrow(() -> new BusinessException("AUTO_SWEEP_NOT_FOUND", "Auto-sweep is not configured"));
    profile.setStatus(enabled ? "ENABLED" : "PAUSED");
    profile.setUpdatedAt(clock.instant());
    return response(profileRepository.saveAndFlush(profile).getId());
  }

  @Transactional(readOnly = true)
  public List<AutoSweepOperationResponse> operations(
      UUID sourceAccountId, UUID userId, OperationsQuery query) {
    AutoSweepProfileEntity profile = profileRepository.findBySourceAccountId(sourceAccountId)
        .orElseThrow(() -> new BusinessException("AUTO_SWEEP_NOT_FOUND", "Auto-sweep is not configured"));
    if (!profile.getUserId().equals(userId)) {
      throw new BusinessException("FORBIDDEN", "Not your auto-sweep profile");
    }
    return operationRepository.findByProfileIdOrderByCreatedAtDesc(
            profile.getId(), PageRequest.of(0, query.limit()))
        .stream().map(this::operationResponse).toList();
  }

  private AutoSweepProfileResponse response(UUID profileId) {
    return response(queryRepository.findById(profileId)
        .orElseThrow(() -> new BusinessException(
            "AUTO_SWEEP_NOT_FOUND", "Auto-sweep profile not found")));
  }

  private AutoSweepProfileResponse response(ProfileRow profile) {
    return new AutoSweepProfileResponse(
        profile.id(), profile.sourceAccountId(), profile.sourceAccountNumber(), profile.productCode(),
        profile.status(), profile.thresholdAmount(), profile.minSweepAmount(),
        profile.annualRateBps(), profile.casaBalance(), profile.availableBalance(),
        profile.flexiblePrincipal(), profile.accruedInterest(), profile.totalLiquidity(),
        profile.lastSweepBusinessDate(), profile.version(), profile.updatedAt());
  }

  private AutoSweepOperationResponse operationResponse(AutoSweepOperationEntity operation) {
    return new AutoSweepOperationResponse(
        operation.getId(), operation.getOperationType(), operation.getTriggerType(),
        operation.getAmount(), operation.getAnnualRateBps(), operation.getBusinessDate(),
        operation.getPaymentReference(), operation.getCasaBalanceAfter(),
        operation.getPositionBalanceAfter(), operation.getCreatedAt());
  }

  private void requireOwner(AccountEntity account, GatewayUser user) {
    if (!account.getUserId().equals(user.userId())) {
      throw new BusinessException("FORBIDDEN", "Not your account");
    }
  }
}
