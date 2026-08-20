package com.banksystem.corporate.application.corporation;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.CorporateDtos.AddMemberRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporateAccountResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporateMemberResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CorporationResponse;
import com.banksystem.corporate.api.dto.CorporateDtos.CreateCorporationRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.LinkAccountRequest;
import com.banksystem.corporate.api.dto.CorporateDtos.UpdateMemberRolesRequest;
import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.domain.corporation.CorporateAccountEntity;
import com.banksystem.corporate.domain.corporation.CorporateAccountRepository;
import com.banksystem.corporate.domain.corporation.CorporateMemberRoleEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipRepository;
import com.banksystem.corporate.domain.corporation.CorporationEntity;
import com.banksystem.corporate.domain.corporation.CorporationRepository;
import com.banksystem.corporate.infrastructure.config.InternalApiKeyProperties;
import com.banksystem.corporate.infrastructure.feign.AccountClient;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.AccountDto;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CreateCorporateAccountReq;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporationService {

  private static final Logger log = LoggerFactory.getLogger(CorporationService.class);

  private final CorporationRepository corporationRepository;
  private final CorporateMembershipRepository membershipRepository;
  private final CorporateAccountRepository accountRepository;
  private final AccountClient accountClient;
  private final CorporateAuditService auditService;
  private final CorporateAuthorizationService authorizationService;
  private final InternalApiKeyProperties apiKeyProperties;

  public CorporationService(
      CorporationRepository corporationRepository,
      CorporateMembershipRepository membershipRepository,
      CorporateAccountRepository accountRepository,
      AccountClient accountClient,
      CorporateAuditService auditService,
      CorporateAuthorizationService authorizationService,
      InternalApiKeyProperties apiKeyProperties) {
    this.corporationRepository = corporationRepository;
    this.membershipRepository = membershipRepository;
    this.accountRepository = accountRepository;
    this.accountClient = accountClient;
    this.auditService = auditService;
    this.authorizationService = authorizationService;
    this.apiKeyProperties = apiKeyProperties;
  }

  @Transactional
  public CorporationResponse createCorporation(UUID createdByUserId, CreateCorporationRequest req) {
    if (corporationRepository.existsByTaxId(req.taxId())) {
      throw new BusinessException("TAX_ID_EXISTS", "Corporation with tax ID " + req.taxId() + " already exists");
    }

    CorporationEntity c = new CorporationEntity();
    c.setId(UUID.randomUUID());
    c.setTaxId(req.taxId().trim());
    c.setCompanyName(req.companyName().trim());
    c.setShortName(req.shortName());
    c.setContactEmail(req.contactEmail());
    c.setContactPhone(req.contactPhone());
    c.setAddress(req.address());
    c.setKycStatus("VERIFIED");
    c.setStatus("ACTIVE");
    c.setCreatedAt(Instant.now());
    c.setUpdatedAt(Instant.now());

    CorporationEntity saved = corporationRepository.save(c);

    // Automatically make creator a CORPORATE_ADMIN and MAKER
    CorporateMembershipEntity m = new CorporateMembershipEntity();
    m.setId(UUID.randomUUID());
    m.setCorporation(saved);
    m.setCorporateId(saved.getId());
    m.setUserId(createdByUserId);
    m.setStatus("ACTIVE");
    m.setJoinedAt(Instant.now());
    m.setCreatedAt(Instant.now());
    m.setUpdatedAt(Instant.now());

    Set<CorporateMemberRoleEntity> roles = new HashSet<>();
    roles.add(new CorporateMemberRoleEntity(UUID.randomUUID(), m, "CORPORATE_ADMIN"));
    roles.add(new CorporateMemberRoleEntity(UUID.randomUUID(), m, "MAKER"));
    m.setRoles(roles);

    membershipRepository.save(m);

    auditService.log(saved.getId(), createdByUserId, "CREATE_CORPORATION", "CORPORATION", saved.getId().toString(), "TaxId=" + saved.getTaxId());
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public CorporationResponse getCorporation(UUID corporateId, UUID userId) {
    authorizationService.requireActiveMember(corporateId, userId);
    CorporationEntity c = corporationRepository.findById(corporateId).orElseThrow(() ->
        new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));
    return toResponse(c);
  }

  @Transactional(readOnly = true)
  public List<CorporationResponse> listUserCorporations(UUID userId) {
    return corporationRepository.findActiveCorporationsForUser(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public CorporateMemberResponse addMember(UUID corporateId, UUID actorUserId, AddMemberRequest req) {
    authorizationService.requireRole(corporateId, actorUserId, "CORPORATE_ADMIN");

    if (membershipRepository.findByCorporateIdAndUserId(corporateId, req.userId()).isPresent()) {
      throw new BusinessException("MEMBER_EXISTS", "User is already a member of this corporation");
    }

    CorporationEntity c = corporationRepository.findById(corporateId).orElseThrow(() ->
        new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));

    CorporateMembershipEntity m = new CorporateMembershipEntity();
    m.setId(UUID.randomUUID());
    m.setCorporation(c);
    m.setCorporateId(corporateId);
    m.setUserId(req.userId());
    m.setStatus("ACTIVE");
    m.setJoinedAt(Instant.now());
    m.setExpiresAt(req.expiresAt());
    m.setCreatedAt(Instant.now());
    m.setUpdatedAt(Instant.now());

    Set<CorporateMemberRoleEntity> roles = new HashSet<>();
    for (String r : req.roles()) {
      roles.add(new CorporateMemberRoleEntity(UUID.randomUUID(), m, r.trim().toUpperCase()));
    }
    m.setRoles(roles);

    CorporateMembershipEntity saved = membershipRepository.save(m);
    auditService.log(corporateId, actorUserId, "ADD_MEMBER", "MEMBERSHIP", saved.getId().toString(), "userId=" + req.userId() + ",roles=" + req.roles());
    return toMemberResponse(saved);
  }

  @Transactional
  public CorporateMemberResponse updateMemberRoles(UUID corporateId, UUID actorUserId, UUID targetUserId, UpdateMemberRolesRequest req) {
    authorizationService.requireRole(corporateId, actorUserId, "CORPORATE_ADMIN");

    CorporateMembershipEntity m = membershipRepository.findByCorporateIdAndUserId(corporateId, targetUserId).orElseThrow(() ->
        new BusinessException("MEMBER_NOT_FOUND", "Corporate member not found"));

    m.getRoles().clear();
    for (String r : req.roles()) {
      m.getRoles().add(new CorporateMemberRoleEntity(UUID.randomUUID(), m, r.trim().toUpperCase()));
    }
    m.setUpdatedAt(Instant.now());
    CorporateMembershipEntity saved = membershipRepository.save(m);
    auditService.log(corporateId, actorUserId, "UPDATE_MEMBER_ROLES", "MEMBERSHIP", saved.getId().toString(), "targetUserId=" + targetUserId + ",roles=" + req.roles());
    return toMemberResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<CorporateMemberResponse> listMembers(UUID corporateId, UUID userId) {
    authorizationService.requireActiveMember(corporateId, userId);
    return membershipRepository.findByCorporateId(corporateId).stream()
        .map(this::toMemberResponse)
        .toList();
  }

  @Transactional
  public CorporateAccountResponse linkAccount(UUID corporateId, UUID actorUserId, LinkAccountRequest req) {
    authorizationService.requireRole(corporateId, actorUserId, "CORPORATE_ADMIN");

    if (accountRepository.existsByCorporateIdAndAccountId(corporateId, req.accountId())) {
      throw new BusinessException("ACCOUNT_ALREADY_LINKED", "Account is already linked to this corporation");
    }

    // Verify account existence and ownership with account-service
    var ownershipResp = accountClient.getOwnership(apiKeyProperties.getEffectiveAccountApiKey(), req.accountId());
    if (ownershipResp == null || ownershipResp.data() == null) {
      throw new BusinessException("ACCOUNT_NOT_FOUND", "Account not found on account-service");
    }
    var ownership = ownershipResp.data();
    if (!"CORPORATE".equalsIgnoreCase(ownership.ownerType()) || !corporateId.equals(ownership.ownerId())) {
      throw new BusinessException("UNAUTHORIZED_ACCOUNT_OWNERSHIP", "Account does not belong to this corporation");
    }
    if (!"ACTIVE".equalsIgnoreCase(ownership.status())) {
      throw new BusinessException("ACCOUNT_NOT_ACTIVE", "Account is not ACTIVE (current status: " + ownership.status() + ")");
    }
    if (!ownership.accountNumber().equalsIgnoreCase(req.accountNumber().trim())) {
      throw new BusinessException("ACCOUNT_NUMBER_MISMATCH", "Account number does not match accountId");
    }

    CorporationEntity c = corporationRepository.findById(corporateId).orElseThrow(() ->
        new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));

    CorporateAccountEntity a = new CorporateAccountEntity();
    a.setId(UUID.randomUUID());
    a.setCorporation(c);
    a.setCorporateId(corporateId);
    a.setAccountId(req.accountId());
    a.setAccountNumber(req.accountNumber().trim());
    a.setAccountName(req.accountName() != null ? req.accountName() : "Corporate Main Account");
    a.setCurrency(ownership.currency() != null ? ownership.currency() : (req.currency() != null ? req.currency() : "VND"));
    a.setPrimary(req.isPrimary());
    a.setStatus("ACTIVE");
    a.setDailyPayoutLimit(req.dailyPayoutLimit());
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());

    CorporateAccountEntity saved = accountRepository.save(a);
    auditService.log(corporateId, actorUserId, "LINK_ACCOUNT", "ACCOUNT", saved.getId().toString(), "accountNumber=" + req.accountNumber());
    return toAccountResponse(saved);
  }

  public CorporateAccountResponse createAndLinkNewAccount(
      UUID corporateId,
      UUID actorUserId,
      UUID commandId,
      String accountType,
      String currency) {
    authorizationService.requireRole(corporateId, actorUserId, "CORPORATE_ADMIN");

    var res = accountClient.createCorporateAccount(apiKeyProperties.getEffectiveAccountApiKey(), new CreateCorporateAccountReq(
        commandId, corporateId, actorUserId, accountType, currency));
    if (res == null || res.data() == null) {
      throw new BusinessException("ACCOUNT_CREATION_FAILED", "Failed to create corporate account in account-service");
    }
    AccountDto created = res.data();

    UUID createdAccountId = UUID.fromString(created.id());
    CorporateAccountEntity linked = accountRepository
        .findByCorporateIdAndAccountId(corporateId, createdAccountId)
        .orElse(null);
    if (linked != null) {
      return toAccountResponse(linked);
    }

    return linkAccount(corporateId, actorUserId, new LinkAccountRequest(
        createdAccountId, created.accountNumber(), "TK Doanh Nghiệp (" + created.accountNumber() + ")",
        created.currency(), false, null));
  }

  @Transactional(readOnly = true)
  public List<CorporateAccountResponse> listAccounts(UUID corporateId, UUID userId) {
    authorizationService.requireActiveMember(corporateId, userId);
    return accountRepository.findByCorporateIdAndStatus(corporateId, "ACTIVE").stream()
        .map(this::toAccountResponse)
        .toList();
  }

  /**
   * Thin delegate to CorporateAuthorizationService for backward compatibility.
   * Services still referencing this method will be migrated in subsequent phases.
   */
  public CorporateMembershipEntity validateMembership(UUID corporateId, UUID userId) {
    return authorizationService.requireActiveMember(corporateId, userId);
  }

  /**
   * Thin delegate to CorporateAuthorizationService for backward compatibility.
   * Services still referencing this method will be migrated in subsequent phases.
   */
  public CorporateMembershipEntity validateAdminOrRole(UUID corporateId, UUID userId, String requiredRole) {
    return authorizationService.requireRole(corporateId, userId, "CORPORATE_ADMIN", requiredRole);
  }

  private CorporationResponse toResponse(CorporationEntity c) {
    return new CorporationResponse(
        c.getId(), c.getTaxId(), c.getCompanyName(), c.getShortName(), c.getKycStatus(),
        c.getStatus(), c.getContactEmail(), c.getContactPhone(), c.getAddress(),
        c.getCreatedAt(), c.getUpdatedAt());
  }

  private CorporateMemberResponse toMemberResponse(CorporateMembershipEntity m) {
    Set<String> roleNames = m.getRoles() != null
        ? m.getRoles().stream().map(CorporateMemberRoleEntity::getRoleName).collect(Collectors.toSet())
        : Set.of();
    return new CorporateMemberResponse(
        m.getId(), m.getCorporateId(), m.getUserId(), m.getStatus(), roleNames, m.getJoinedAt(), m.getExpiresAt());
  }

  private CorporateAccountResponse toAccountResponse(CorporateAccountEntity a) {
    return new CorporateAccountResponse(
        a.getId(), a.getCorporateId(), a.getAccountId(), a.getAccountNumber(), a.getAccountName(),
        a.getCurrency(), BigDecimal.ZERO, a.isPrimary(), a.getStatus(), a.getDailyPayoutLimit(), a.getCreatedAt());
  }
}
