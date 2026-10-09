package com.slotix.reservationcore.common;

import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class TenantAccessService {

    private final CompanyMembershipRepository companyMembershipRepository;

    public TenantAccessService(CompanyMembershipRepository companyMembershipRepository) {
        this.companyMembershipRepository = companyMembershipRepository;
    }

    public void requireActiveMembership(UUID companyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("An authenticated company context is required");
        }

        if (companyId == null || !companyId.equals(principal.companyId())
            || !companyMembershipRepository.existsByCompanyIdAndUserIdAndStatusAndDeletedAtIsNull(
                companyId,
                principal.userId(),
                "ACTIVE"
            )) {
            throw new AccessDeniedException("You do not have access to this company");
        }
    }

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new AccessDeniedException("An authenticated company context is required");
        }
        return principal.userId();
    }

    public void requireCompanyRole(UUID companyId, MembershipRole role) {
        if (!currentCompanyRoles(companyId).contains(role)) {
            throw new AccessDeniedException("The required company role is not assigned");
        }
    }

    public Set<MembershipRole> currentCompanyRoles(UUID companyId) {
        requireActiveMembership(companyId);
        return companyMembershipRepository
            .findByCompanyIdAndUserIdAndDeletedAtIsNull(companyId, currentUserId())
            .filter(membership -> "ACTIVE".equals(membership.getStatus()))
            .map(membership -> Set.copyOf(membership.getRoles()))
            .orElseThrow(() -> new AccessDeniedException("You do not have access to this company"));
    }
}
