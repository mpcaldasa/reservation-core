package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.identity.MembershipRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ResourceAccessService {
    private final ResourceRepository resources;
    private final TenantAccessService tenantAccess;

    public ResourceAccessService(ResourceRepository resources, TenantAccessService tenantAccess) {
        this.resources = resources;
        this.tenantAccess = tenantAccess;
    }

    public List<Resource> visibleResources(UUID companyId) {
        Set<MembershipRole> roles = tenantAccess.currentCompanyRoles(companyId);
        return resources.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId).stream()
            .filter(resource -> isVisible(resource, roles))
            .toList();
    }

    public Resource requireVisibleResource(UUID companyId, UUID resourceId) {
        Set<MembershipRole> roles = tenantAccess.currentCompanyRoles(companyId);
        return resources.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .filter(resource -> isVisible(resource, roles))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist or is not visible to the current user"));
    }

    private boolean isVisible(Resource resource, Set<MembershipRole> roles) {
        return resource.getVisibility() != ResourceVisibility.PRIVATE
            || roles.contains(MembershipRole.COMPANY_ADMIN)
            || roles.contains(MembershipRole.BOOKING_MANAGER);
    }
}
