package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.booking.BookingResourceRepository;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.resource.ResourceAccessService;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResourcePolicyService {
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final ResourceRepository resources;
    private final TenantAccessService access;
    private final AuditService audit;
    private final ResourceAccessService resourceAccess;
    private final CompanyRepository companies;
    private final BookingResourceRepository bookingResources;
    private final Clock clock;

    public ResourcePolicyService(ResourcePolicyRepository assignments, BookingPolicyRepository policies,
                                 ResourceRepository resources, TenantAccessService access, AuditService audit,
                                 ResourceAccessService resourceAccess, CompanyRepository companies,
                                 BookingResourceRepository bookingResources, Clock clock) {
        this.assignments = assignments;
        this.policies = policies;
        this.resources = resources;
        this.access = access;
        this.audit = audit;
        this.resourceAccess = resourceAccess;
        this.companies = companies;
        this.bookingResources = bookingResources;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourcePolicyResponse assign(UUID companyId, UUID resourceId, AssignResourcePolicyRequest request) {
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.INVALID_POLICY_ASSIGNMENT_INTERVAL,
                "effectiveTo must be after effectiveFrom");
        }
        lockActiveCompanyForAdmin(companyId);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
        policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(request.policyId(), companyId, "ACTIVE")
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.POLICY_NOT_FOUND,
                "The active booking policy does not exist in this company"));
        boolean overlaps = request.effectiveTo() == null
            ? assignments.overlapsOpen(resourceId, request.effectiveFrom())
            : assignments.overlapsBounded(resourceId, request.effectiveFrom(), request.effectiveTo());
        if (overlaps) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.POLICY_ASSIGNMENT_OVERLAP,
                "The requested period overlaps an existing policy assignment");
        }
        ResourcePolicy assignment = assignments.save(ResourcePolicy.create(resourceId, request.policyId(),
            request.effectiveFrom(), request.effectiveTo()));
        audit.record(companyId, access.currentUserId(), "RESOURCE_POLICY_ASSIGNED", "RESOURCE", resourceId, null,
            "{\"policyId\":\"" + request.policyId() + "\",\"effectiveFrom\":\"" + request.effectiveFrom()
                + "\",\"effectiveTo\":" + (request.effectiveTo() == null ? "null" : "\"" + request.effectiveTo() + "\"") + "}");
        return ResourcePolicyResponse.from(assignment);
    }

    @Transactional(readOnly = true)
    public List<ResourcePolicyResponse> list(UUID companyId, UUID resourceId) {
        resourceAccess.requireVisibleResource(companyId, resourceId);
        return assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resourceId).stream()
            .map(ResourcePolicyResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourcePolicyResponse close(UUID companyId, UUID resourceId, Instant effectiveFrom,
                                       CloseResourcePolicyAssignmentRequest request) {
        lockActiveCompanyForAdmin(companyId);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
        ResourcePolicy assignment = lockAssignment(resourceId, effectiveFrom);
        Instant newEffectiveTo = request.effectiveTo();
        if (!newEffectiveTo.isAfter(effectiveFrom)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.INVALID_POLICY_ASSIGNMENT_INTERVAL,
                "effectiveTo must be after effectiveFrom");
        }
        Instant oldEffectiveTo = assignment.getEffectiveTo();
        if (oldEffectiveTo != null && newEffectiveTo.isAfter(oldEffectiveTo)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.POLICY_ASSIGNMENT_CANNOT_BE_EXTENDED,
                "Closing an assignment may shorten its end time but cannot extend it");
        }
        if (newEffectiveTo.equals(oldEffectiveTo)) {
            return ResourcePolicyResponse.from(assignment);
        }
        boolean hasActiveReservationOutside = oldEffectiveTo == null
            ? bookingResources.hasActiveReservationOutsideShortenedOpenAssignment(resourceId, effectiveFrom, newEffectiveTo)
            : bookingResources.hasActiveReservationOutsideShortenedBoundedAssignment(resourceId, effectiveFrom,
                oldEffectiveTo, newEffectiveTo);
        if (hasActiveReservationOutside) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE,
                "The assignment cannot be shortened because active reservations extend beyond the requested end");
        }
        String before = assignmentSnapshot(assignment);
        assignment.closeAt(newEffectiveTo);
        audit.record(companyId, access.currentUserId(), "RESOURCE_POLICY_ASSIGNMENT_CLOSED",
            "RESOURCE_POLICY_ASSIGNMENT", resourceId, before, assignmentSnapshot(assignment));
        return ResourcePolicyResponse.from(assignment);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResourcePolicyResponse replacePolicy(UUID companyId, UUID resourceId, Instant effectiveFrom,
                                                ReplaceResourcePolicyRequest request) {
        lockActiveCompanyForAdmin(companyId);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
        ResourcePolicy assignment = lockAssignment(resourceId, effectiveFrom);
        policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(request.policyId(), companyId, "ACTIVE")
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.POLICY_NOT_FOUND,
                "The active booking policy does not exist in this company"));

        if (assignment.getPolicyId().equals(request.policyId())) {
            return ResourcePolicyResponse.from(assignment);
        }
        if (hasActiveReservationInAssignment(assignment)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_CHANGE,
                "The policy cannot be replaced because active reservations use this assignment");
        }

        String before = assignmentSnapshot(assignment);
        assignment.replacePolicy(request.policyId());
        audit.record(companyId, access.currentUserId(), "RESOURCE_POLICY_ASSIGNMENT_POLICY_REPLACED",
            "RESOURCE_POLICY_ASSIGNMENT", resourceId, before, assignmentSnapshot(assignment));
        return ResourcePolicyResponse.from(assignment);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public void removeFuture(UUID companyId, UUID resourceId, Instant effectiveFrom) {
        lockActiveCompanyForAdmin(companyId);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "The resource does not exist in this company"));
        ResourcePolicy assignment = lockAssignment(resourceId, effectiveFrom);
        if (!effectiveFrom.isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.POLICY_ASSIGNMENT_NOT_FUTURE,
                "Only assignments that have not started can be removed");
        }
        if (hasActiveReservationInAssignment(assignment)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.ACTIVE_RESERVATIONS_PREVENT_ASSIGNMENT_REMOVAL,
                "The assignment cannot be removed because it has active reservations");
        }
        String before = assignmentSnapshot(assignment);
        assignments.delete(assignment);
        assignments.flush();
        audit.record(companyId, access.currentUserId(), "RESOURCE_POLICY_ASSIGNMENT_REMOVED",
            "RESOURCE_POLICY_ASSIGNMENT", resourceId, before, null);
    }

    private void lockActiveCompanyForAdmin(UUID companyId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "The company must be active to manage policy assignments"));
        access.requireCompanyRole(company.getId(), MembershipRole.COMPANY_ADMIN);
    }

    private ResourcePolicy lockAssignment(UUID resourceId, Instant effectiveFrom) {
        return assignments.lockAssignment(resourceId, effectiveFrom)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.POLICY_ASSIGNMENT_NOT_FOUND,
                "The policy assignment does not exist for this resource and start time"));
    }

    private boolean hasActiveReservationInAssignment(ResourcePolicy assignment) {
        Instant effectiveTo = assignment.getEffectiveTo();
        UUID resourceId = assignment.getId().resourceId();
        Instant effectiveFrom = assignment.getId().effectiveFrom();
        return effectiveTo == null
            ? bookingResources.hasActiveReservationInOpenAssignment(resourceId, effectiveFrom)
            : bookingResources.hasActiveReservationInBoundedAssignment(resourceId, effectiveFrom, effectiveTo);
    }

    private String assignmentSnapshot(ResourcePolicy assignment) {
        return "{\"policyId\":\"" + assignment.getPolicyId() + "\",\"effectiveFrom\":\""
            + assignment.getId().effectiveFrom() + "\",\"effectiveTo\":"
            + (assignment.getEffectiveTo() == null ? "null" : "\"" + assignment.getEffectiveTo() + "\"") + "}";
    }
}
