package com.slotix.reservationcore.availability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateResourceBlockRequest(@NotNull Instant startAt, @NotNull Instant endAt,
                                         @NotBlank @Size(max = 2000) String reason,
                                         @NotNull ResourceBlockType blockType) {}
