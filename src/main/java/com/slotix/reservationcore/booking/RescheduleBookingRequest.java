package com.slotix.reservationcore.booking;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleBookingRequest(@NotNull Instant startAt, @NotNull Instant endAt) {
}
