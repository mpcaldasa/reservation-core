package com.slotix.reservationcore.availability;
import jakarta.validation.constraints.*; import java.time.*;
public record CreateAvailabilityRuleRequest(@Min(0) @Max(6) short weekday,@NotNull LocalTime startLocalTime,@NotNull LocalTime endLocalTime,LocalDate effectiveFrom,LocalDate effectiveTo) {}
