package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.booking.BookingResourceRepository;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ResourceService {
    private final ResourceRepository resources;
    private final CompanyRepository companies;
    private final BookingResourceRepository allocations;
    private final TenantAccessService access;
    private final AuditService audit;
    private final ResourceAccessService resourceAccess;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ResourceService(ResourceRepository resources, CompanyRepository companies,
                           BookingResourceRepository allocations, TenantAccessService access,
                           AuditService audit, ResourceAccessService resourceAccess,
                           ObjectMapper objectMapper, Clock clock) {
        this.resources = resources;
        this.companies = companies;
        this.allocations = allocations;
        this.access = access;
        this.audit = audit;
        this.resourceAccess = resourceAccess;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> list(UUID companyId) {
        return resourceAccess.visibleResources(companyId).stream().map(ResourceResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceResponse create(UUID companyId, CreateResourceRequest request) {
        Company company = lockActiveCompany(companyId);
        Resource resource = Resource.create(company.getId(), request.name().trim(), request.description(),
            request.resourceType(), request.capacity(), request.visibility());
        Resource saved = resources.save(resource);
        audit.record(companyId, access.currentUserId(), "RESOURCE_CREATED", "RESOURCE", saved.getId(), null,
            snapshot(saved));
        return ResourceResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceResponse update(UUID companyId, UUID resourceId, UpdateResourceRequest request) {
        lockActiveCompany(companyId);
        Resource resource = lockResource(companyId, resourceId);
        int previousCapacity = resource.getCapacity();
        if (request.capacity() != previousCapacity && allocations.countActiveReservationsAfter(resourceId, now()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CAPACITY_LOCKED_BY_ACTIVE_RESERVATIONS,
                "Resource capacity cannot change while active reservations remain");
        }

        String before = snapshot(resource);
        boolean changed = resource.updateDetails(request.name().trim(), request.description(), request.resourceType(),
            request.capacity(), request.visibility(), now());
        if (changed) {
            audit.record(companyId, access.currentUserId(), "RESOURCE_UPDATED", "RESOURCE", resourceId,
                before, snapshot(resource));
        }
        return ResourceResponse.from(resource);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceResponse activate(UUID companyId, UUID resourceId) {
        lockActiveCompany(companyId);
        Resource resource = lockResource(companyId, resourceId);
        String previousStatus = resource.getStatus().name();
        if (resource.activate(now())) {
            audit.record(companyId, access.currentUserId(), "RESOURCE_ACTIVATED", "RESOURCE", resourceId,
                "{\"status\":\"%s\"}".formatted(previousStatus), "{\"status\":\"ACTIVE\"}");
        }
        return ResourceResponse.from(resource);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourceResponse deactivate(UUID companyId, UUID resourceId) {
        lockActiveCompany(companyId);
        Resource resource = lockResource(companyId, resourceId);
        String previousStatus = resource.getStatus().name();
        if (resource.deactivate(now())) {
            audit.record(companyId, access.currentUserId(), "RESOURCE_DEACTIVATED", "RESOURCE", resourceId,
                "{\"status\":\"%s\"}".formatted(previousStatus), "{\"status\":\"INACTIVE\"}");
        }
        return ResourceResponse.from(resource);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public void delete(UUID companyId, UUID resourceId) {
        lockActiveCompany(companyId);
        Resource resource = resources.lockIncludingDeletedByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
        if (resource.getDeletedAt() != null) return;
        if (allocations.countActiveReservationsAfter(resourceId, now()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ACTIVE_RESERVATIONS_PREVENT_RESOURCE_DELETION,
                "The resource cannot be deleted while active reservations remain");
        }

        String before = snapshot(resource);
        resource.softDelete(now());
        audit.record(companyId, access.currentUserId(), "RESOURCE_DELETED", "RESOURCE", resourceId,
            before, snapshot(resource));
    }

    private Company lockActiveCompany(UUID companyId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "The company must be active to manage resources"));
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
        return company;
    }

    private Resource lockResource(UUID companyId, UUID resourceId) {
        return resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private String snapshot(Resource resource) {
        try {
            return objectMapper.writeValueAsString(new ResourceAuditSnapshot(resource.getName(),
                resource.getDescription(), resource.getResourceType(), resource.getCapacity(),
                resource.getStatus(), resource.getVisibility(), resource.getDeletedAt()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Resource audit snapshot could not be serialized", exception);
        }
    }

    private record ResourceAuditSnapshot(String name, String description, ResourceType resourceType, int capacity,
                                         ResourceStatus status, ResourceVisibility visibility, Instant deletedAt) {
    }
}
