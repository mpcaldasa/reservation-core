package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.audit.AuditService;
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
public class BookingPolicyService {
    private final BookingPolicyRepository policies;
    private final ResourcePolicyRepository assignments;
    private final CompanyRepository companies;
    private final TenantAccessService access;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BookingPolicyService(BookingPolicyRepository policies, ResourcePolicyRepository assignments,
                                CompanyRepository companies, TenantAccessService access, AuditService audit,
                                ObjectMapper objectMapper, Clock clock) {
        this.policies = policies;
        this.assignments = assignments;
        this.companies = companies;
        this.access = access;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public List<BookingPolicyResponse> list(UUID companyId) {
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
        return policies.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId).stream()
            .map(BookingPolicyResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public UUID create(UUID companyId, CreateBookingPolicyRequest request) {
        lockActiveCompany(companyId);
        validate(request.name(), request.minDurationMinutes(), request.maxDurationMinutes(),
            request.slotIncrementMinutes(), request.minNoticeMinutes(), request.maxAdvanceDays(),
            request.cancellationNoticeMinutes());
        BookingPolicy policy = policies.save(BookingPolicy.create(companyId, request.name().trim(),
            request.minDurationMinutes(), request.maxDurationMinutes(), request.slotIncrementMinutes(),
            request.minNoticeMinutes(), request.maxAdvanceDays(), request.cancellationNoticeMinutes(),
            request.approvalRequired(), request.allowCustomerCancel()));
        audit.record(companyId, access.currentUserId(), "BOOKING_POLICY_CREATED", "BOOKING_POLICY", policy.getId(), null,
            snapshot(policy));
        return policy.getId();
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse update(UUID companyId, UUID policyId, UpdateBookingPolicyRequest request) {
        lockActiveCompany(companyId);
        validate(request.name(), request.minDurationMinutes(), request.maxDurationMinutes(),
            request.slotIncrementMinutes(), request.minNoticeMinutes(), request.maxAdvanceDays(),
            request.cancellationNoticeMinutes());
        BookingPolicy policy = lockPolicy(companyId, policyId);
        if (assignments.existsByPolicyId(policyId)) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_POLICY_ASSIGNED_CANNOT_EDIT,
                "Assigned policies cannot be edited; create a new policy for future assignments");
        }
        String before = snapshot(policy);
        if (policy.update(request.name().trim(), request.minDurationMinutes(), request.maxDurationMinutes(),
            request.slotIncrementMinutes(), request.minNoticeMinutes(), request.maxAdvanceDays(),
            request.cancellationNoticeMinutes(), request.approvalRequired(), request.allowCustomerCancel(), now())) {
            audit.record(companyId, access.currentUserId(), "BOOKING_POLICY_UPDATED", "BOOKING_POLICY", policyId,
                before, snapshot(policy));
        }
        return BookingPolicyResponse.from(policy);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse activate(UUID companyId, UUID policyId) {
        lockActiveCompany(companyId);
        BookingPolicy policy = lockPolicy(companyId, policyId);
        String before = policy.getStatus();
        if (policy.activate(now())) {
            audit.record(companyId, access.currentUserId(), "BOOKING_POLICY_ACTIVATED", "BOOKING_POLICY", policyId,
                statusSnapshot(before), statusSnapshot(policy.getStatus()));
        }
        return BookingPolicyResponse.from(policy);
    }

    @Transactional
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BookingPolicyResponse deactivate(UUID companyId, UUID policyId) {
        lockActiveCompany(companyId);
        BookingPolicy policy = lockPolicy(companyId, policyId);
        if (assignments.hasCurrentOrFutureAssignment(policyId, now())) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.BOOKING_POLICY_HAS_ACTIVE_ASSIGNMENTS,
                "A policy with a current or future resource assignment cannot be deactivated");
        }
        String before = policy.getStatus();
        if (policy.deactivate(now())) {
            audit.record(companyId, access.currentUserId(), "BOOKING_POLICY_DEACTIVATED", "BOOKING_POLICY", policyId,
                statusSnapshot(before), statusSnapshot(policy.getStatus()));
        }
        return BookingPolicyResponse.from(policy);
    }

    private Company lockActiveCompany(UUID companyId) {
        access.requireActiveMembership(companyId);
        Company company = companies.lockById(companyId).filter(Company::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.COMPANY_INACTIVE,
                "Company is not active"));
        access.requireCompanyRole(companyId, MembershipRole.COMPANY_ADMIN);
        return company;
    }

    private BookingPolicy lockPolicy(UUID companyId, UUID policyId) {
        return policies.lockByIdAndCompanyId(policyId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.POLICY_NOT_FOUND,
                "Booking policy not found"));
    }

    private void validate(String name, int min, int max, int increment, int notice, int advance, int cancellation) {
        if (name == null || name.isBlank() || name.trim().length() > 120
            || min <= 0 || max < min || increment <= 0 || min % increment != 0 || max % increment != 0
            || notice < 0 || advance < 0 || cancellation < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.BOOKING_POLICY_INVALID,
                "The booking policy is invalid or its durations do not align with the slot increment");
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private String snapshot(BookingPolicy policy) {
        try {
            return objectMapper.writeValueAsString(new PolicySnapshot(policy.getName(), policy.getMinDurationMinutes(),
                policy.getMaxDurationMinutes(), policy.getSlotIncrementMinutes(), policy.getMinNoticeMinutes(),
                policy.getMaxAdvanceDays(), policy.getCancellationNoticeMinutes(), policy.isApprovalRequired(),
                policy.isAllowCustomerCancel(), policy.getStatus()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Booking policy audit snapshot could not be serialized", exception);
        }
    }

    private String statusSnapshot(String status) {
        return "{\"status\":\"%s\"}".formatted(status);
    }

    private record PolicySnapshot(String name, int minDurationMinutes, int maxDurationMinutes,
                                  int slotIncrementMinutes, int minNoticeMinutes, int maxAdvanceDays,
                                  int cancellationNoticeMinutes, boolean approvalRequired,
                                  boolean allowCustomerCancel, String status) {
    }
}
