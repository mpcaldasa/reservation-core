package com.slotix.reservationcore.availability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateResourceBlockRequest(
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    @NotBlank String reason,
    @NotNull ResourceBlockType blockType
) {
}
