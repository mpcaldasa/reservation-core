package com.slotix.reservationcore.policy;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.common.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class BookingPolicyService {
    private final BookingPolicyRepository repository;
    private final TenantAccessService access;
    private final AuditService audit;

    public BookingPolicyService(BookingPolicyRepository repository, TenantAccessService access, AuditService audit) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public UUID create(UUID companyId, CreateBookingPolicyRequest request) {
        access.requireActiveMembership(companyId);
        if (request.maxDurationMinutes() < request.minDurationMinutes()
            || request.minDurationMinutes() % request.slotIncrementMinutes() != 0
            || request.maxDurationMinutes() % request.slotIncrementMinutes() != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Policy durations must align with the slot increment");
        }
        BookingPolicy policy = repository.save(BookingPolicy.create(companyId, request.name().trim(),
            request.minDurationMinutes(), request.maxDurationMinutes(), request.slotIncrementMinutes(),
            request.minNoticeMinutes(), request.maxAdvanceDays(), request.cancellationNoticeMinutes(),
            request.approvalRequired(), request.allowCustomerCancel()));
        audit.record(companyId, access.currentUserId(), "BOOKING_POLICY_CREATED", "BOOKING_POLICY", policy.getId(), null,
            "{\"minDurationMinutes\":" + request.minDurationMinutes() + ",\"maxDurationMinutes\":"
                + request.maxDurationMinutes() + ",\"slotIncrementMinutes\":" + request.slotIncrementMinutes() + "}");
        return policy.getId();
    }
}
