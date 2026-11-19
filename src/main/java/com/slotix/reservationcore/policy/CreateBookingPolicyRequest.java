package com.slotix.reservationcore.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateBookingPolicyRequest(
    @NotBlank @Size(max = 120) String name,
    @Positive int minDurationMinutes,
    @Positive int maxDurationMinutes,
    @Positive int slotIncrementMinutes,
    @PositiveOrZero int minNoticeMinutes,
    @PositiveOrZero int maxAdvanceDays,
    @PositiveOrZero int cancellationNoticeMinutes,
    boolean approvalRequired,
    boolean allowCustomerCancel
) {
}
