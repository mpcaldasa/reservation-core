package com.slotix.reservationcore.availability;

import com.slotix.reservationcore.common.TenantAccessService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.policy.BookingPolicy;
import com.slotix.reservationcore.policy.BookingPolicyRepository;
import com.slotix.reservationcore.policy.ResourcePolicy;
import com.slotix.reservationcore.policy.ResourcePolicyRepository;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {
    private final AvailabilityRuleRepository rules;
    private final ResourceBlockRepository blocks;
    private final CompanyRepository companies;
    private final ResourceRepository resources;
    private final ResourcePolicyRepository assignments;
    private final BookingPolicyRepository policies;
    private final TenantAccessService access;

    public AvailabilityService(AvailabilityRuleRepository rules, ResourceBlockRepository blocks,
                               CompanyRepository companies, ResourceRepository resources,
                               ResourcePolicyRepository assignments, BookingPolicyRepository policies,
                               TenantAccessService access) {
        this.rules = rules;
        this.blocks = blocks;
        this.companies = companies;
        this.resources = resources;
        this.assignments = assignments;
        this.policies = policies;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> slots(UUID companyId, UUID resourceId, LocalDate date, int durationMinutes) {
        access.requireActiveMembership(companyId);
        if (durationMinutes <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be greater than zero");
        }
        resources.findByIdAndCompanyIdAndDeletedAtIsNull(resourceId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        Company company = companies.findById(companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        ZoneId zone = ZoneId.of(company.getTimezone());
        Instant now = Instant.now();
        List<ResourcePolicy> assigned = assignments.findByIdResourceIdOrderByIdEffectiveFromAsc(resourceId);
        Map<UUID, BookingPolicy> policyById = policies.findAllById(assigned.stream().map(ResourcePolicy::getPolicyId).toList())
            .stream().filter(policy -> policy.getCompanyId().equals(companyId) && policy.getDeletedAt() == null
                && "ACTIVE".equals(policy.getStatus()))
            .collect(Collectors.toMap(BookingPolicy::getId, Function.identity()));
        List<ResourceBlock> blocked = blocks.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByStartAtAsc(companyId, resourceId);
        short weekday = (short) (date.getDayOfWeek().getValue() % 7);
        List<AvailabilitySlot> result = new ArrayList<>();

        for (AvailabilityRule rule : rules.findByCompanyIdAndResourceIdAndDeletedAtIsNullOrderByWeekdayAscStartLocalTimeAsc(companyId, resourceId)) {
            if (rule.getWeekday() != weekday || (rule.getEffectiveFrom() != null && date.isBefore(rule.getEffectiveFrom()))
                || (rule.getEffectiveTo() != null && date.isAfter(rule.getEffectiveTo()))) {
                continue;
            }
            for (LocalTime local = rule.getStartLocalTime();
                 Duration.between(local, rule.getEndLocalTime()).toMinutes() >= durationMinutes;
                 local = local.plusMinutes(1)) {
                Instant start = LocalDateTime.of(date, local).atZone(zone).toInstant();
                Instant end = LocalDateTime.of(date, local.plusMinutes(durationMinutes)).atZone(zone).toInstant();
                ResourcePolicy assignment = assigned.stream().filter(item -> item.covers(start, end)).findFirst().orElse(null);
                if (assignment == null) continue;
                BookingPolicy policy = policyById.get(assignment.getPolicyId());
                if (policy == null || durationMinutes < policy.getMinDurationMinutes()
                    || durationMinutes > policy.getMaxDurationMinutes()
                    || durationMinutes % policy.getSlotIncrementMinutes() != 0) continue;
                if (Duration.between(rule.getStartLocalTime(), local).toMinutes() % policy.getSlotIncrementMinutes() != 0) continue;
                if (start.isBefore(now.plus(Duration.ofMinutes(policy.getMinNoticeMinutes())))
                    || start.isAfter(now.plus(Duration.ofDays(policy.getMaxAdvanceDays())))) continue;
                if (blocked.stream().anyMatch(block -> start.isBefore(block.getEndAt()) && end.isAfter(block.getStartAt()))) continue;
                result.add(new AvailabilitySlot(start, end));
            }
        }
        return result;
    }
}
