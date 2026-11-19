package com.slotix.reservationcore.availability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateAvailabilityRuleRequest(
    @Min(0) @Max(6) short weekday,
    @NotNull LocalTime startLocalTime,
    @NotNull LocalTime endLocalTime,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {
}
