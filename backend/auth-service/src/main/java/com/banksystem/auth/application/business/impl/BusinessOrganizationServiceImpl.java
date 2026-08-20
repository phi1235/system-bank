package com.banksystem.auth.application.business.impl;

import com.banksystem.auth.api.dto.BusinessDtos.AddBusinessMemberRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMemberResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMembershipVerifyResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessOrganizationResponse;
import com.banksystem.auth.api.dto.BusinessDtos.CreateBusinessOrganizationRequest;
import com.banksystem.auth.api.dto.BusinessDtos.UpdateBusinessMemberRequest;
import com.banksystem.auth.application.business.BusinessOrganizationService;
import com.banksystem.auth.application.rbac.PermissionResolver;
import com.banksystem.auth.domain.auth.UserEntity;
import com.banksystem.auth.domain.auth.UserRepository;
import com.banksystem.auth.domain.business.BusinessMemberEntity;
import com.banksystem.auth.domain.business.BusinessMemberRepository;
import com.banksystem.auth.domain.business.BusinessOrganizationEntity;
import com.banksystem.auth.domain.business.BusinessOrganizationRepository;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private final BusinessOrganizationRepository organizationRepository;
  private final BusinessMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final PermissionResolver permissionResolver;

  public BusinessOrganizationServiceImpl(
      BusinessOrganizationRepository organizationRepository,
      BusinessMemberRepository memberRepository,
      UserRepository userRepository,
      PermissionResolver permissionResolver) {
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.userRepository = userRepository;
    this.permissionResolver = permissionResolver;
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

    BusinessMemberEntity ownerMember = BusinessMemberEntity.create(
        org.getId(), userId, "BUSINESS_OWNER", now
    );
    memberRepository.save(ownerMember);

    log.info("[BUSINESS-ORG] Created organization id={}, code={}, owner={}", org.getId(), org.getCode(), userId);
    return new BusinessOrganizationResponse(
        org.getId(), org.getCode(), org.getLegalName(), org.getTaxNumber(), org.getStatus(), org.getCreatedAt(), "BUSINESS_OWNER"
    );
  }

  private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "SUSPENDED", "REVOKED");

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
        .map(o -> new BusinessOrganizationResponse(
            o.getId(), o.getCode(), o.getLegalName(), o.getTaxNumber(), o.getStatus(), o.getCreatedAt(), roleMap.get(o.getId())
        ))
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

    return new BusinessOrganizationResponse(
        org.getId(), org.getCode(), org.getLegalName(), org.getTaxNumber(), org.getStatus(), org.getCreatedAt(), member.getBusinessRole()
    );
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

    return members.stream().map(m -> {
      UserEntity user = userMap.get(m.getUserId());
      String username = user != null ? user.getUsername() : "UNKNOWN";
      String email = user != null ? user.getEmail() : "";
      return new BusinessMemberResponse(
          m.getId(), m.getOrganizationId(), m.getUserId(), username, email, m.getBusinessRole(), m.getStatus(), m.getJoinedAt()
      );
    }).toList();
  }

  @Override
  @Transactional
  public BusinessMemberResponse addMember(UUID organizationId, UUID requestingUserId, AddBusinessMemberRequest request) {
    BusinessMemberEntity requesting = ensureMembership(organizationId, requestingUserId);
    if (!"BUSINESS_OWNER".equals(requesting.getBusinessRole())) {
      throw new BusinessException("FORBIDDEN", "Only organization owner can invite members");
    }

    String role = request.businessRole().toUpperCase().trim();
    if (!ALLOWED_ROLES.contains(role)) {
      throw new BusinessException("INVALID_BUSINESS_ROLE", "Allowed roles: " + ALLOWED_ROLES);
    }

    UserEntity targetUser;
    if (request.userId() != null) {
      targetUser = userRepository.findById(request.userId()).orElseThrow(() ->
          new BusinessException("USER_NOT_FOUND", "User not found"));
    } else if (request.username() != null && !request.username().isBlank()) {
      targetUser = userRepository.findByUsername(request.username().trim()).orElseThrow(() ->
          new BusinessException("USER_NOT_FOUND", "User not found with username: " + request.username()));
    } else {
      throw new BusinessException("INVALID_REQUEST", "Either userId or username must be provided");
    }

    if (memberRepository.existsByOrganizationIdAndUserId(organizationId, targetUser.getId())) {
      throw new BusinessException("MEMBER_ALREADY_EXISTS", "User is already a member of this organization");
    }

    Instant now = Instant.now();
    BusinessMemberEntity member = BusinessMemberEntity.create(organizationId, targetUser.getId(), role, now);
    memberRepository.save(member);

    log.info("[BUSINESS-ORG] Added member user={} role={} to org={}", targetUser.getId(), role, organizationId);
    return new BusinessMemberResponse(
        member.getId(), member.getOrganizationId(), member.getUserId(),
        targetUser.getUsername(), targetUser.getEmail(), member.getBusinessRole(), member.getStatus(), member.getJoinedAt()
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
    if (!ALLOWED_ROLES.contains(role)) {
      throw new BusinessException("INVALID_BUSINESS_ROLE", "Allowed roles: " + ALLOWED_ROLES);
    }

    BusinessMemberEntity targetMember = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
        .orElseThrow(() -> new BusinessException("MEMBER_NOT_FOUND", "Member not found in organization"));

    // Guard: Prevent demoting the last owner
    if ("BUSINESS_OWNER".equals(targetMember.getBusinessRole()) && !"BUSINESS_OWNER".equals(role)) {
      long activeOwners = memberRepository.findByOrganizationId(organizationId).stream()
          .filter(m -> "BUSINESS_OWNER".equals(m.getBusinessRole()) && "ACTIVE".equalsIgnoreCase(m.getStatus()))
          .count();
      if (activeOwners <= 1) {
        throw new BusinessException("CANNOT_DEMOTE_LAST_OWNER", "Cannot demote the only active owner of the organization");
      }
    }

    targetMember.setBusinessRole(role);
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
        targetMember.getBusinessRole(), targetMember.getStatus(), targetMember.getJoinedAt()
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
      return new BusinessMembershipVerifyResponse(false, organizationId, userId, null, null, List.of());
    }

    BusinessMemberEntity member = memberRepository.findByOrganizationIdAndUserId(organizationId, userId).orElse(null);
    if (member == null || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
      return new BusinessMembershipVerifyResponse(false, organizationId, userId, null, null, List.of());
    }

    List<String> perms = permissionResolver.resolvePermissions(List.of(member.getBusinessRole()));
    boolean valid = requiredPermission == null || perms.contains(requiredPermission) || perms.contains("*");
    return new BusinessMembershipVerifyResponse(valid, organizationId, userId, member.getBusinessRole(), member.getStatus(), perms);
  }

  @Override
  @Transactional(readOnly = true)
  public BusinessMembershipVerifyResponse getMyMembership(UUID organizationId, UUID userId) {
    return verifyMembership(organizationId, userId, null);
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
