package com.slotix.reservationcore.common;

import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

        if (!companyId.equals(principal.companyId())
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
}
