package com.slotix.reservationcore.availability;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityRuleService {
    private final AvailabilityRuleRepository rules;
    private final ResourceRepository resources;
    private final TenantAccessService access;
    private final AuditService audit;

    public AvailabilityRuleService(AvailabilityRuleRepository rules, ResourceRepository resources,
                                   TenantAccessService access, AuditService audit) {
        this.rules = rules;
        this.resources = resources;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public AvailabilityRuleResponse create(UUID companyId, UUID resourceId, CreateAvailabilityRuleRequest request) {
        access.requireActiveMembership(companyId);
        if (!request.endLocalTime().isAfter(request.startLocalTime())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "The availability interval is invalid");
        }
        resources.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        AvailabilityRule rule = rules.save(AvailabilityRule.create(companyId, resourceId, request.weekday(),
            request.startLocalTime(), request.endLocalTime(), request.effectiveFrom(), request.effectiveTo()));
        audit.record(companyId, access.currentUserId(), "AVAILABILITY_RULE_CREATED", "AVAILABILITY_RULE", rule.getId(), null,
            "{\"weekday\":" + request.weekday() + ",\"startLocalTime\":\"" + request.startLocalTime()
                + "\",\"endLocalTime\":\"" + request.endLocalTime() + "\"}");
        return AvailabilityRuleResponse.from(rule);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> list(UUID companyId, UUID resourceId) {
        access.requireActiveMembership(companyId);
        return rules.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByWeekdayAscStartLocalTimeAsc(companyId, resourceId)
            .stream().map(AvailabilityRuleResponse::from).toList();
    }
}
