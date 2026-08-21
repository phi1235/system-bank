package com.banksystem.auth.application.business.impl;

import com.banksystem.auth.api.dto.BusinessDtos.AddBusinessMemberRequest;
import com.banksystem.auth.api.dto.BusinessDtos.AdminBusinessResponse;
import com.banksystem.auth.api.dto.BusinessDtos.AdminKycReviewRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMemberResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMembershipVerifyResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessOrganizationResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessPermissionActionDto;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessPermissionFeatureDto;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessPermissionMatrixResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessPermissionModuleDto;
import com.banksystem.auth.api.dto.BusinessDtos.CreateBusinessOrganizationRequest;
import com.banksystem.auth.api.dto.BusinessDtos.CustomRoleRequest;
import com.banksystem.auth.api.dto.BusinessDtos.CustomRoleResponse;
import com.banksystem.auth.api.dto.BusinessDtos.RegisterBusinessRequest;
import com.banksystem.auth.api.dto.BusinessDtos.UpdateBusinessMemberRequest;
import com.banksystem.auth.application.business.BusinessOrganizationService;
import com.banksystem.auth.application.rbac.PermissionResolver;
import com.banksystem.auth.domain.auth.UserEntity;
import com.banksystem.auth.domain.auth.UserRepository;
import com.banksystem.auth.domain.business.BusinessMemberEntity;
import com.banksystem.auth.domain.business.BusinessMemberRepository;
import com.banksystem.auth.domain.business.BusinessOrganizationEntity;
import com.banksystem.auth.domain.business.BusinessOrganizationRepository;
import com.banksystem.auth.domain.business.OrgCustomRoleEntity;
import com.banksystem.auth.domain.business.OrgCustomRolePermissionEntity;
import com.banksystem.auth.domain.business.OrgCustomRoleRepository;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessOrganizationServiceImpl implements BusinessOrganizationService {

  private static final Logger log = LoggerFactory.getLogger(BusinessOrganizationServiceImpl.class);
  private static final Set<String> ALLOWED_ROLES = Set.of(
      "BUSINESS_OWNER", "BUSINESS_FINANCE", "BUSINESS_OPERATOR", "BUSINESS_VIEWER"
  );
  private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "SUSPENDED", "REVOKED");

  public static final List<String> ALL_AVAILABLE_PERMISSIONS = List.of(
      // Thu hộ & QR
      "va:view", "va:create", "va:manage", "va:close",
      // Đơn hàng thu tiền
      "collection:view", "collection:create", "collection:edit", "collection:cancel", "collection:settle",
      // Chia tiền đại lý
      "split:view", "split:create", "split:manage", "split:delete",
      // Chuyển tiền & Lô chi
      "transfer:view", "transfer:create", "transfer:approve", "transfer:cancel", "batch:create", "batch:approve",
      // Kết nối POS & Open Banking
      "developer:view", "developer:create", "developer:manage", "developer:delete",
      "openbanking:view", "openbanking:create", "openbanking:manage", "openbanking:delete",
      // Tổ chức & Phân quyền
      "org:members:view", "org:members:create", "org:members:manage", "org:members:delete", "org:members",
      "org:roles:view", "org:roles:create", "org:roles:manage", "org:roles:delete", "org:roles",
      "org:settings:view", "org:settings"
  );

  private final BusinessOrganizationRepository organizationRepository;
  private final BusinessMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final PermissionResolver permissionResolver;
  private final OrgCustomRoleRepository customRoleRepository;

  public BusinessOrganizationServiceImpl(
      BusinessOrganizationRepository organizationRepository,
      BusinessMemberRepository memberRepository,
      UserRepository userRepository,
      PermissionResolver permissionResolver,
      OrgCustomRoleRepository customRoleRepository) {
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.userRepository = userRepository;
    this.permissionResolver = permissionResolver;
    this.customRoleRepository = customRoleRepository;
  }

  @Override
  @Transactional
  public BusinessOrganizationResponse createOrganization(UUID userId, CreateBusinessOrganizationRequest request) {
    String code = request.code().toUpperCase().trim();
    if (organizationRepository.existsByCode(code)) {
      throw new BusinessException("ORGANIZATION_CODE_EXISTS", "Organization code already exists");
    }
    userRepository.findById(userId).orElseThrow(() ->
        new BusinessException("USER_NOT_FOUND", "User not found"));

    Instant now = Instant.now();
    BusinessOrganizationEntity org = BusinessOrganizationEntity.create(
        UUID.randomUUID(), code, request.legalName(), request.taxNumber(), now
    );
    organizationRepository.save(org);

    OrgCustomRoleEntity ownerRole = seedDefaultRolesForOrg(org.getId(), now);

    BusinessMemberEntity ownerMember = BusinessMemberEntity.createWithCustomRole(
        org.getId(), userId, ownerRole.getId(), "BUSINESS_OWNER", now
    );
    memberRepository.save(ownerMember);

    log.info("[BUSINESS-ORG] Created organization id={}, code={}, owner={}", org.getId(), org.getCode(), userId);
    return toOrgResponse(org, "BUSINESS_OWNER");
  }

  @Override
  @Transactional
  public BusinessOrganizationResponse registerBusiness(UUID userId, RegisterBusinessRequest request) {
    userRepository.findById(userId).orElseThrow(() ->
        new BusinessException("USER_NOT_FOUND", "User not found"));

    String taxNumber = request.taxNumber() != null ? request.taxNumber().trim() : null;
    if (taxNumber != null && !taxNumber.isBlank()) {
      if (organizationRepository.existsByTaxNumber(taxNumber)) {
        throw new BusinessException("TAX_NUMBER_EXISTS", "Tax number is already registered with another business");
      }
    }

    String baseCode = request.legalName().replaceAll("[^A-Za-z0-9]", "_").toUpperCase().replaceAll("_+", "_");
    if (baseCode.startsWith("_")) baseCode = baseCode.substring(1);
    if (baseCode.length() > 25) baseCode = baseCode.substring(0, 25);
    if (baseCode.isBlank()) baseCode = "BIZ";

    String code = baseCode;
    Random rnd = new Random();
    while (organizationRepository.existsByCode(code)) {
      code = baseCode + "_" + (1000 + rnd.nextInt(9000));
    }

    Instant now = Instant.now();
    BusinessOrganizationEntity org = BusinessOrganizationEntity.register(
        code, request.legalName(), taxNumber,
        request.contactEmail(), request.contactPhone(), request.address(),
        request.representativeName(), request.industry(), now
    );
    organizationRepository.save(org);

    OrgCustomRoleEntity ownerRole = seedDefaultRolesForOrg(org.getId(), now);

    BusinessMemberEntity ownerMember = BusinessMemberEntity.createWithCustomRole(
        org.getId(), userId, ownerRole.getId(), "BUSINESS_OWNER", now
    );
    memberRepository.save(ownerMember);

    log.info("[BUSINESS-ORG] Registered business id={}, code={}, legalName={}, owner={}, kycStatus=PENDING_KYC",
        org.getId(), org.getCode(), org.getLegalName(), userId);
    return toOrgResponse(org, "BUSINESS_OWNER");
  }

  @Override
  @Transactional
  public BusinessOrganizationResponse reviewKyc(UUID organizationId, UUID adminUserId, AdminKycReviewRequest request) {
    BusinessOrganizationEntity org = organizationRepository.findById(organizationId).orElseThrow(() ->
        new BusinessException("ORGANIZATION_NOT_FOUND", "Organization not found"));

    Instant now = Instant.now();
    if (request.isApprove()) {
      org.approveKyc(adminUserId, now);
      log.info("[BUSINESS-ORG] KYC APPROVED for org id={}, by admin={}", org.getId(), adminUserId);
    } else {
      String reason = request.rejectReason() != null ? request.rejectReason().trim() : "Hồ sơ không hợp lệ";
      org.rejectKyc(adminUserId, reason, now);
      log.info("[BUSINESS-ORG] KYC REJECTED for org id={}, reason={}, by admin={}", org.getId(), reason, adminUserId);
    }
    organizationRepository.save(org);
    return toOrgResponse(org, "ADMIN");
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminBusinessResponse> listAdminBusinesses(String kycStatus) {
    List<BusinessOrganizationEntity> orgs;
    if (kycStatus != null && !kycStatus.isBlank()) {
      orgs = organizationRepository.findByKycStatusIn(List.of(kycStatus.toUpperCase().trim()));
    } else {
      orgs = organizationRepository.findAll();
    }

    return orgs.stream().map(o -> new AdminBusinessResponse(
        o.getId(), o.getCode(), o.getLegalName(), o.getTaxNumber(),
        o.getStatus(), o.getKycStatus(), o.getContactEmail(), o.getContactPhone(),
        o.getAddress(), o.getRepresentativeName(), o.getIndustry(),
        o.getBusinessLicenseUrl(), o.getIdCardUrl(), o.getKycRejectReason(),
        o.getKycReviewedBy(), o.getKycReviewedAt(), o.getCreatedAt(), o.getUpdatedAt()
    )).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<BusinessOrganizationResponse> listUserOrganizations(UUID userId) {
    List<BusinessMemberEntity> memberships = memberRepository.findByUserId(userId).stream()
        .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
        .toList();
    if (memberships.isEmpty()) {
      return List.of();
    }

    Map<UUID, String> roleMap = memberships.stream()
        .collect(Collectors.toMap(BusinessMemberEntity::getOrganizationId, BusinessMemberEntity::getBusinessRole, (a, b) -> a));

    List<BusinessOrganizationEntity> orgs = organizationRepository.findAllById(roleMap.keySet()).stream()
        .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()))
        .toList();

    return orgs.stream()
        .map(o -> toOrgResponse(o, roleMap.get(o.getId())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessOrganizationResponse getOrganization(UUID organizationId, UUID userId) {
    BusinessOrganizationEntity org = organizationRepository.findById(organizationId).orElseThrow(() ->
        new BusinessException("ORGANIZATION_NOT_FOUND", "Organization not found"));

    if (!"ACTIVE".equalsIgnoreCase(org.getStatus())) {
      throw new BusinessException("ORGANIZATION_INACTIVE", "Organization is not active");
    }

    BusinessMemberEntity member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
        .orElseThrow(() -> new BusinessException("FORBIDDEN_ORGANIZATION_ACCESS", "User is not a member of this organization"));

    if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
      throw new BusinessException("MEMBERSHIP_INACTIVE", "Membership is not active");
    }

    return toOrgResponse(org, member.getBusinessRole());
  }

  @Override
  @Transactional(readOnly = true)
  public List<BusinessMemberResponse> listMembers(UUID organizationId, UUID requestingUserId) {
    ensureMembership(organizationId, requestingUserId);
    List<BusinessMemberEntity> members = memberRepository.findByOrganizationId(organizationId);
    if (members.isEmpty()) return List.of();

    List<UUID> userIds = members.stream().map(BusinessMemberEntity::getUserId).toList();
    Map<UUID, UserEntity> userMap = userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u));

    List<OrgCustomRoleEntity> roles = customRoleRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    Map<UUID, String> roleIdToName = roles.stream().collect(Collectors.toMap(OrgCustomRoleEntity::getId, OrgCustomRoleEntity::getDisplayName, (a, b) -> a));
    Map<String, String> roleCodeToName = roles.stream().collect(Collectors.toMap(r -> r.getCode().toUpperCase(), OrgCustomRoleEntity::getDisplayName, (a, b) -> a));

    return members.stream().map(m -> {
      UserEntity user = userMap.get(m.getUserId());
      String username = user != null ? user.getUsername() : "UNKNOWN";
      String email = user != null ? user.getEmail() : "";
      String roleDisplayName = resolveRoleDisplayName(m, roleIdToName, roleCodeToName);
      return new BusinessMemberResponse(
          m.getId(), m.getOrganizationId(), m.getUserId(), username, email,
          m.getBusinessRole(), roleDisplayName, m.getStatus(), m.getJoinedAt()
      );
    }).toList();
  }

  private String resolveRoleDisplayName(BusinessMemberEntity m, Map<UUID, String> roleIdToName, Map<String, String> roleCodeToName) {
    if (m.getCustomRoleId() != null && roleIdToName.containsKey(m.getCustomRoleId())) {
      return roleIdToName.get(m.getCustomRoleId());
    }
    String code = m.getBusinessRole() != null ? m.getBusinessRole().toUpperCase() : "";
    if (roleCodeToName.containsKey(code)) {
      return roleCodeToName.get(code);
    }
    if ("BUSINESS_OWNER".equals(code) || "OWNER".equals(code)) {
      return "Chủ doanh nghiệp (Toàn quyền)";
    }
    if ("BUSINESS_FINANCE".equals(code)) {
      return "Kế toán / Tài chính";
    }
    if ("BUSINESS_OPERATOR".equals(code)) {
      return "Nhân viên vận hành";
    }
    if ("BUSINESS_VIEWER".equals(code)) {
      return "Người xem";
    }
    return m.getBusinessRole();
  }

  @Override
  @Transactional
  public BusinessMemberResponse addMember(UUID organizationId, UUID requestingUserId, AddBusinessMemberRequest request) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can invite members");
    }

    String rawRole = request.effectiveRole();
    if (rawRole == null || rawRole.isBlank()) {
      throw new BusinessException("INVALID_BUSINESS_ROLE", "Business role is required");
    }
    String role = rawRole.toUpperCase().trim();

    UUID customRoleId = null;
    String roleDisplayName = role;
    var customRoleOpt = customRoleRepository.findByOrganizationIdAndCode(organizationId, role);
    if (customRoleOpt.isPresent()) {
      customRoleId = customRoleOpt.get().getId();
      roleDisplayName = customRoleOpt.get().getDisplayName();
    } else if (!ALLOWED_ROLES.contains(role)) {
      throw new BusinessException("INVALID_BUSINESS_ROLE", "Role not found in organization: " + role);
    }

    String identifier = request.effectiveIdentifier();
    if (identifier == null || identifier.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Username, email, or user ID must be provided");
    }

    UserEntity targetUser = null;
    try {
      UUID uid = UUID.fromString(identifier);
      targetUser = userRepository.findById(uid).orElse(null);
    } catch (IllegalArgumentException ignored) {}

    if (targetUser == null) {
      targetUser = userRepository.findByUsername(identifier)
          .or(() -> userRepository.findByEmailIgnoreCase(identifier))
          .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "No user found with username or email: " + identifier));
    }

    if (memberRepository.existsByOrganizationIdAndUserId(organizationId, targetUser.getId())) {
      throw new BusinessException("MEMBER_ALREADY_EXISTS", "User is already a member of this organization");
    }

    Instant now = Instant.now();
    BusinessMemberEntity member = customRoleId != null
        ? BusinessMemberEntity.createWithCustomRole(organizationId, targetUser.getId(), customRoleId, role, now)
        : BusinessMemberEntity.create(organizationId, targetUser.getId(), role, now);
    memberRepository.save(member);

    log.info("[BUSINESS-ORG] Added member user={} role={} to org={}", targetUser.getId(), role, organizationId);
    return new BusinessMemberResponse(
        member.getId(), member.getOrganizationId(), member.getUserId(),
        targetUser.getUsername(), targetUser.getEmail(), member.getBusinessRole(), roleDisplayName, member.getStatus(), member.getJoinedAt()
    );
  }

  @Override
  @Transactional
  public BusinessMemberResponse updateMemberRole(UUID organizationId, UUID targetUserId, UUID requestingUserId, UpdateBusinessMemberRequest request) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can update member roles");
    }

    String role = request.businessRole().toUpperCase().trim();
    UUID customRoleId = null;
    String roleDisplayName = role;
    var customRoleOpt = customRoleRepository.findByOrganizationIdAndCode(organizationId, role);
    if (customRoleOpt.isPresent()) {
      customRoleId = customRoleOpt.get().getId();
      roleDisplayName = customRoleOpt.get().getDisplayName();
    } else if (!ALLOWED_ROLES.contains(role)) {
      throw new BusinessException("INVALID_BUSINESS_ROLE", "Role not found in organization: " + role);
    }

    BusinessMemberEntity targetMember = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
        .orElseThrow(() -> new BusinessException("MEMBER_NOT_FOUND", "Member not found in organization"));

    if ("BUSINESS_OWNER".equals(targetMember.getBusinessRole()) && !"BUSINESS_OWNER".equals(role)) {
      long activeOwners = memberRepository.findByOrganizationId(organizationId).stream()
          .filter(m -> "BUSINESS_OWNER".equals(m.getBusinessRole()) && "ACTIVE".equalsIgnoreCase(m.getStatus()))
          .count();
      if (activeOwners <= 1) {
        throw new BusinessException("CANNOT_DEMOTE_LAST_OWNER", "Cannot demote the only active owner of the organization");
      }
    }

    targetMember.setBusinessRole(role);
    targetMember.setCustomRoleId(customRoleId);
    if (request.status() != null && !request.status().isBlank()) {
      String newStatus = request.status().toUpperCase().trim();
      if (!ALLOWED_STATUSES.contains(newStatus)) {
        throw new BusinessException("INVALID_STATUS", "Allowed statuses: " + ALLOWED_STATUSES);
      }
      if ("BUSINESS_OWNER".equals(targetMember.getBusinessRole()) && !"ACTIVE".equalsIgnoreCase(newStatus)) {
        long activeOwners = memberRepository.findByOrganizationId(organizationId).stream()
            .filter(m -> "BUSINESS_OWNER".equals(m.getBusinessRole()) && "ACTIVE".equalsIgnoreCase(m.getStatus()) && !m.getId().equals(targetMember.getId()))
            .count();
        if (activeOwners < 1) {
          throw new BusinessException("CANNOT_DEACTIVATE_LAST_OWNER", "Cannot deactivate the only active owner of the organization");
        }
      }
      targetMember.setStatus(newStatus);
    }
    targetMember.setUpdatedAt(Instant.now());
    memberRepository.save(targetMember);

    UserEntity user = userRepository.findById(targetUserId).orElse(null);
    return new BusinessMemberResponse(
        targetMember.getId(), targetMember.getOrganizationId(), targetMember.getUserId(),
        user != null ? user.getUsername() : "UNKNOWN", user != null ? user.getEmail() : "",
        targetMember.getBusinessRole(), roleDisplayName, targetMember.getStatus(), targetMember.getJoinedAt()
    );
  }

  @Override
  @Transactional
  public void removeMember(UUID organizationId, UUID targetUserId, UUID requestingUserId) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole()) && !Objects.equals(requestingUserId, targetUserId)) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can remove other members");
    }

    BusinessMemberEntity targetMember = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
        .orElseThrow(() -> new BusinessException("MEMBER_NOT_FOUND", "Member not found in organization"));

    if ("BUSINESS_OWNER".equals(targetMember.getBusinessRole())) {
      long activeOwners = memberRepository.findByOrganizationId(organizationId).stream()
          .filter(m -> "BUSINESS_OWNER".equals(m.getBusinessRole()) && "ACTIVE".equalsIgnoreCase(m.getStatus()))
          .count();
      if (activeOwners <= 1) {
        throw new BusinessException("CANNOT_REMOVE_LAST_OWNER", "Cannot remove the only active owner of the organization");
      }
    }

    memberRepository.delete(targetMember);
    log.info("[BUSINESS-ORG] Removed member user={} from org={}", targetUserId, organizationId);
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessMembershipVerifyResponse verifyMembership(UUID organizationId, UUID userId, String requiredPermission) {
    BusinessOrganizationEntity org = organizationRepository.findById(organizationId).orElse(null);
    if (org == null || !"ACTIVE".equalsIgnoreCase(org.getStatus())) {
      return new BusinessMembershipVerifyResponse(false, organizationId, userId, null, null, null, List.of());
    }

    BusinessMemberEntity member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId).orElse(null);
    if (member == null || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
      return new BusinessMembershipVerifyResponse(false, organizationId, userId, null, null, null, List.of());
    }

    List<String> perms = new ArrayList<>();
    String roleDisplayName = member.getBusinessRole();

    if (member.getCustomRoleId() != null) {
      perms = customRoleRepository.findPermissionCodesByRoleId(member.getCustomRoleId());
      OrgCustomRoleEntity roleEnt = customRoleRepository.findById(member.getCustomRoleId()).orElse(null);
      if (roleEnt != null && roleEnt.getDisplayName() != null) {
        roleDisplayName = roleEnt.getDisplayName();
      }
    } else {
      var customRoleOpt = customRoleRepository.findByOrganizationIdAndCode(organizationId, member.getBusinessRole());
      if (customRoleOpt.isPresent()) {
        perms = customRoleRepository.findPermissionCodesByRoleId(customRoleOpt.get().getId());
        roleDisplayName = customRoleOpt.get().getDisplayName();
      }
    }

    if (perms.isEmpty()) {
      perms = permissionResolver.resolvePermissions(List.of(member.getBusinessRole()));
    }

    boolean isOwner = "BUSINESS_OWNER".equalsIgnoreCase(member.getBusinessRole()) || "OWNER".equalsIgnoreCase(member.getBusinessRole());
    if (isOwner && "BUSINESS_OWNER".equalsIgnoreCase(roleDisplayName)) {
      roleDisplayName = "Chủ doanh nghiệp (Toàn quyền)";
    }

    boolean valid = isOwner || matchesPermission(perms, requiredPermission);
    return new BusinessMembershipVerifyResponse(valid, organizationId, userId, member.getBusinessRole(), roleDisplayName, member.getStatus(), perms);
  }

  private boolean matchesPermission(List<String> userPerms, String requiredPermission) {
    if (requiredPermission == null || requiredPermission.isBlank()) return true;
    if (userPerms.contains("*") || userPerms.contains(requiredPermission)) return true;

    String stripped = requiredPermission.startsWith("business:") ? requiredPermission.substring(9) : requiredPermission;
    if (userPerms.contains(stripped) || userPerms.contains("business:" + stripped)) return true;

    String[] parts = stripped.split(":", 2);
    if (parts.length == 2) {
      String moduleWildcard = parts[0] + ":*";
      if (userPerms.contains(moduleWildcard) || userPerms.contains("business:" + moduleWildcard)) return true;
    }

    if (stripped.startsWith("orders:")) {
      String collPerm = "collection:" + stripped.substring(7);
      if (userPerms.contains(collPerm) || userPerms.contains("business:" + collPerm)) return true;
    }
    if (stripped.equals("orders:manage") || stripped.equals("orders:create")) {
      if (userPerms.contains("collection:create") || userPerms.contains("collection:manage") || userPerms.contains("collection:edit")) return true;
    }
    if (stripped.equals("settlements:view") && (userPerms.contains("transfer:view") || userPerms.contains("transfer:create") || userPerms.contains("transfer:approve") || userPerms.contains("batch:create") || userPerms.contains("batch:approve"))) return true;
    if (stripped.equals("settlements:execute") && (userPerms.contains("transfer:approve") || userPerms.contains("transfer:create") || userPerms.contains("batch:approve"))) return true;
    if (stripped.equals("va:view") && (userPerms.contains("va:view") || userPerms.contains("va:create") || userPerms.contains("va:manage") || userPerms.contains("va:close"))) return true;
    if (stripped.equals("split:view") && (userPerms.contains("split:view") || userPerms.contains("split:create") || userPerms.contains("split:manage") || userPerms.contains("split:delete"))) return true;
    if (stripped.equals("developer:view") && (userPerms.contains("developer:view") || userPerms.contains("developer:create") || userPerms.contains("developer:manage") || userPerms.contains("developer:delete"))) return true;
    if (stripped.equals("openbanking:view") && (userPerms.contains("openbanking:view") || userPerms.contains("openbanking:create") || userPerms.contains("openbanking:manage") || userPerms.contains("openbanking:delete"))) return true;
    if (stripped.equals("dashboard:view")) return true;
    if (stripped.equals("members:manage") && (userPerms.contains("org:members") || userPerms.contains("org:members:manage") || userPerms.contains("org:members:create"))) return true;
    if (stripped.equals("members:view") && (userPerms.contains("org:members") || userPerms.contains("org:members:view") || userPerms.contains("org:members:manage") || userPerms.contains("org:members:create"))) return true;
    if (stripped.equals("roles:manage") && (userPerms.contains("org:roles") || userPerms.contains("org:roles:manage") || userPerms.contains("org:roles:create"))) return true;
    if (stripped.equals("roles:view") && (userPerms.contains("org:roles") || userPerms.contains("org:roles:view") || userPerms.contains("org:roles:manage") || userPerms.contains("org:roles:create"))) return true;

    return false;
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessMembershipVerifyResponse getMyMembership(UUID organizationId, UUID userId) {
    return verifyMembership(organizationId, userId, null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomRoleResponse> listCustomRoles(UUID organizationId, UUID requestingUserId) {
    ensureMembership(organizationId, requestingUserId);
    List<OrgCustomRoleEntity> roles = customRoleRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    return roles.stream().map(r -> {
      List<String> rolePerms = customRoleRepository.findPermissionCodesByRoleId(r.getId());
      return new CustomRoleResponse(
          r.getId(), r.getCode(), r.getDisplayName(), r.getDescription(),
          r.isOwnerRole(), r.isDefaultRole(), rolePerms, r.getCreatedAt()
      );
    }).toList();
  }

  @Override
  @Transactional
  public CustomRoleResponse createCustomRole(UUID organizationId, UUID requestingUserId, CustomRoleRequest request) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can create custom roles");
    }

    String code = request.code().toUpperCase().trim();
    if (customRoleRepository.existsByOrganizationIdAndCode(organizationId, code)) {
      throw new BusinessException("ROLE_CODE_EXISTS", "A role with code '" + code + "' already exists in this organization");
    }

    Instant now = Instant.now();
    OrgCustomRoleEntity entity = OrgCustomRoleEntity.create(
        organizationId, code, request.displayName(), request.description(), false, false, now
    );

    if (request.permissions() != null && !request.permissions().isEmpty()) {
      for (String p : request.permissions()) {
        entity.getPermissions().add(new OrgCustomRolePermissionEntity(entity, p.trim()));
      }
    }
    customRoleRepository.save(entity);

    log.info("[BUSINESS-ORG] Created custom role org={}, code={}, name={}", organizationId, code, request.displayName());
    return new CustomRoleResponse(
        entity.getId(), entity.getCode(), entity.getDisplayName(), entity.getDescription(),
        entity.isOwnerRole(), entity.isDefaultRole(), request.permissions(), entity.getCreatedAt()
    );
  }

  @Override
  @Transactional
  public CustomRoleResponse updateCustomRole(UUID organizationId, UUID roleId, UUID requestingUserId, CustomRoleRequest request) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can edit custom roles");
    }

    OrgCustomRoleEntity role = customRoleRepository.findById(roleId).orElseThrow(() ->
        new BusinessException("ROLE_NOT_FOUND", "Custom role not found"));

    if (!role.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Role does not belong to this organization");
    }

    if (role.isOwnerRole()) {
      throw new BusinessException("CANNOT_MODIFY_OWNER_ROLE", "Cannot modify the default Owner role");
    }

    role.setDisplayName(request.displayName().trim());
    role.setDescription(request.description());
    role.setUpdatedAt(Instant.now());

    role.getPermissions().clear();
    if (request.permissions() != null) {
      for (String p : request.permissions()) {
        role.getPermissions().add(new OrgCustomRolePermissionEntity(role, p.trim()));
      }
    }
    customRoleRepository.save(role);

    log.info("[BUSINESS-ORG] Updated custom role org={}, roleId={}", organizationId, roleId);
    return new CustomRoleResponse(
        role.getId(), role.getCode(), role.getDisplayName(), role.getDescription(),
        role.isOwnerRole(), role.isDefaultRole(), request.permissions() != null ? request.permissions() : List.of(), role.getCreatedAt()
    );
  }

  @Override
  @Transactional
  public void deleteCustomRole(UUID organizationId, UUID roleId, UUID requestingUserId) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can delete custom roles");
    }

    OrgCustomRoleEntity role = customRoleRepository.findById(roleId).orElseThrow(() ->
        new BusinessException("ROLE_NOT_FOUND", "Custom role not found"));

    if (!role.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Role does not belong to this organization");
    }

    if (role.isOwnerRole() || role.isDefaultRole()) {
      throw new BusinessException("CANNOT_DELETE_DEFAULT_ROLE", "Cannot delete default or owner roles");
    }

    customRoleRepository.delete(role);
    log.info("[BUSINESS-ORG] Deleted custom role org={}, roleId={}", organizationId, roleId);
  }

  @Override
  public List<String> listAvailablePermissions() {
    return ALL_AVAILABLE_PERMISSIONS;
  }

  @Override
  public BusinessPermissionMatrixResponse getPermissionMatrix() {
    List<BusinessPermissionActionDto> actionCols = List.of(
        new BusinessPermissionActionDto("view", "B2B.RBAC.ACT_VIEW", "visibility"),
        new BusinessPermissionActionDto("create", "B2B.RBAC.ACT_CREATE", "add_circle"),
        new BusinessPermissionActionDto("manage", "B2B.RBAC.ACT_MANAGE", "edit"),
        new BusinessPermissionActionDto("delete", "B2B.RBAC.ACT_DELETE", "delete"),
        new BusinessPermissionActionDto("approve", "B2B.RBAC.ACT_APPROVE", "verified")
    );

    List<BusinessPermissionModuleDto> modules = List.of(
        new BusinessPermissionModuleDto(
            "collection", "Thu hộ & Điểm bán", "B2B.RBAC.MODULE_COLLECTION", "account_balance",
            List.of(
                new BusinessPermissionFeatureDto(
                    "va", "Tài khoản Thu hộ & QR", "B2B.RBAC.FEAT_VA",
                    Map.of("view", "va:view", "create", "va:create", "manage", "va:manage", "delete", "va:close")
                ),
                new BusinessPermissionFeatureDto(
                    "collection_orders", "Đơn hàng thu tiền", "B2B.RBAC.FEAT_ORDERS",
                    Map.of("view", "collection:view", "create", "collection:create", "manage", "collection:edit", "delete", "collection:cancel", "approve", "collection:settle")
                )
            )
        ),
        new BusinessPermissionModuleDto(
            "payout", "Chi tiền & Đối tác", "B2B.RBAC.MODULE_PAYOUT", "call_split",
            List.of(
                new BusinessPermissionFeatureDto(
                    "split_rules", "Quy tắc chia tiền đại lý", "B2B.RBAC.FEAT_SPLIT",
                    Map.of("view", "split:view", "create", "split:create", "manage", "split:manage", "delete", "split:delete")
                ),
                new BusinessPermissionFeatureDto(
                    "transfers", "Lệnh chuyển tiền & Lô chi", "B2B.RBAC.FEAT_TRANSFERS",
                    Map.of("view", "transfer:view", "create", "transfer:create", "manage", "batch:create", "delete", "transfer:cancel", "approve", "transfer:approve")
                )
            )
        ),
        new BusinessPermissionModuleDto(
            "integration", "Kết nối & Kỹ thuật", "B2B.RBAC.MODULE_INTEGRATION", "terminal",
            List.of(
                new BusinessPermissionFeatureDto(
                    "developer", "Kết nối phần mềm POS/API", "B2B.RBAC.FEAT_DEVELOPER",
                    Map.of("view", "developer:view", "create", "developer:create", "manage", "developer:manage", "delete", "developer:delete")
                ),
                new BusinessPermissionFeatureDto(
                    "openbanking", "Ủy quyền Open Banking", "B2B.RBAC.FEAT_OPENBANKING",
                    Map.of("view", "openbanking:view", "create", "openbanking:create", "manage", "openbanking:manage", "delete", "openbanking:delete")
                )
            )
        ),
        new BusinessPermissionModuleDto(
            "organization", "Tổ chức & Cài đặt", "B2B.RBAC.MODULE_ORGANIZATION", "admin_panel_settings",
            List.of(
                new BusinessPermissionFeatureDto(
                    "members", "Nhân viên & Thành viên", "B2B.RBAC.FEAT_MEMBERS",
                    Map.of("view", "org:members:view", "create", "org:members:create", "manage", "org:members:manage", "delete", "org:members:delete")
                ),
                new BusinessPermissionFeatureDto(
                    "roles", "Chức vụ & Phân quyền", "B2B.RBAC.FEAT_ROLES",
                    Map.of("view", "org:roles:view", "create", "org:roles:create", "manage", "org:roles:manage", "delete", "org:roles:delete")
                ),
                new BusinessPermissionFeatureDto(
                    "settings", "Thông tin Doanh nghiệp", "B2B.RBAC.FEAT_SETTINGS",
                    Map.of("view", "org:settings:view", "manage", "org:settings")
                )
            )
        )
    );

    return new BusinessPermissionMatrixResponse(actionCols, modules);
  }

  private OrgCustomRoleEntity seedDefaultRolesForOrg(UUID organizationId, Instant now) {
    List<OrgCustomRoleEntity> defaultRoles = OrgCustomRoleEntity.seedDefaults(organizationId, now);
    for (OrgCustomRoleEntity r : defaultRoles) {
      if (r.isOwnerRole()) {
        for (String perm : ALL_AVAILABLE_PERMISSIONS) {
          r.getPermissions().add(new OrgCustomRolePermissionEntity(r, perm));
        }
      } else {
        for (String perm : List.of("collection:view", "va:view", "transfer:view", "report:view")) {
          r.getPermissions().add(new OrgCustomRolePermissionEntity(r, perm));
        }
      }
    }
    customRoleRepository.saveAll(defaultRoles);
    return defaultRoles.stream().filter(OrgCustomRoleEntity::isOwnerRole).findFirst().orElseThrow();
  }

  private BusinessOrganizationResponse toOrgResponse(BusinessOrganizationEntity org, String role) {
    return new BusinessOrganizationResponse(
        org.getId(), org.getCode(), org.getLegalName(), org.getTaxNumber(),
        org.getStatus(), org.getCreatedAt(), role
    );
  }

  private BusinessMemberEntity ensureMembership(UUID organizationId, UUID userId) {
    BusinessOrganizationEntity org = organizationRepository.findById(organizationId)
        .orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "Organization not found"));
    if (!"ACTIVE".equalsIgnoreCase(org.getStatus())) {
      throw new BusinessException("ORGANIZATION_INACTIVE", "Organization is inactive");
    }

    BusinessMemberEntity member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
        .orElseThrow(() -> new BusinessException("FORBIDDEN_ORGANIZATION_ACCESS", "You are not a member of this business organization"));
    if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
      throw new BusinessException("MEMBERSHIP_INACTIVE", "Your membership in this organization is inactive");
    }
    return member;
  }
}
