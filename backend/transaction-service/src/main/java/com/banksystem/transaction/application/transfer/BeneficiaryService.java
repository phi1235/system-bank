package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.CreateBeneficiaryRequest;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.UpdateBeneficiaryRequest;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.transfer.BeneficiaryEntity;
import com.banksystem.transaction.domain.transfer.BeneficiaryRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.redis.BeneficiaryListCache;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {

  private final BeneficiaryRepository beneficiaryRepository;
  private final AccountGateway accountGateway;
  private final BeneficiaryListCache listCache;

  public BeneficiaryService(
      BeneficiaryRepository beneficiaryRepository,
      AccountGateway accountGateway,
      BeneficiaryListCache listCache) {
    this.beneficiaryRepository = beneficiaryRepository;
    this.accountGateway = accountGateway;
    this.listCache = listCache;
  }

  @Transactional(readOnly = true)
  public List<BeneficiaryResponse> listMine(UUID userId) {
    return listCache.get(userId).orElseGet(() -> {
      List<BeneficiaryResponse> items =
          beneficiaryRepository.findByUserIdAndActiveTrueOrderByNicknameAsc(userId).stream()
              .map(this::toResponse)
              .toList();
      listCache.put(userId, items);
      return items;
    });
  }

  @Transactional
  public BeneficiaryResponse create(UUID userId, CreateBeneficiaryRequest req) {
    String nickname = normalizeNickname(req.nickname());
    String accountNumber = normalizeAccountNumber(req.accountNumber());

    if (beneficiaryRepository.existsByUserIdAndAccountNumber(userId, accountNumber)) {
      throw new BusinessException(
          "BENEFICIARY_EXISTS",
          "Beneficiary with this account number already exists");
    }

    AccountView destination = loadActiveDestination(accountNumber);

    BeneficiaryEntity entity = new BeneficiaryEntity();
    entity.setId(UUID.randomUUID());
    entity.setUserId(userId);
    entity.setNickname(nickname);
    entity.setAccountNumber(destination.accountNumber());
    entity.setAccountId(destination.idUuid());
    entity.setCurrency(destination.currency() == null ? "VND" : destination.currency());
    entity.setActive(true);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    try {
      BeneficiaryResponse saved = toResponse(beneficiaryRepository.save(entity));
      listCache.evict(userId);
      return saved;
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(
          "BENEFICIARY_EXISTS",
          "Beneficiary with this account number already exists");
    }
  }

  @Transactional
  public BeneficiaryResponse rename(UUID userId, UUID id, UpdateBeneficiaryRequest req) {
    BeneficiaryEntity entity = requireOwned(userId, id);
    entity.setNickname(normalizeNickname(req.nickname()));
    entity.setUpdatedAt(Instant.now());
    BeneficiaryResponse saved = toResponse(beneficiaryRepository.save(entity));
    listCache.evict(userId);
    return saved;
  }

  @Transactional
  public void deactivate(UUID userId, UUID id) {
    BeneficiaryEntity entity = requireOwned(userId, id);
    if (!entity.isActive()) {
      return;
    }
    entity.setActive(false);
    entity.setUpdatedAt(Instant.now());
    beneficiaryRepository.save(entity);
    listCache.evict(userId);
  }

  private BeneficiaryEntity requireOwned(UUID userId, UUID id) {
    return beneficiaryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(
            "BENEFICIARY_NOT_FOUND",
            "Beneficiary not found"));
  }

  private AccountView loadActiveDestination(String accountNumber) {
    try {
      AccountView account = accountGateway.getAccountByNumber(accountNumber);
      if (account == null) {
        throw new BusinessException(
            "ACCOUNT_NOT_FOUND",
            "Destination account not found");
      }
      if (!"ACTIVE".equals(account.status())) {
        throw new BusinessException(
            "ACCOUNT_FROZEN",
            "Destination account is not active");
      }
      return account;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          "ACCOUNT_SERVICE_ERROR",
          "Account service unavailable");
    }
  }

  private String normalizeNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new BusinessException("INVALID_NICKNAME", "Nickname is required");
    }
    String trimmed = nickname.trim();
    if (trimmed.length() > 80) {
      throw new BusinessException("INVALID_NICKNAME", "Nickname too long");
    }
    return trimmed;
  }

  private String normalizeAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required");
    }
    String normalized = accountNumber.trim();
    if (!normalized.matches("\\d{8,14}")) {
      throw new BusinessException(
          "INVALID_ACCOUNT",
          "Account number must be 8-14 digits");
    }
    return normalized;
  }

  private BeneficiaryResponse toResponse(BeneficiaryEntity e) {
    return new BeneficiaryResponse(
        e.getId().toString(),
        e.getNickname(),
        e.getAccountNumber(),
        e.getAccountId() == null ? null : e.getAccountId().toString(),
        e.getCurrency(),
        e.isActive(),
        e.getCreatedAt());
  }
}
