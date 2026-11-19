package com.slotix.reservationcore.policy;

import java.time.Instant;
import java.util.UUID;

public record BookingPolicyResponse(
    UUID id,
    UUID companyId,
    String name,
    int minDurationMinutes,
    int maxDurationMinutes,
    int slotIncrementMinutes,
    int minNoticeMinutes,
    int maxAdvanceDays,
    int cancellationNoticeMinutes,
    boolean approvalRequired,
    boolean allowCustomerCancel,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
    public static BookingPolicyResponse from(BookingPolicy policy) {
        return new BookingPolicyResponse(policy.getId(), policy.getCompanyId(), policy.getName(),
            policy.getMinDurationMinutes(), policy.getMaxDurationMinutes(), policy.getSlotIncrementMinutes(),
            policy.getMinNoticeMinutes(), policy.getMaxAdvanceDays(), policy.getCancellationNoticeMinutes(),
            policy.isApprovalRequired(), policy.isAllowCustomerCancel(), policy.getStatus(), policy.getCreatedAt(),
            policy.getUpdatedAt());
    }
}
