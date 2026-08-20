package com.banksystem.corporate.application.corporation;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.domain.corporation.CorporateMembershipEntity;
import com.banksystem.corporate.domain.corporation.CorporateMembershipRepository;
import com.banksystem.corporate.domain.corporation.CorporationEntity;
import com.banksystem.corporate.domain.corporation.CorporationRepository;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CorporateAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CorporateAuthorizationService.class);

    private final CorporateMembershipRepository membershipRepository;
    private final CorporationRepository corporationRepository;

    public CorporateAuthorizationService(CorporateMembershipRepository membershipRepository, CorporationRepository corporationRepository) {
        this.membershipRepository = membershipRepository;
        this.corporationRepository = corporationRepository;
    }

    public CorporateMembershipEntity requireActiveMember(UUID corporateId, UUID userId) {
        CorporationEntity corporation = corporationRepository.findById(corporateId)
                .orElseThrow(() -> new BusinessException("CORPORATION_NOT_FOUND", "Corporation not found"));

        if (!"ACTIVE".equals(corporation.getStatus())) {
            log.warn("Authorization failed: Corporation {} is not active", corporateId);
            throw new BusinessException("CORPORATION_NOT_ACTIVE", "Corporation is not active");
        }

        return membershipRepository.findActiveWithRoles(corporateId, userId)
                .orElseThrow(() -> {
                    log.warn("Authorization failed: User {} is not an active member of corporation {}", userId, corporateId);
                    return new BusinessException("FORBIDDEN_NOT_MEMBER", "User is not an active member of this corporation");
                });
    }

    public CorporateMembershipEntity requireRole(UUID corporateId, UUID userId, String... roles) {
        CorporateMembershipEntity membership = requireActiveMember(corporateId, userId);

        if (roles != null && roles.length > 0) {
            for (String role : roles) {
                if (membership.hasRole(role)) {
                    return membership;
                }
            }
            log.warn("Authorization failed: User {} does not have any of the required roles {} in corporation {}", userId, Arrays.toString(roles), corporateId);
            throw new BusinessException("FORBIDDEN_INSUFFICIENT_ROLE", "Requires one of roles: " + Arrays.toString(roles));
        }
        
        return membership;
    }

    public void requireResourceBelongsToCorporate(UUID resourceCorporateId, UUID expectedCorporateId) {
        if (resourceCorporateId == null || !resourceCorporateId.equals(expectedCorporateId)) {
            log.warn("Authorization failed: Resource belongs to corporation {} but expected {}", resourceCorporateId, expectedCorporateId);
            throw new BusinessException("FORBIDDEN", "Resource does not belong to this corporation");
        }
    }
}
