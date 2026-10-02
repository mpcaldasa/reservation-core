package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ResourcePolicyService {
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final ResourceRepository resources;
    private final TenantAccessService access;
    private final AuditService audit;

    public ResourcePolicyService(ResourcePolicyRepository assignments, BookingPolicyRepository policies,
                                 ResourceRepository resources, TenantAccessService access, AuditService audit) {
        this.assignments = assignments;
        this.policies = policies;
        this.resources = resources;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public ResourcePolicyResponse assign(UUID companyId, UUID resourceId, AssignResourcePolicyRequest request) {
        access.requireActiveMembership(companyId);
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "effectiveTo must be after effectiveFrom");
        }
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        policies.findByIdAndCompanyIdAndStatusAndDeletedAtIsNull(request.policyId(), companyId, "ACTIVE")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active policy not found"));
        boolean overlaps = request.effectiveTo() == null
            ? assignments.overlapsOpen(resourceId, request.effectiveFrom())
            : assignments.overlapsBounded(resourceId, request.effectiveFrom(), request.effectiveTo());
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Policy assignment overlaps an existing period");
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
        access.requireActiveMembership(companyId);
        resources.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        return assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resourceId).stream()
            .map(ResourcePolicyResponse::from).toList();
    }
}
