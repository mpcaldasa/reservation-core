package com.slotix.reservationcore.availability;

import com.slotix.reservationcore.audit.AuditService;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityRuleService {
    private final AvailabilityRuleRepository rules;
    private final CompanyRepository companies;
    private final ResourceRepository resources;
    private final ResourceAccessService resourceAccess;
    private final TenantAccessService access;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AvailabilityRuleService(AvailabilityRuleRepository rules, CompanyRepository companies,
                                   ResourceRepository resources, ResourceAccessService resourceAccess,
                                   TenantAccessService access, AuditService audit, ObjectMapper objectMapper,
                                   Clock clock) {
        this.rules = rules;
        this.companies = companies;
        this.resources = resources;
        this.resourceAccess = resourceAccess;
        this.access = access;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public AvailabilityRuleResponse create(UUID companyId, UUID resourceId, CreateAvailabilityRuleRequest request) {
        short weekday = request.weekday();
        LocalTime start = normalize(request.startLocalTime());
        LocalTime end = normalize(request.endLocalTime());
        validate(weekday, start, end,
            request.effectiveFrom(), request.effectiveTo());
        lockCompanyAndResource(companyId, resourceId);
        AvailabilityRule rule = rules.save(AvailabilityRule.create(companyId, resourceId, weekday,
            start, end, request.effectiveFrom(), request.effectiveTo()));
        audit.record(companyId, access.currentUserId(), "AVAILABILITY_RULE_CREATED", "AVAILABILITY_RULE", rule.getId(),
            null, snapshot(rule));
        return AvailabilityRuleResponse.from(rule);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public AvailabilityRuleResponse update(UUID companyId, UUID resourceId, UUID ruleId,
                                           UpdateAvailabilityRuleRequest request) {
        short weekday = request.weekday();
        LocalTime start = normalize(request.startLocalTime());
        LocalTime end = normalize(request.endLocalTime());
        validate(weekday, start, end,
            request.effectiveFrom(), request.effectiveTo());
        lockCompanyAndResource(companyId, resourceId);
        AvailabilityRule rule = lockRule(companyId, resourceId, ruleId);
        if (rule.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.AVAILABILITY_RULE_NOT_FOUND,
                "Availability rule not found");
        }
        String before = snapshot(rule);
        if (rule.update(weekday, start, end,
            request.effectiveFrom(), request.effectiveTo(), now())) {
            audit.record(companyId, access.currentUserId(), "AVAILABILITY_RULE_UPDATED", "AVAILABILITY_RULE", ruleId,
                before, snapshot(rule));
        }
        return AvailabilityRuleResponse.from(rule);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public void delete(UUID companyId, UUID resourceId, UUID ruleId) {
        lockCompanyAndResource(companyId, resourceId);
        AvailabilityRule rule = lockRule(companyId, resourceId, ruleId);
        if (rule.softDelete(now())) {
            audit.record(companyId, access.currentUserId(), "AVAILABILITY_RULE_DELETED", "AVAILABILITY_RULE", ruleId,
                snapshot(rule), null);
        }
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> list(UUID companyId, UUID resourceId) {
        resourceAccess.requireVisibleResource(companyId, resourceId);
        return rules.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByWeekdayAscStartLocalTimeAsc(companyId, resourceId)
            .stream().map(AvailabilityRuleResponse::from).toList();
    }

    private void lockCompanyAndResource(UUID companyId, UUID resourceId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "Company is not active"));
        access.requireCompanyRole(company.getId(), MembershipRole.COMPANY_ADMIN);
        resources.lockByIdAndCompanyId(resourceId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND,
                "Resource not found"));
    }

    private AvailabilityRule lockRule(UUID companyId, UUID resourceId, UUID ruleId) {
        return rules.lockByIdAndCompanyAndResource(ruleId, companyId, resourceId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.AVAILABILITY_RULE_NOT_FOUND,
                "Availability rule not found"));
    }

    private void validate(short weekday, java.time.LocalTime start, java.time.LocalTime end,
                          java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo) {
        if (start == null || end == null || weekday < 0 || weekday > 6 || !end.isAfter(start)
            || (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.AVAILABILITY_RULE_INVALID,
                "The availability rule is invalid");
        }
    }

    private LocalTime normalize(LocalTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private String snapshot(AvailabilityRule rule) {
        try {
            return objectMapper.writeValueAsString(new RuleSnapshot(rule.getWeekday(), rule.getStartLocalTime(),
                rule.getEndLocalTime(), rule.getEffectiveFrom(), rule.getEffectiveTo()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Availability rule audit snapshot could not be serialized", exception);
        }
    }

    private record RuleSnapshot(short weekday, java.time.LocalTime startLocalTime, java.time.LocalTime endLocalTime,
                                java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo) {
    }
}
