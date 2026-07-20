package com.banksystem.transaction.application;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.CreateBeneficiaryRequest;
import com.banksystem.transaction.api.dto.BeneficiaryDtos.UpdateBeneficiaryRequest;
import com.banksystem.transaction.domain.BeneficiaryEntity;
import com.banksystem.transaction.domain.BeneficiaryRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import feign.FeignException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {

  private final BeneficiaryRepository beneficiaryRepository;
  private final AccountClient accountClient;
  private final String internalApiKey;

  public BeneficiaryService(
      BeneficiaryRepository beneficiaryRepository,
      AccountClient accountClient,
      @Value("${bank.internal.account-api-key}") String internalApiKey) {
    this.beneficiaryRepository = beneficiaryRepository;
    this.accountClient = accountClient;
    this.internalApiKey = internalApiKey;
  }

  @Transactional(readOnly = true)
  public List<BeneficiaryResponse> listMine(UUID userId) {
    return beneficiaryRepository.findByUserIdAndActiveTrueOrderByNicknameAsc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public BeneficiaryResponse create(UUID userId, CreateBeneficiaryRequest req) {
    String nickname = normalizeNickname(req.nickname());
    String accountNumber = normalizeAccountNumber(req.accountNumber());

    if (beneficiaryRepository.existsByUserIdAndAccountNumber(userId, accountNumber)) {
      throw new BusinessException(
          "BENEFICIARY_EXISTS",
          "Beneficiary with this account number already exists",
          HttpStatus.CONFLICT);
    }

    AccountView destination = loadActiveDestination(accountNumber);
    // Prevent saving own account as beneficiary of itself via same owner check is not always available;
    // account ownership is validated at transfer time for source. Destination must simply exist + ACTIVE.

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
      return toResponse(beneficiaryRepository.save(entity));
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(
          "BENEFICIARY_EXISTS",
          "Beneficiary with this account number already exists",
          HttpStatus.CONFLICT);
    }
  }

  @Transactional
  public BeneficiaryResponse rename(UUID userId, UUID id, UpdateBeneficiaryRequest req) {
    BeneficiaryEntity entity = requireOwned(userId, id);
    entity.setNickname(normalizeNickname(req.nickname()));
    entity.setUpdatedAt(Instant.now());
    return toResponse(beneficiaryRepository.save(entity));
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
  }

  private BeneficiaryEntity requireOwned(UUID userId, UUID id) {
    return beneficiaryRepository.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new BusinessException(
            "BENEFICIARY_NOT_FOUND",
            "Beneficiary not found",
            HttpStatus.NOT_FOUND));
  }

  private AccountView loadActiveDestination(String accountNumber) {
    try {
      ApiResponse<AccountView> res = accountClient.getByNumber(accountNumber, internalApiKey);
      if (res == null || !res.success() || res.data() == null) {
        throw new BusinessException(
            "ACCOUNT_NOT_FOUND",
            "Destination account not found",
            HttpStatus.NOT_FOUND);
      }
      AccountView account = res.data();
      if (!"ACTIVE".equals(account.status())) {
        throw new BusinessException(
            "ACCOUNT_FROZEN",
            "Destination account is not active",
            HttpStatus.UNPROCESSABLE_ENTITY);
      }
      return account;
    } catch (FeignException.NotFound e) {
      throw new BusinessException(
          "ACCOUNT_NOT_FOUND",
          "Destination account not found",
          HttpStatus.NOT_FOUND);
    } catch (BusinessException e) {
      throw e;
    } catch (FeignException e) {
      throw new BusinessException(
          "ACCOUNT_SERVICE_ERROR",
          "Account service unavailable",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private String normalizeNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new BusinessException("INVALID_NICKNAME", "Nickname is required", HttpStatus.BAD_REQUEST);
    }
    String trimmed = nickname.trim();
    if (trimmed.length() > 80) {
      throw new BusinessException("INVALID_NICKNAME", "Nickname too long", HttpStatus.BAD_REQUEST);
    }
    return trimmed;
  }

  private String normalizeAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.isBlank()) {
      throw new BusinessException("INVALID_ACCOUNT", "Account number is required", HttpStatus.BAD_REQUEST);
    }
    String normalized = accountNumber.trim();
    if (!normalized.matches("\\d{8,14}")) {
      throw new BusinessException(
          "INVALID_ACCOUNT",
          "Account number must be 8-14 digits",
          HttpStatus.BAD_REQUEST);
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
