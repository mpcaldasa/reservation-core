package com.slotix.reservationcore.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginRequest(
    @NotNull(message = "companyId is required")
    UUID companyId,

    @NotBlank(message = "email is required")
    String email,

    @NotBlank(message = "password is required")
    String password
) {
}
