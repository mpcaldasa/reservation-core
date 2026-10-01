package com.slotix.reservationcore.booking;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(@NotNull UUID resourceId, @NotNull Instant startAt, @NotNull Instant endAt,
                                   UUID customerUserId, String notes) {
}
